package com.chirpchat.chat;

import com.chirpchat.chat.ChatDtos.ChannelView;
import com.chirpchat.chat.ChatDtos.CreateChannelRequest;
import com.chirpchat.chat.ChatDtos.MemberView;
import com.chirpchat.chat.ChatDtos.ReadState;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelService {

    private final ChannelRepository channels;
    private final ChannelMemberRepository members;
    private final UserDirectory directory;
    private final ChatEvents events;
    private final Clock clock;

    public ChannelService(ChannelRepository channels, ChannelMemberRepository members, UserDirectory directory,
            ChatEvents events, Clock clock) {
        this.channels = channels;
        this.members = members;
        this.directory = directory;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ChannelView create(CurrentUser me, CreateChannelRequest request) {
        if (channels.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("CHANNEL_EXISTS", "#" + request.name() + " already exists");
        }
        Instant now = clock.instant();
        try {
            Channel channel = channels.saveAndFlush(new Channel(request.name(), blankToNull(request.topic()),
                    request.isPrivate(), me.id(), now));
            ChannelMember owner = members.saveAndFlush(
                    new ChannelMember(channel, me.id(), me.username(), ChannelMember.Role.OWNER, 0, now));
            return view(channel, owner);
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.conflict("CHANNEL_EXISTS", "#" + request.name() + " already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<ChannelView> mine(UUID userId) {
        return members.findMembershipsOf(userId).stream().map(m -> view(m.getChannel(), m)).toList();
    }

    @Transactional(readOnly = true)
    public List<ChannelView> discover(UUID userId, String prefix) {
        String p = prefix == null ? "" : prefix.replace("%", "").replace("_", "").trim();
        return channels.discover(userId, p, PageRequest.of(0, 50)).stream().map(c -> view(c, null)).toList();
    }

    @Transactional(readOnly = true)
    public ChannelView get(UUID userId, UUID channelId) {
        Channel channel = visible(userId, channelId);
        return view(channel, members.findByChannelIdAndUserId(channelId, userId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID userId, UUID channelId) {
        visible(userId, channelId);
        return members.findMembers(channelId).stream()
                .map(m -> new MemberView(m.getUserId(), m.getUsername(), m.getRole())).toList();
    }

    @Transactional
    public ChannelView updateTopic(CurrentUser me, UUID channelId, String topic) {
        ChannelMember owner = requireOwner(me.id(), channelId);
        owner.getChannel().setTopic(blankToNull(topic));
        events.publish(members.findUsernames(channelId), "channel.updated", channelId, Map.of("topic",
                owner.getChannel().getTopic() == null ? "" : owner.getChannel().getTopic()));
        return view(owner.getChannel(), owner);
    }

    /** Joining a public channel is idempotent. Private channels cannot be joined, only added to. */
    @Transactional
    public ChannelView join(CurrentUser me, UUID channelId) {
        Channel channel = visible(me.id(), channelId);
        var existing = members.findByChannelIdAndUserId(channelId, me.id());
        if (existing.isPresent()) {
            return view(channel, existing.get());
        }
        ChannelMember member = members.saveAndFlush(new ChannelMember(channel, me.id(), me.username(),
                ChannelMember.Role.MEMBER, channel.getLastSeq(), clock.instant()));
        events.publish(members.findUsernames(channelId), "member.joined", channelId, Map.of("username", me.username()));
        return view(channel, member);
    }

    @Transactional
    public MemberView add(CurrentUser me, UUID channelId, String username, String bearerToken) {
        Channel channel = requireOwner(me.id(), channelId).getChannel();
        var user = directory.find(username.trim(), bearerToken).orElseThrow(() -> ApiException.notFound("User"));
        if (members.existsByChannelIdAndUserId(channelId, user.id())) {
            throw ApiException.conflict("ALREADY_MEMBER", user.username() + " is already in #" + channel.getName());
        }
        ChannelMember member = members.saveAndFlush(new ChannelMember(channel, user.id(), user.username(),
                ChannelMember.Role.MEMBER, channel.getLastSeq(), clock.instant()));
        events.publish(members.findUsernames(channelId), "member.joined", channelId, Map.of("username", user.username()));
        return new MemberView(member.getUserId(), member.getUsername(), member.getRole());
    }

    @Transactional
    public void remove(CurrentUser me, UUID channelId, UUID userId) {
        boolean self = userId.equals(me.id());
        ChannelMember target;
        if (self) {
            target = requireMember(me.id(), channelId);
        } else {
            requireOwner(me.id(), channelId);
            target = members.findByChannelIdAndUserId(channelId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        }
        members.delete(target);
        members.flush();
        List<ChannelMember> remaining = members.findMembers(channelId);
        if (!remaining.isEmpty() && remaining.stream().noneMatch(m -> m.getRole() == ChannelMember.Role.OWNER)) {
            remaining.stream().min(Comparator.comparing(ChannelMember::getJoinedAt)
                    .thenComparing(ChannelMember::getUsername)).ifPresent(m -> m.setRole(ChannelMember.Role.OWNER));
        }
        List<String> notify = new java.util.ArrayList<>(remaining.stream().map(ChannelMember::getUsername).toList());
        if (!self) {
            notify.add(target.getUsername());
        }
        events.publish(notify, "member.left", channelId, Map.of("username", target.getUsername()));
    }

    @Transactional
    public ReadState markRead(CurrentUser me, UUID channelId, long seq) {
        ChannelMember member = requireMember(me.id(), channelId);
        long lastSeq = member.getChannel().getLastSeq();
        member.markReadUpTo(Math.min(seq, lastSeq));
        return new ReadState(channelId, member.getLastReadSeq(), lastSeq - member.getLastReadSeq());
    }

    /** Public channels are visible to everyone; private ones only to members (others get 404). */
    Channel visible(UUID userId, UUID channelId) {
        Channel channel = channels.findById(channelId).orElseThrow(() -> ApiException.notFound("Channel"));
        if (channel.isPrivateChannel() && !members.existsByChannelIdAndUserId(channelId, userId)) {
            throw ApiException.notFound("Channel");
        }
        return channel;
    }

    ChannelMember requireMember(UUID userId, UUID channelId) {
        return members.findByChannelIdAndUserId(channelId, userId).orElseGet(() -> {
            visible(userId, channelId);
            throw ApiException.forbidden("Join the channel first");
        });
    }

    private ChannelMember requireOwner(UUID userId, UUID channelId) {
        ChannelMember member = requireMember(userId, channelId);
        if (member.getRole() != ChannelMember.Role.OWNER) {
            throw ApiException.forbidden("Only channel owners can do that");
        }
        return member;
    }

    private ChannelView view(Channel c, ChannelMember me) {
        return new ChannelView(c.getId(), c.getName(), c.getTopic(), c.isPrivateChannel(), members.countByChannelId(c.getId()),
                c.getLastSeq(), me == null ? null : c.getLastSeq() - me.getLastReadSeq(), me == null ? null : me.getRole(),
                c.getLastMessageAt(), c.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.chirpchat.chat;

import com.chirpchat.chat.ChatDtos.MessagePage;
import com.chirpchat.chat.ChatDtos.MessageView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    /** "@name" not preceded by a word character or another "@" (so e-mail addresses do not mention anyone). */
    static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([A-Za-z0-9_.]{3,32})");

    private final ChannelService channels;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository members;
    private final MessageRepository messages;
    private final ChatEvents events;
    private final Clock clock;

    public MessageService(ChannelService channels, ChannelRepository channelRepository, ChannelMemberRepository members,
            MessageRepository messages, ChatEvents events, Clock clock) {
        this.channels = channels;
        this.channelRepository = channelRepository;
        this.members = members;
        this.messages = messages;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public MessageView post(CurrentUser me, UUID channelId, String body) {
        ChannelMember author = channels.requireMember(me.id(), channelId);
        Channel channel = channelRepository.findForUpdate(channelId).orElseThrow();
        Instant now = clock.instant();
        long seq = channel.nextSeq(now);

        List<String> usernames = members.findUsernames(channelId);
        Map<String, String> byLower = usernames.stream()
                .collect(Collectors.toMap(u -> u.toLowerCase(Locale.ROOT), Function.identity()));
        List<String> mentions = mentions(body).stream()
                .map(byLower::get)
                .filter(u -> u != null && !u.equals(me.username()))
                .toList();

        Message message = messages.save(new Message(channel, me, seq, body.strip(), mentions, now));
        author.markReadUpTo(seq);
        MessageView view = MessageView.of(message);
        events.publish(usernames, "message.created", channelId, view);
        events.publish(mentions, "mention", channelId, view);
        return view;
    }

    @Transactional(readOnly = true)
    public MessagePage page(CurrentUser me, UUID channelId, Long before, Integer limit) {
        channels.requireMember(me.id(), channelId);
        int size = limit == null ? 50 : Math.clamp(limit, 1, 100);
        List<Message> newestFirst = messages.findPage(channelId, before == null ? Long.MAX_VALUE : before,
                PageRequest.of(0, size + 1));
        boolean more = newestFirst.size() > size;
        List<MessageView> page = new ArrayList<>(newestFirst.stream().limit(size).map(MessageView::of).toList());
        Collections.reverse(page);
        return new MessagePage(page, more ? page.getFirst().seq() : null);
    }

    static Set<String> mentions(String body) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(body);
        while (m.find()) {
            found.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return found;
    }
}

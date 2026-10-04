package com.chirpchat.chat;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ChannelRepository extends JpaRepository<Channel, UUID> {

    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Channel c where c.id = :id")
    Optional<Channel> findForUpdate(UUID id);

    /** Public channels the user has not joined, optionally filtered by name prefix. */
    @Query("""
            select c from Channel c
            where c.privateChannel = false
              and lower(c.name) like concat(lower(:prefix), '%')
              and not exists (select 1 from ChannelMember m where m.channel = c and m.userId = :userId)
            order by c.name""")
    List<Channel> discover(UUID userId, String prefix, Pageable page);
}

interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {

    Optional<ChannelMember> findByChannelIdAndUserId(UUID channelId, UUID userId);

    boolean existsByChannelIdAndUserId(UUID channelId, UUID userId);

    long countByChannelId(UUID channelId);

    @Query("""
            select m from ChannelMember m join fetch m.channel c
            where m.userId = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc""")
    List<ChannelMember> findMembershipsOf(UUID userId);

    @Query("""
            select m from ChannelMember m where m.channel.id = :channelId
            order by case when m.role = 'OWNER' then 0 else 1 end, m.joinedAt, m.username""")
    List<ChannelMember> findMembers(UUID channelId);

    @Query("select m.username from ChannelMember m where m.channel.id = :channelId")
    List<String> findUsernames(UUID channelId);
}

interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("select m from Message m where m.channel.id = :channelId and m.seq < :beforeSeq order by m.seq desc")
    List<Message> findPage(UUID channelId, long beforeSeq, Pageable page);
}

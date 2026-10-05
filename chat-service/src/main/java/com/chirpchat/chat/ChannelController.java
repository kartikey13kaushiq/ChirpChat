package com.chirpchat.chat;

import com.chirpchat.chat.ChatDtos.AddMemberRequest;
import com.chirpchat.chat.ChatDtos.ChannelView;
import com.chirpchat.chat.ChatDtos.CreateChannelRequest;
import com.chirpchat.chat.ChatDtos.MemberView;
import com.chirpchat.chat.ChatDtos.MessagePage;
import com.chirpchat.chat.ChatDtos.MessageView;
import com.chirpchat.chat.ChatDtos.PostMessageRequest;
import com.chirpchat.chat.ChatDtos.ReadRequest;
import com.chirpchat.chat.ChatDtos.ReadState;
import com.chirpchat.chat.ChatDtos.UpdateTopicRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
class ChannelController {

    private final ChannelService channels;
    private final MessageService messages;

    ChannelController(ChannelService channels, MessageService messages) {
        this.channels = channels;
        this.messages = messages;
    }

    @GetMapping
    @Operation(summary = "Channels you belong to, most recently active first, with unread counts")
    List<ChannelView> mine(@AuthenticationPrincipal Jwt jwt) {
        return channels.mine(CurrentUser.of(jwt).id());
    }

    @GetMapping("/discover")
    @Operation(summary = "Public channels you have not joined")
    List<ChannelView> discover(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String q) {
        return channels.discover(CurrentUser.of(jwt).id(), q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ChannelView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateChannelRequest request) {
        return channels.create(CurrentUser.of(jwt), request);
    }

    @GetMapping("/{id}")
    ChannelView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return channels.get(CurrentUser.of(jwt).id(), id);
    }

    @PatchMapping("/{id}")
    ChannelView updateTopic(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateTopicRequest request) {
        return channels.updateTopic(CurrentUser.of(jwt), id, request.topic());
    }

    @PostMapping("/{id}/join")
    ChannelView join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return channels.join(CurrentUser.of(jwt), id);
    }

    @GetMapping("/{id}/members")
    List<MemberView> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return channels.members(CurrentUser.of(jwt).id(), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a user by username (owners only); the user is resolved through the auth service")
    MemberView add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AddMemberRequest request) {
        return channels.add(CurrentUser.of(jwt), id, request.username(), jwt.getTokenValue());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave (your own id) or remove someone (owners only)")
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        channels.remove(CurrentUser.of(jwt), id, userId);
    }

    @PostMapping("/{id}/read")
    ReadState read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ReadRequest request) {
        return channels.markRead(CurrentUser.of(jwt), id, request.seq());
    }

    @GetMapping("/{id}/messages")
    MessagePage messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        return messages.page(CurrentUser.of(jwt), id, before, limit);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PostMessageRequest request) {
        return messages.post(CurrentUser.of(jwt), id, request.body());
    }
}

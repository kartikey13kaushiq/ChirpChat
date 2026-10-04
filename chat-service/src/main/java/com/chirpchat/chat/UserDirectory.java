package com.chirpchat.chat;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Looks users up in the auth service, forwarding the caller's own token (no service credentials needed). */
@Component
public class UserDirectory {

    public record DirectoryUser(UUID id, String username, String displayName) {
    }

    private final RestClient client;

    public UserDirectory(RestClient.Builder builder, ChatProperties properties) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(3000);
        this.client = builder.baseUrl(properties.baseUrl()).requestFactory(timeouts).build();
    }

    public Optional<DirectoryUser> find(String username, String bearerToken) {
        try {
            return Optional.ofNullable(client.get()
                    .uri("/auth/users/{username}", username)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(DirectoryUser.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw ApiException.badGateway("User directory rejected the lookup (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw ApiException.badGateway("User directory is unavailable");
        }
    }
}

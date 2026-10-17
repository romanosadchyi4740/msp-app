package com.romanosadchyi.smp.music_app.shared.config;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST requests authenticated by the JWT filter, whose principal is built from the token claims.
 */
@IntegrationTest
class AuthenticatedRequestsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtStompChannelInterceptor stompInterceptor;

    @Test
    void currentUser_WithTokenFromRegistration_IsReturned() throws Exception {
        String email = "me-" + UUID.randomUUID() + "@example.com";
        Registered user = register(email);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void protectedEndpoint_WithoutToken_IsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/users/me", "/api/users/following", "/api/tracks/liked", "/api/tracks/recommended",
            "/api/playlists/me", "/api/playlists/00000000-0000-0000-0000-000000000000/integrations"})
    void currentUsersEndpointUnderAPublicPath_WithoutToken_IsUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingOrDeletingTheCurrentUser_WithoutToken_IsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_WithInvalidToken_IsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccount_TokenIsRejectedOnRestAndStomp() throws Exception {
        Registered user = register("gone-" + UUID.randomUUID() + "@example.com");
        String bearer = "Bearer " + user.token();
        Message<byte[]> connect = connectFrame(bearer);
        assertThat(StompHeaderAccessor.wrap(stompInterceptor.preSend(connect, null)).getUser()).isNotNull();

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> stompInterceptor.preSend(connectFrame(bearer), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void becomeArtist_ReturnsTokenWithArtistRole() throws Exception {
        Registered user = register("artist-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/albums/my").header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isForbidden());

        MvcResult result = mockMvc.perform(post("/api/users/me/become-artist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ARTIST"))
                .andReturn();
        String newAuthorization = result.getResponse().getHeader(HttpHeaders.AUTHORIZATION);
        assertThat(newAuthorization).startsWith("Bearer ");

        mockMvc.perform(get("/api/albums/my").header(HttpHeaders.AUTHORIZATION, newAuthorization))
                .andExpect(status().isOk());
    }

    @Test
    void unknownUser_IsNotFound() throws Exception {
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404 NOT_FOUND"));
    }

    private static Message<byte[]> connectFrame(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Registered register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "secret123", "displayName": "Tester"}
                """.formatted(email);
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(JsonPath.read(response, "$.userId"), JsonPath.read(response, "$.accessToken"));
    }

    private record Registered(String id, String token) {
    }
}

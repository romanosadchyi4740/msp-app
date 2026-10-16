package com.romanosadchyi.smp.music_app.shared.config;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST requests authenticated by the JWT filter, whose principal is built from the token claims.
 */
@IntegrationTest
class AuthenticatedRequestsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

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

    @Test
    void protectedEndpoint_WithInvalidToken_IsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
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

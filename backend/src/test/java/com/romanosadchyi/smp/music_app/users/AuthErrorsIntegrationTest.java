package com.romanosadchyi.smp.music_app.users;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Expected sign-in and registration failures answer with their own status, not 500 (KAN-100).
 */
@IntegrationTest
class AuthErrorsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void login_WrongPassword_IsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isOk());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_UnknownEmail_IsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail(), "secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void register_TakenEmail_IsConflict() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isOk());

        register(email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void register_SameEmailConcurrently_OneSucceedsAndTheOtherIsConflict() throws Exception {
        String email = uniqueEmail();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> registration = () -> {
            start.await();
            return register(email).andReturn().getResponse().getStatus();
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(executor.submit(registration), executor.submit(registration));
            start.countDown();

            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
    }

    private ResultActions register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "secret123", "displayName": "Tester"}
                """.formatted(email);
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }
}

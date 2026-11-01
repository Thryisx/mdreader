package com.mdreader.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录、限流、Token 失效和强制改密集成测试。
 */
class AuthSecurityTest extends BaseIntegrationTest {

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        String username = uniqueName("login");
        createUser(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
    }

    @Test
    void loginIsLockedAfterFiveFailures() throws Exception {
        String username = uniqueName("limit");
        createUser(username);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", username, "password", "Wrong12345"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Wrong12345"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请15分钟后再试"));
    }

    @Test
    void changePasswordInvalidatesOldTokenAndPassword() throws Exception {
        String username = uniqueName("change");
        createUser(username);
        String oldToken = login(username, DEFAULT_PASSWORD);

        mockMvc.perform(put("/api/auth/me/password")
                        .header(AUTHORIZATION, bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", DEFAULT_PASSWORD,
                                "newPassword", NEW_PASSWORD,
                                "confirmPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isUnauthorized());

        String newToken = login(username, NEW_PASSWORD);
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void forcedPasswordChangeBlocksBusinessApisUntilChanged() throws Exception {
        String username = uniqueName("force");
        createUser(username, "ADMIN", 1, DEFAULT_PASSWORD, true);
        String oldToken = login(username, DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        mockMvc.perform(get("/api/admin/files").header(AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/auth/me/password")
                        .header(AUTHORIZATION, bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", DEFAULT_PASSWORD,
                                "newPassword", NEW_PASSWORD,
                                "confirmPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        String newToken = login(username, NEW_PASSWORD);
        mockMvc.perform(get("/api/admin/files").header(AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsSamePassword() throws Exception {
        String username = uniqueName("same");
        createUser(username);
        String token = login(username, DEFAULT_PASSWORD);

        mockMvc.perform(put("/api/auth/me/password")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", DEFAULT_PASSWORD,
                                "newPassword", DEFAULT_PASSWORD,
                                "confirmPassword", DEFAULT_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能与当前密码相同"));
    }
}

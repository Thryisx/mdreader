package com.mdreader.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员权限边界、重置密码和最后管理员保护测试。
 */
class AdminSecurityTest extends BaseIntegrationTest {

    @Test
    void normalUserCannotAccessAdminEndpoints() throws Exception {
        String username = uniqueName("user");
        createUser(username);
        String token = login(username, DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/admin/files").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResetPasswordInvalidatesTokenAndForcesChange() throws Exception {
        String username = uniqueName("reset");
        Long userId = createUser(username);
        String userToken = login(username, DEFAULT_PASSWORD);
        String admin = uniqueName("admin");
        createAdmin(admin);
        String adminToken = login(admin, DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/users/{id}/reset-password", userId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
    }

    @Test
    void disablingUserInvalidatesExistingToken() throws Exception {
        String username = uniqueName("disabled");
        Long userId = createUser(username);
        String userToken = login(username, DEFAULT_PASSWORD);
        String admin = uniqueName("admin");
        createAdmin(admin);
        String adminToken = login(admin, DEFAULT_PASSWORD);

        mockMvc.perform(patch("/api/users/{id}/status", userId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentAdminCannotDemoteDisableOrDeleteSelf() throws Exception {
        String username = uniqueName("admin");
        Long adminId = createAdmin(username);
        String token = login(username, DEFAULT_PASSWORD);

        mockMvc.perform(put("/api/users/{id}", adminId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/{id}/status", adminId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 0))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/users/{id}", adminId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }
}

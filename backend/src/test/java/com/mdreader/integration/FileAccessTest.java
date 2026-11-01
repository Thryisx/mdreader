package com.mdreader.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开文件匿名读取、私有文件权限和管理端正文隔离测试。
 */
class FileAccessTest extends BaseIntegrationTest {

    @Test
    void anonymousCanReadPublicFileOnly() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        String publicTitle = uniqueName("pub");
        String privateTitle = uniqueName("priv");
        Long publicId = createFile(ownerToken, publicTitle, "PUBLIC", "PUBLISHED", "# public");
        Long privateId = createFile(ownerToken, privateTitle, "PRIVATE", "DRAFT", "# private");

        MvcResult listResult = mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = readJson(listResult).path("data").path("records");
        assertThat(containsId(records, publicId)).isTrue();
        assertThat(containsId(records, privateId)).isFalse();

        mockMvc.perform(get("/api/files/{id}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(publicTitle));

        mockMvc.perform(get("/api/files/{id}", privateId))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicContentSupportsAnonymousReadAndSecurityHeaders() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        Long publicId = createFile(ownerToken, uniqueName("pub"), "PUBLIC", "PUBLISHED", "# public");

        mockMvc.perform(get("/api/files/{id}/content", publicId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("# public"));
    }

    @Test
    void ownerCanReadPrivateFileButOtherUserCannot() throws Exception {
        String owner = uniqueName("owner");
        String other = uniqueName("other");
        createUser(owner);
        createUser(other);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        String otherToken = login(other, DEFAULT_PASSWORD);
        Long privateId = createFile(ownerToken, uniqueName("priv"), "PRIVATE", "DRAFT", "# private");

        mockMvc.perform(get("/api/files/{id}", privateId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/files/{id}", privateId).header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/files/{id}/content", privateId).header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotReadOtherUsersPrivateContentAndGetsMetadataOnly() throws Exception {
        String owner = uniqueName("owner");
        String admin = uniqueName("admin");
        createUser(owner);
        createAdmin(admin);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        String adminToken = login(admin, DEFAULT_PASSWORD);
        Long privateId = createFile(ownerToken, uniqueName("priv"), "PRIVATE", "DRAFT", "# private");

        mockMvc.perform(get("/api/files/{id}/content", privateId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        MvcResult result = mockMvc.perform(get("/api/admin/files").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode record = findById(readJson(result).path("data").path("records"), privateId);
        assertThat(record).isNotNull();
        assertThat(record.has("mdUrl")).isFalse();
        assertThat(record.has("content")).isFalse();
    }

    @Test
    void publicListOnlyContainsPublicPublishedFiles() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        Long publicDraftId = createFile(ownerToken, uniqueName("draft"), "PUBLIC", "DRAFT", "# draft");
        Long publicPublishedId = createFile(ownerToken, uniqueName("published"), "PUBLIC", "PUBLISHED", "# published");
        Long privatePublishedId = createFile(ownerToken, uniqueName("private"), "PRIVATE", "PUBLISHED", "# private");

        MvcResult result = mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = readJson(result).path("data").path("records");

        assertThat(containsId(records, publicPublishedId)).isTrue();
        assertThat(containsId(records, publicDraftId)).isFalse();
        assertThat(containsId(records, privatePublishedId)).isFalse();
    }

    private boolean containsId(JsonNode records, Long id) {
        return findById(records, id) != null;
    }

    private JsonNode findById(JsonNode records, Long id) {
        for (JsonNode record : records) {
            if (record.path("id").asLong() == id) {
                return record;
            }
        }
        return null;
    }
}

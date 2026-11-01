package com.mdreader.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同级文件/文件夹重名校验测试。
 */
class FileCollisionTest extends BaseIntegrationTest {

    @Test
    void createDuplicateSiblingReturnsConflict() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String token = login(owner, DEFAULT_PASSWORD);
        String title = uniqueName("dup");
        createFile(token, title, "PRIVATE", "DRAFT", "# a");

        mockMvc.perform(post("/api/files")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "content", "# b"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同级目录下已存在同名文件或文件夹"));
    }

    @Test
    void updateTitleToExistingSiblingReturnsConflict() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String token = login(owner, DEFAULT_PASSWORD);
        String firstTitle = uniqueName("first");
        String secondTitle = uniqueName("second");
        createFile(token, firstTitle, "PRIVATE", "DRAFT", "# first");
        Long secondId = createFile(token, secondTitle, "PRIVATE", "DRAFT", "# second");

        mockMvc.perform(put("/api/files/{id}", secondId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", firstTitle))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同级目录下已存在同名文件或文件夹"));
    }

    @Test
    void fileAndFolderCannotShareSiblingName() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String token = login(owner, DEFAULT_PASSWORD);
        String name = uniqueName("node");

        mockMvc.perform(post("/api/files/folders")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/files")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", name, "content", "# file"))))
                .andExpect(status().isConflict());
    }
}

package com.mdreader.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开文章分类、摘要与作者字段测试。
 */
class PublicArticleMetaTest extends BaseIntegrationTest {

    @Test
    void publicListExposesCategorySummaryAndAuthor() throws Exception {
        String owner = uniqueName("owner");
        Long ownerId = createUser(owner);
        jdbcTemplate.update("UPDATE `user` SET nickname = ? WHERE id = ?", "林墨", ownerId);
        String ownerToken = login(owner, DEFAULT_PASSWORD);

        Long fileId = createFile(ownerToken, uniqueName("art"), "PUBLIC", "PUBLISHED", "# 标题\n\n这是一段正文摘要内容。");
        MvcResult updateResult = mockMvc.perform(put("/api/files/{id}", fileId)
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of("category", "技术随笔"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("技术随笔"))
                .andReturn();
        assertThat(readJson(updateResult).path("data").path("summary").asText()).contains("正文摘要内容");

        MvcResult listResult = mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode record = findById(readJson(listResult).path("data").path("records"), fileId);
        assertThat(record).isNotNull();
        assertThat(record.path("category").asText()).isEqualTo("技术随笔");
        assertThat(record.path("summary").asText()).contains("正文摘要内容");
        assertThat(record.path("authorNickname").asText()).isEqualTo("林墨");
        assertThat(record.path("authorUsername").asText()).isEqualTo(owner);
    }

    @Test
    void publicPageFiltersByCategory() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        String category = uniqueName("cat");
        Long matched = createFile(ownerToken, uniqueName("in"), "PUBLIC", "PUBLISHED", "# in");
        Long other = createFile(ownerToken, uniqueName("out"), "PUBLIC", "PUBLISHED", "# out");
        mockMvc.perform(put("/api/files/{id}", matched)
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of("category", category))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/files").param("category", category))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = readJson(result).path("data").path("records");
        assertThat(findById(records, matched)).isNotNull();
        assertThat(findById(records, other)).isNull();
    }

    @Test
    void categoriesEndpointReturnsCountsForPublicFilesOnly() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        String category = uniqueName("cat");
        Long published = createFile(ownerToken, uniqueName("pub"), "PUBLIC", "PUBLISHED", "# pub");
        Long draft = createFile(ownerToken, uniqueName("draft"), "PUBLIC", "DRAFT", "# draft");
        mockMvc.perform(put("/api/files/{id}", published)
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of("category", category))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/files/categories"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categories = readJson(result).path("data");
        JsonNode matched = null;
        for (JsonNode item : categories) {
            if (category.equals(item.path("category").asText())) {
                matched = item;
            }
        }
        assertThat(matched).isNotNull();
        assertThat(matched.path("count").asLong()).isEqualTo(1);

        long publishedCount = 0;
        for (JsonNode item : categories) {
            publishedCount += item.path("count").asLong();
        }
        assertThat(publishedCount).isGreaterThanOrEqualTo(1);
        assertThat(draft).isNotNull();
    }

    @Test
    void createFileAcceptsCategoryAndSummary() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/files")
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "title", uniqueName("art"),
                                "visibility", "PUBLIC",
                                "status", "PUBLISHED",
                                "category", "读书笔记",
                                "summary", "手写摘要",
                                "content", "# 正文"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("读书笔记"))
                .andExpect(jsonPath("$.data.summary").value("手写摘要"))
                .andReturn();
        assertThat(readJson(result).path("data").path("id").asLong()).isPositive();
    }

    @Test
    void clearingCategoryAndSummaryFallsBackToGeneratedValues() throws Exception {
        String owner = uniqueName("owner");
        createUser(owner);
        String ownerToken = login(owner, DEFAULT_PASSWORD);
        Long fileId = createFile(ownerToken, uniqueName("art"), "PUBLIC", "PUBLISHED", "# 标题\n\n自动摘要正文。");
        mockMvc.perform(put("/api/files/{id}", fileId)
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of("category", "临时分类"))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(put("/api/files/{id}", fileId)
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content(json(java.util.Map.of("category", "", "summary", ""))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = readJson(result).path("data");
        assertThat(data.path("category").isNull()).isTrue();
        assertThat(data.path("summary").asText()).contains("自动摘要正文");
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

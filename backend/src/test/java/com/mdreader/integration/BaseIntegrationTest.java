package com.mdreader.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdreader.common.constant.SystemConstants;
import com.mdreader.utils.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类。
 *
 * <p>测试使用独立数据库 mdreader_test，测试方法整体回滚，避免污染开发库。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    protected static final String AUTHORIZATION = "Authorization";
    protected static final String DEFAULT_PASSWORD = "12345678Ab";
    protected static final String NEW_PASSWORD = "87654321Zz";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 生成测试内唯一的用户名。
     *
     * @param prefix 前缀
     * @return 3-50 位唯一用户名
     */
    protected String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /**
     * 创建普通用户。
     *
     * @param username 用户名
     * @return 用户 ID
     */
    protected Long createUser(String username) {
        return createUser(username, SystemConstants.ROLE_USER, 1, DEFAULT_PASSWORD, false);
    }

    /**
     * 创建管理员用户。
     *
     * @param username 用户名
     * @return 用户 ID
     */
    protected Long createAdmin(String username) {
        return createUser(username, SystemConstants.ROLE_ADMIN, 1, DEFAULT_PASSWORD, false);
    }

    /**
     * 创建指定角色、状态和改密标记的用户。
     *
     * @param username    用户名
     * @param role        角色
     * @param status      状态
     * @param password    原始密码
     * @param mustChange  是否强制改密
     * @return 用户 ID
     */
    protected Long createUser(String username, String role, int status, String password, boolean mustChange) {
        jdbcTemplate.update(
                "INSERT INTO `user` (username, password_hash, role, status, token_version, must_change_password) "
                        + "VALUES (?, ?, ?, ?, 0, ?)",
                username, PasswordUtil.hash(password), role, status, mustChange ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    /**
     * 登录并返回 Token。
     *
     * @param username 用户名
     * @param password 密码
     * @return JWT Token
     */
    protected String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).path("data").path("token").asText();
    }

    /**
     * 使用当前用户创建文件。
     *
     * @param token       用户 Token
     * @param title       标题
     * @param visibility  可见性
     * @param fileStatus  状态
     * @param content     正文
     * @return 文件 ID
     */
    protected Long createFile(String token, String title, String visibility, String fileStatus, String content)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/files")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "visibility", visibility,
                                "status", fileStatus,
                                "content", content))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).path("data").path("id").asLong();
    }

    /**
     * 构造 Bearer 请求头。
     *
     * @param token JWT Token
     * @return Authorization 请求头值
     */
    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * 序列化 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 读取响应 JSON。
     *
     * @param result MockMvc 结果
     * @return JSON 根节点
     */
    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}

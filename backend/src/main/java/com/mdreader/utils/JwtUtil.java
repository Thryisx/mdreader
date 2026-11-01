package com.mdreader.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 生成、解析与校验工具。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_TOKEN_VERSION = "ver";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long expirationMinutes;

    /**
     * 构造 JWT 工具。
     *
     * @param secret            签名密钥，至少 32 字节
     * @param expirationMinutes Token 有效期（分钟）
     * 策略：启动时校验密钥长度和有效期，避免运行期才暴露配置错误
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-minutes:180}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 未配置");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret 至少需要 32 字节（256 位）");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("jwt.expiration-minutes 必须大于 0");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * 生成 JWT Token。
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param role         角色：ADMIN / USER
     * @param tokenVersion Token 版本号
     * @return 签名后的 JWT 字符串
     * 策略：使用 HS256 签名，subject 存用户 ID，payload 附带 username、role 和 token 版本号
     */
    public String generateToken(Long userId, String username, String role, Integer tokenVersion) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验证 JWT Token。
     *
     * @param token JWT 字符串
     * @return Token 载荷
     * 策略：校验签名和过期时间；过期、签名错误或格式错误时抛出 JWT 异常
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 JWT Token 是否有效。
     *
     * @param token JWT 字符串
     * @return 是否有效
     * 策略：调用解析方法并捕获 JWT 异常，布尔结果不包含具体失败原因
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 从载荷中获取用户 ID。
     *
     * @param claims JWT 载荷
     * @return 用户 ID
     */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从载荷中获取角色。
     *
     * @param claims JWT 载荷
     * @return 角色；不存在时返回 null
     */
    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    /**
     * 从载荷中获取 Token 版本号。
     *
     * @param claims JWT 载荷
     * @return Token 版本号；旧 Token 不存在该声明时返回 0
     */
    public Integer getTokenVersion(Claims claims) {
        Number value = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        return value == null ? 0 : value.intValue();
    }

    /**
     * 获取 Token 有效期秒数。
     *
     * @return 有效期（秒）
     */
    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}

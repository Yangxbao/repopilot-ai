package com.repopilot.security;

import com.repopilot.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

/**
 * JWT 的签发与校验。
 *
 * <p>生产环境必须通过环境变量 REPOPILOT_JWT_SECRET 提供至少 32 字节的随机密钥；
 * 未配置时这里会生成一个随机的临时密钥，保证本地"开箱即用"，
 * 但重启后所有已登录用户都需要重新登录（会在启动日志中明确提示）。
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(AppProperties properties) {
        String secret = properties.jwt().secret();
        this.expireMillis = properties.jwt().expireHours() * 3600_000L;
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES) {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("未配置 REPOPILOT_JWT_SECRET 或长度不足 32 字节，已生成随机开发密钥："
                    + "应用重启后所有登录态将失效。生产环境必须配置该环境变量。");
        }
    }

    public String createToken(long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 token；无效或过期时返回 null，由过滤器决定按"未登录"处理。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

package com.demo_apache_camel.hub.security;

import com.demo_apache_camel.hub.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * [JWT TOKEN VALIDATOR - PRODUCTION GRADE]
 *
 * Xác thực JWT thật bằng thư viện JJWT 0.12.x:
 * 1. Kiểm tra chữ ký số HMAC-SHA256 (chống giả mạo token).
 * 2. Kiểm tra expiry (exp), not-before (nbf), issuer (iss).
 * 3. Clock skew tolerance: chấp nhận lệch đồng hồ cấu hình được (mặc định 60s).
 * 4. Phân loại lỗi chi tiết qua JwtValidationException.Reason.
 * 5. Cung cấp JwtClaims chứa clientId, roles, expiry cho downstream.
 *
 * Cách dùng (dành cho lập trình viên):
 * <pre>
 *   JwtClaims claims = jwtTokenValidator.validate(bearerToken);
 *   String clientId = claims.getClientId();
 * </pre>
 *
 * Cách tạo token cho test:
 * <pre>
 *   String token = jwtTokenValidator.issueToken("CLIENT_ID", List.of("ROLE_PARTNER"));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtProperties jwtProperties;

    /**
     * Claims đã xác thực từ JWT, truyền downstream qua HttpServletRequest attribute.
     */
    public record JwtClaims(
            String clientId,
            String issuer,
            Instant issuedAt,
            Instant expiresAt,
            Object roles
    ) {}

    /**
     * Validate JWT Bearer token và trả về JwtClaims đã được xác thực.
     *
     * @param bearerToken Chuỗi đầy đủ dạng "Bearer eyJ..." hoặc chỉ "eyJ..."
     * @return JwtClaims nếu token hợp lệ
     * @throws JwtValidationException với Reason cụ thể nếu token không hợp lệ
     */
    public JwtClaims validate(String bearerToken) throws JwtValidationException {
        String token = extractRawToken(bearerToken);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Kiểm tra issuer claim
            String issuer = claims.getIssuer();
            if (issuer == null || !issuer.equals(jwtProperties.getIssuer())) {
                log.warn("[JWT-VALIDATOR] Issuer không hợp lệ: expected='{}', actual='{}'",
                        jwtProperties.getIssuer(), issuer);
                throw new JwtValidationException(
                        JwtValidationException.Reason.INVALID_ISSUER,
                        "JWT issuer không khớp: " + issuer
                );
            }

            JwtClaims result = new JwtClaims(
                    claims.getSubject(),
                    claims.getIssuer(),
                    claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now(),
                    claims.getExpiration() != null ? claims.getExpiration().toInstant() : null,
                    claims.get("roles")
            );

            log.debug("[JWT-VALIDATOR] Token hợp lệ: clientId='{}', exp='{}'",
                    result.clientId(), result.expiresAt());
            return result;

        } catch (ExpiredJwtException ex) {
            log.warn("[JWT-VALIDATOR] Token hết hạn: subject='{}'", ex.getClaims().getSubject());
            throw new JwtValidationException(
                    JwtValidationException.Reason.TOKEN_EXPIRED,
                    "JWT đã hết hạn tại: " + ex.getClaims().getExpiration(), ex
            );
        } catch (SignatureException ex) {
            log.warn("[JWT-VALIDATOR] Chữ ký số sai hoặc token bị giả mạo");
            throw new JwtValidationException(
                    JwtValidationException.Reason.INVALID_SIGNATURE,
                    "Chữ ký JWT không hợp lệ", ex
            );
        } catch (MalformedJwtException ex) {
            log.warn("[JWT-VALIDATOR] Token sai định dạng JWT");
            throw new JwtValidationException(
                    JwtValidationException.Reason.MISSING_OR_MALFORMED,
                    "JWT sai định dạng", ex
            );
        } catch (UnsupportedJwtException ex) {
            log.warn("[JWT-VALIDATOR] Loại JWT không được hỗ trợ");
            throw new JwtValidationException(
                    JwtValidationException.Reason.PARSE_ERROR,
                    "Loại JWT không được hỗ trợ", ex
            );
        } catch (JwtValidationException ex) {
            throw ex; // Re-throw exception đã phân loại
        } catch (Exception ex) {
            log.error("[JWT-VALIDATOR] Lỗi parse JWT không xác định: {}", ex.getMessage());
            throw new JwtValidationException(
                    JwtValidationException.Reason.PARSE_ERROR,
                    "Lỗi xử lý JWT: " + ex.getMessage(), ex
            );
        }
    }

    /**
     * Phát hành JWT mới — Dùng cho internal service-to-service hoặc test setup.
     * Production: Nên dùng Authorization Server riêng (Keycloak, AWS Cognito).
     *
     * @param clientId  Subject claim (định danh client)
     * @param roles     Danh sách role (gắn vào claim "roles")
     * @return JWT token string
     */
    public String issueToken(String clientId, Object roles) {
        Instant now = Clock.systemUTC().instant();
        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plusSeconds(jwtProperties.getExpirationSeconds()));

        return Jwts.builder()
                .subject(clientId)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .claim("roles", roles)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Trích xuất raw JWT từ header "Bearer eyJ..." hoặc token thuần.
     */
    private String extractRawToken(String bearerToken) throws JwtValidationException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new JwtValidationException(
                    JwtValidationException.Reason.MISSING_OR_MALFORMED,
                    "Authorization header rỗng hoặc thiếu"
            );
        }
        if (bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();
            if (token.isBlank()) {
                throw new JwtValidationException(
                        JwtValidationException.Reason.MISSING_OR_MALFORMED,
                        "Bearer token rỗng sau prefix"
                );
            }
            return token;
        }
        return bearerToken.trim();
    }

    /**
     * Tạo SecretKey từ cấu hình.
     * - Thử parse raw bytes UTF-8 trước (phổ biến nhất với plain text secret).
     * - Nếu quá ngắn (<32 bytes): padding đến 32 bytes.
     * - Production recommendation: Dùng secret ≥ 32 ký tự ASCII để đảm bảo 256-bit.
     */
    private SecretKey getSigningKey() {
        String secret = jwtProperties.getSecret();
        byte[] rawBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Đảm bảo tối thiểu 32 bytes (256 bits) cho HS256
        if (rawBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(rawBytes, 0, paddedKey, 0, rawBytes.length);
            return Keys.hmacShaKeyFor(paddedKey);
        }
        return Keys.hmacShaKeyFor(rawBytes);
    }
}

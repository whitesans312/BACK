package com.ergpos.app.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ergpos.app.model.Usuario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long expirationMs;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:28800000}") long expirationMs) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(Usuario usuario) {
        Instant now = Instant.now();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", usuario.getEmail());
        claims.put("userId", usuario.getId().toString());
        claims.put("name", usuario.getNombre());
        claims.put("role", usuario.getRol().getNombre());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusMillis(expirationMs).getEpochSecond());

        String unsignedToken = base64UrlJson(header) + "." + base64UrlJson(claims);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String extractEmail(String token) {
        Object subject = parseClaims(token).get("sub");
        return subject instanceof String ? (String) subject : null;
    }

    public boolean isTokenValid(String token) {
        try {
            Map<String, Object> claims = parseClaims(token);
            Object exp = claims.get("exp");
            if (!(exp instanceof Number)) return false;
            return ((Number) exp).longValue() > Instant.now().getEpochSecond();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Object> parseClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Token JWT invalido");

        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new IllegalArgumentException("Firma JWT invalida");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, CLAIMS_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Payload JWT invalido", ex);
        }
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo construir el token JWT", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token JWT", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}

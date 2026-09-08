package hn.shadowcore.mercadox.appointments.google.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hn.shadowcore.mercadox.library.entity.model.appointments.GoogleOAuthAuthorizationTransaction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class RedisGoogleOAuthStateStore implements GoogleOAuthStateStore {

    private static final String KEY_PREFIX = "appointments:google-oauth:state:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisGoogleOAuthStateStore(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean save(String state,
                        GoogleOAuthAuthorizationTransaction transaction,
                        Duration ttl) {
        try {
            String value = objectMapper.writeValueAsString(transaction);
            Boolean stored = redisTemplate.opsForValue()
                    .setIfAbsent(key(state), value, ttl);
            return Boolean.TRUE.equals(stored);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the OAuth transaction", exception);
        }
    }

    @Override
    public Optional<GoogleOAuthAuthorizationTransaction> consume(String state) {
        String value = redisTemplate.opsForValue().getAndDelete(key(state));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    value, GoogleOAuthAuthorizationTransaction.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize the OAuth transaction", exception);
        }
    }

    private String key(String state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(state.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

package hn.shadowcore.mercadox.appointments.google.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import hn.shadowcore.mercadox.library.entity.model.appointments.GoogleOAuthAuthorizationTransaction;
import hn.shadowcore.mercadox.library.entity.model.enums.GoogleCalendarAuthorizationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGoogleOAuthStateStoreTest {

    private static final String STATE =
            "VvKXj-Ym9jllNw2P7zSybV9KO4BqC8Xw0jmc7v9SdPo";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisGoogleOAuthStateStore store;
    private GoogleOAuthAuthorizationTransaction transaction;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        store = new RedisGoogleOAuthStateStore(redisTemplate, objectMapper);
        transaction = new GoogleOAuthAuthorizationTransaction(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "admin@clinic.example",
                "/settings/appointments/integrations",
                GoogleCalendarAuthorizationPurpose.CONNECT,
                Instant.parse("2026-09-02T18:30:00Z"),
                Instant.parse("2026-09-02T18:40:00Z"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveUsesHashedKeyAndAtomicTtl() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(TTL)))
                .thenReturn(true);

        boolean saved = store.save(STATE, transaction, TTL);

        assertThat(saved).isTrue();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                keyCaptor.capture(), anyString(), eq(TTL));
        assertThat(keyCaptor.getValue())
                .startsWith("appointments:google-oauth:state:")
                .doesNotContain(STATE);
    }

    @Test
    void consumeAtomicallyDeletesAndDeserializesTransaction() throws Exception {
        String serialized = objectMapper.writeValueAsString(transaction);
        when(valueOperations.getAndDelete(anyString())).thenReturn(serialized);

        Optional<GoogleOAuthAuthorizationTransaction> result = store.consume(STATE);

        assertThat(result).contains(transaction);
        verify(valueOperations).getAndDelete(anyString());
    }
}

package hn.alturaforge.mercadox.appointments.google.oauth;

import hn.alturaforge.mercadox.appointments.config.GoogleCalendarOAuthProperties;
import hn.alturaforge.mercadox.library.entity.model.appointments.GoogleOAuthAuthorizationTransaction;
import hn.alturaforge.mercadox.library.entity.model.enums.GoogleCalendarAuthorizationPurpose;
import hn.alturaforge.mercadox.library.entity.model.enums.GoogleCalendarConnectionStatus;
import hn.alturaforge.mercadox.library.entity.response.dto.appointments.GoogleCalendarAuthorizationStart;
import hn.alturaforge.mercadox.library.jpa.repository.GoogleCalendarConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarAuthorizationServiceTest {

    private static final UUID ORG_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ADMIN_EMAIL = "admin@clinic.example";
    private static final String RETURN_PATH = "/settings/appointments/integrations";
    private static final String STATE =
            "VvKXj-Ym9jllNw2P7zSybV9KO4BqC8Xw0jmc7v9SdPo";
    private static final Instant NOW = Instant.parse("2026-09-02T18:30:00Z");

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;

    @Mock
    private GoogleOAuthStateStore stateStore;

    @Mock
    private GoogleOAuthStateGenerator stateGenerator;

    private GoogleCalendarOAuthProperties properties;
    private GoogleCalendarAuthorizationService service;

    @BeforeEach
    void setUp() {
        properties = new GoogleCalendarOAuthProperties();
        properties.setClientId("client-id.apps.googleusercontent.com");
        properties.setRedirectUri(URI.create(
                "https://api.mercadox.example/api/v1/google-calendar/connections/callback"));
        properties.setStateTtl(Duration.ofMinutes(10));
        properties.setAllowedReturnPaths(java.util.List.of(RETURN_PATH));
        service = new GoogleCalendarAuthorizationService(
                connectionRepository,
                stateStore,
                stateGenerator,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startCreatesOfflineAuthorizationAndTenantBoundState() {
        when(connectionRepository.findStatusByOrgId(ORG_ID)).thenReturn(Optional.empty());
        when(stateGenerator.generate()).thenReturn(STATE);
        when(stateStore.save(eq(STATE), any(), eq(Duration.ofMinutes(10))))
                .thenReturn(true);

        GoogleCalendarAuthorizationStart result = service.start(ORG_ID, ADMIN_EMAIL, RETURN_PATH);

        assertThat(result.purpose())
                .isEqualTo(GoogleCalendarAuthorizationPurpose.CONNECT);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));

        MultiValueMap<String, String> query = UriComponentsBuilder
                .fromUri(result.authorizationUrl())
                .build()
                .getQueryParams();
        assertThat(query.getFirst("client_id"))
                .isEqualTo("client-id.apps.googleusercontent.com");
        assertThat(query.getFirst("redirect_uri")).isEqualTo(
                "https://api.mercadox.example/api/v1/google-calendar/connections/callback");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("state")).isEqualTo(STATE);
        assertThat(query.getFirst("access_type")).isEqualTo("offline");
        assertThat(query.getFirst("include_granted_scopes")).isEqualTo("true");
        assertThat(UriUtils.decode(query.getFirst("prompt"), StandardCharsets.UTF_8))
                .isEqualTo("consent select_account");
        String scopes = UriUtils.decode(query.getFirst("scope"), StandardCharsets.UTF_8);
        assertThat(Set.copyOf(Arrays.asList(scopes.split(" "))))
                .containsExactlyInAnyOrder(
                        "openid",
                        "email",
                        "https://www.googleapis.com/auth/calendar.calendarlist.readonly",
                        "https://www.googleapis.com/auth/calendar.events.freebusy",
                        "https://www.googleapis.com/auth/calendar.events");

        ArgumentCaptor<GoogleOAuthAuthorizationTransaction> transactionCaptor =
                ArgumentCaptor.forClass(GoogleOAuthAuthorizationTransaction.class);
        verify(stateStore).save(
                eq(STATE), transactionCaptor.capture(), eq(Duration.ofMinutes(10)));
        GoogleOAuthAuthorizationTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.orgId()).isEqualTo(ORG_ID);
        assertThat(transaction.administratorEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(transaction.returnPath()).isEqualTo(RETURN_PATH);
        assertThat(transaction.purpose())
                .isEqualTo(GoogleCalendarAuthorizationPurpose.CONNECT);
        assertThat(transaction.createdAt()).isEqualTo(NOW);
        assertThat(transaction.expiresAt()).isEqualTo(result.expiresAt());
    }

    @Test
    void startMarksReauthorizationWhenConnectionNeedsConsent() {
        when(connectionRepository.findStatusByOrgId(ORG_ID))
                .thenReturn(Optional.of(GoogleCalendarConnectionStatus.REAUTH_REQUIRED));
        when(stateGenerator.generate()).thenReturn(STATE);
        when(stateStore.save(eq(STATE), any(), any())).thenReturn(true);

        GoogleCalendarAuthorizationStart result = service.start(ORG_ID, ADMIN_EMAIL, RETURN_PATH);

        assertThat(result.purpose())
                .isEqualTo(GoogleCalendarAuthorizationPurpose.REAUTHORIZE);
    }

    @Test
    void startRejectsAnActiveConnectionBeforeCreatingState() {
        when(connectionRepository.findStatusByOrgId(ORG_ID))
                .thenReturn(Optional.of(GoogleCalendarConnectionStatus.ACTIVE));

        assertThatThrownBy(() -> service.start(ORG_ID, ADMIN_EMAIL, RETURN_PATH))
                .isInstanceOf(GoogleCalendarAuthorizationException.class)
                .satisfies(exception -> assertThat(
                        ((GoogleCalendarAuthorizationException) exception).getReason())
                        .isEqualTo(GoogleCalendarAuthorizationException.Reason
                                .CONNECTION_ALREADY_ACTIVE));

        verify(stateStore, never()).save(any(), any(), any());
    }

    @Test
    void startRejectsReturnPathOutsideServerAllowlist() {
        assertThatThrownBy(() -> service.start(
                ORG_ID, ADMIN_EMAIL, "https://attacker.example/callback"))
                .isInstanceOf(GoogleCalendarAuthorizationException.class)
                .satisfies(exception -> assertThat(
                        ((GoogleCalendarAuthorizationException) exception).getReason())
                        .isEqualTo(GoogleCalendarAuthorizationException.Reason
                                .INVALID_RETURN_PATH));

        verify(connectionRepository, never()).findStatusByOrgId(any());
        verify(stateStore, never()).save(any(), any(), any());
    }
}

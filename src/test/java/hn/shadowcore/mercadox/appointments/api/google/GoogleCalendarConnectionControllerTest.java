package hn.shadowcore.mercadox.appointments.api.google;

import hn.shadowcore.mercadox.appointments.config.AppointmentsSecurityConfig;
import hn.shadowcore.mercadox.appointments.config.TimeConfig;
import hn.shadowcore.mercadox.appointments.google.oauth.GoogleCalendarAuthorizationService;
import hn.shadowcore.mercadox.context.security.JwtVerifier;
import hn.shadowcore.mercadox.context.security.MercadoXJwtAutoConfiguration;
import hn.shadowcore.mercadox.context.security.VerifiedJwt;
import hn.shadowcore.mercadox.library.entity.model.enums.GoogleCalendarAuthorizationPurpose;
import hn.shadowcore.mercadox.library.entity.response.dto.appointments.GoogleCalendarAuthorizationStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GoogleCalendarConnectionController.class,
        properties = "spring.jackson.deserialization.fail-on-unknown-properties=true")
@Import({AppointmentsSecurityConfig.class, TimeConfig.class})
@ImportAutoConfiguration(exclude = MercadoXJwtAutoConfiguration.class)
class GoogleCalendarConnectionControllerTest {

    private static final String ENDPOINT =
            "/api/v1/google-calendar/connections/start";
    private static final UUID ORG_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ADMIN_EMAIL = "admin@clinic.example";
    private static final String CORRELATION_ID = "calendar-onboarding-test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleCalendarAuthorizationService authorizationService;

    @MockitoBean
    private JwtVerifier jwtVerifier;

    @BeforeEach
    void setUp() {
        when(jwtVerifier.validateToken("valid-admin-token")).thenReturn(true);
        when(jwtVerifier.verify("valid-admin-token")).thenReturn(new VerifiedJwt(
                ADMIN_EMAIL,
                ORG_ID.toString(),
                List.of("ROLE_ORG_ADMIN")));
    }

    @Test
    void startUsesVerifiedTenantAndReturnsContractEnvelope() throws Exception {
        GoogleCalendarAuthorizationStart authorization = new GoogleCalendarAuthorizationStart(
                URI.create("https://accounts.google.com/o/oauth2/v2/auth?state=opaque"),
                Instant.parse("2026-09-02T18:40:00Z"),
                GoogleCalendarAuthorizationPurpose.CONNECT);
        when(authorizationService.start(
                ORG_ID,
                ADMIN_EMAIL,
                "/settings/appointments/integrations"))
                .thenReturn(authorization);

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "/settings/appointments/integrations"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.code").value("GOOGLE_AUTHORIZATION_STARTED"))
                .andExpect(jsonPath("$.httpStatusCode").value(200))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.payload.authorizationUrl")
                        .value("https://accounts.google.com/o/oauth2/v2/auth?state=opaque"))
                .andExpect(jsonPath("$.payload.expiresAt")
                        .value("2026-09-02T18:40:00Z"))
                .andExpect(jsonPath("$.payload.purpose").value("CONNECT"));

        verify(authorizationService).start(
                ORG_ID,
                ADMIN_EMAIL,
                "/settings/appointments/integrations");
    }

    @Test
    void startRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "/settings/appointments/integrations"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.httpStatusCode").value(401));
    }

    @Test
    void startRejectsNonAdministrator() throws Exception {
        when(jwtVerifier.validateToken("valid-user-token")).thenReturn(true);
        when(jwtVerifier.verify("valid-user-token")).thenReturn(new VerifiedJwt(
                "user@clinic.example",
                ORG_ID.toString(),
                List.of("ROLE_USER")));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "/settings/appointments/integrations"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.httpStatusCode").value(403));
    }

    @Test
    void startRejectsMalformedTenantClaim() throws Exception {
        when(jwtVerifier.validateToken("malformed-tenant-token")).thenReturn(true);
        when(jwtVerifier.verify("malformed-tenant-token")).thenReturn(new VerifiedJwt(
                ADMIN_EMAIL,
                "not-a-uuid",
                List.of("ROLE_ORG_ADMIN")));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-tenant-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "/settings/appointments/integrations"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(authorizationService, never()).start(any(), any(), any());
    }

    @Test
    void startRejectsUnknownTenantInput() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "/settings/appointments/integrations",
                                  "orgId": "22222222-2222-2222-2222-222222222222"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void startRejectsMalformedReturnPath() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnPath": "https://attacker.example/callback"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("returnPath"));
    }
}

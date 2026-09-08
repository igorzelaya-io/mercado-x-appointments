package hn.shadowcore.mercadox.appointments.api.google;

import hn.shadowcore.mercadox.appointments.google.oauth.GoogleCalendarAuthorizationService;
import hn.shadowcore.mercadox.appointments.security.AppointmentsTenantContextFilter;
import hn.shadowcore.mercadox.appointments.security.CorrelationIdFilter;
import hn.shadowcore.mercadox.context.filter.JwtAuthFilter;
import hn.shadowcore.mercadox.context.security.VerifiedJwt;
import hn.shadowcore.mercadox.library.entity.request.appointments.StartGoogleCalendarConnectionRequest;
import hn.shadowcore.mercadox.library.entity.response.ApiResponse;
import hn.shadowcore.mercadox.library.entity.response.dto.appointments.GoogleCalendarAuthorizationStart;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/google-calendar/connections")
public class GoogleCalendarConnectionController {

    private static final String AUTHORIZATION_STARTED = "GOOGLE_AUTHORIZATION_STARTED";

    private final GoogleCalendarAuthorizationService authorizationService;
    private final Clock clock;

    public GoogleCalendarConnectionController(
            GoogleCalendarAuthorizationService authorizationService,
            Clock clock) {
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<GoogleCalendarAuthorizationStart>> startAuthorization(
            @RequestAttribute(AppointmentsTenantContextFilter.TENANT_ID_ATTRIBUTE)
            UUID orgId,
            @RequestAttribute(JwtAuthFilter.VERIFIED_JWT_ATTRIBUTE)
            VerifiedJwt verifiedJwt,
            @Valid @RequestBody StartGoogleCalendarConnectionRequest request,
            HttpServletRequest servletRequest) {
        GoogleCalendarAuthorizationStart authorization = authorizationService.start(
                orgId,
                verifiedJwt.email(),
                request.returnPath());
        String correlationId = correlationId(servletRequest);
        ApiResponse<GoogleCalendarAuthorizationStart> response = new ApiResponse<>(
                HttpStatus.OK,
                AUTHORIZATION_STARTED,
                "Google Calendar authorization started.",
                Instant.now(clock),
                correlationId,
                authorization);

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                .body(response);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String string ? string : "unavailable";
    }
}

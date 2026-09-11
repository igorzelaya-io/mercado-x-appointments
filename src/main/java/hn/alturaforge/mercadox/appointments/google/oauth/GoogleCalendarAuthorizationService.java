package hn.alturaforge.mercadox.appointments.google.oauth;

import hn.alturaforge.mercadox.appointments.config.GoogleCalendarOAuthProperties;
import hn.alturaforge.mercadox.library.entity.model.appointments.GoogleOAuthAuthorizationTransaction;
import hn.alturaforge.mercadox.library.entity.model.enums.GoogleCalendarAuthorizationPurpose;
import hn.alturaforge.mercadox.library.entity.response.dto.appointments.GoogleCalendarAuthorizationStart;
import hn.alturaforge.mercadox.library.jpa.repository.GoogleCalendarConnectionRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleCalendarAuthorizationService {

    private static final List<String> REQUIRED_SCOPES = List.of(
            "openid",
            "email",
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly",
            "https://www.googleapis.com/auth/calendar.events.freebusy",
            "https://www.googleapis.com/auth/calendar.events"
    );

    private static final int STATE_SAVE_ATTEMPTS = 3;

    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleOAuthStateStore stateStore;
    private final GoogleOAuthStateGenerator stateGenerator;
    private final GoogleCalendarOAuthProperties properties;
    private final Clock clock;

    public GoogleCalendarAuthorizationService(
            GoogleCalendarConnectionRepository connectionRepository,
            GoogleOAuthStateStore stateStore,
            GoogleOAuthStateGenerator stateGenerator,
            GoogleCalendarOAuthProperties properties,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.stateStore = stateStore;
        this.stateGenerator = stateGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    public GoogleCalendarAuthorizationStart start(UUID orgId,
                                                  String administratorEmail,
                                                  String returnPath) {
        validateReturnPath(returnPath);
        GoogleCalendarAuthorizationPurpose purpose = resolvePurpose(orgId);
        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(properties.getStateTtl());

        for (int attempt = 0; attempt < STATE_SAVE_ATTEMPTS; attempt++) {
            String state = stateGenerator.generate();
            GoogleOAuthAuthorizationTransaction transaction =
                    new GoogleOAuthAuthorizationTransaction(
                            orgId,
                            administratorEmail,
                            returnPath,
                            purpose,
                            createdAt,
                            expiresAt);
            try {
                if (stateStore.save(state, transaction, properties.getStateTtl())) {
                    return new GoogleCalendarAuthorizationStart(
                            buildAuthorizationUri(state),
                            expiresAt,
                            purpose);
                }
            } catch (RuntimeException exception) {
                throw new GoogleCalendarAuthorizationException(
                        GoogleCalendarAuthorizationException.Reason.STATE_STORE_UNAVAILABLE,
                        "Google Calendar authorization could not be started.",
                        exception);
            }
        }

        throw new GoogleCalendarAuthorizationException(
                GoogleCalendarAuthorizationException.Reason.STATE_STORE_UNAVAILABLE,
                "Google Calendar authorization could not be started.");
    }

    private void validateReturnPath(String returnPath) {
        if (!properties.getAllowedReturnPaths().contains(returnPath)) {
            throw new GoogleCalendarAuthorizationException(
                    GoogleCalendarAuthorizationException.Reason.INVALID_RETURN_PATH,
                    "The requested return path is not allowed.");
        }
    }

    private GoogleCalendarAuthorizationPurpose resolvePurpose(UUID orgId) {
        return connectionRepository.findStatusByOrgId(orgId)
                .map(status -> switch (status) {
                    case REAUTH_REQUIRED -> GoogleCalendarAuthorizationPurpose.REAUTHORIZE;
                    case REVOKED -> GoogleCalendarAuthorizationPurpose.CONNECT;
                    case ACTIVE -> throw new GoogleCalendarAuthorizationException(
                            GoogleCalendarAuthorizationException.Reason.CONNECTION_ALREADY_ACTIVE,
                            "The organization already has an active Google Calendar connection.");
                })
                .orElse(GoogleCalendarAuthorizationPurpose.CONNECT);
    }

    private URI buildAuthorizationUri(String state) {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(properties.getAuthorizationUri().toString())
                .clientId(properties.getClientId())
                .redirectUri(properties.getRedirectUri().toString())
                .scopes(new LinkedHashSet<>(REQUIRED_SCOPES))
                .state(state)
                .additionalParameters(Map.of(
                        "access_type", "offline",
                        "include_granted_scopes", "true",
                        "prompt", "consent select_account"))
                .build();

        return URI.create(request.getAuthorizationRequestUri());
    }
}

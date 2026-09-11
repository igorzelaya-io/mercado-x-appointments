package hn.alturaforge.mercadox.appointments.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "google.calendar.oauth")
public class GoogleCalendarOAuthProperties {

    private static final URI DEFAULT_AUTHORIZATION_URI =
            URI.create("https://accounts.google.com/o/oauth2/v2/auth");

    @NotBlank
    private String clientId;

    @NotNull
    private URI redirectUri;

    @NotNull
    private URI authorizationUri = DEFAULT_AUTHORIZATION_URI;

    @NotNull
    private Duration stateTtl = Duration.ofMinutes(10);

    @NotEmpty
    private List<@NotBlank String> allowedReturnPaths =
            List.of("/settings/appointments/integrations");

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public URI getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(URI redirectUri) {
        this.redirectUri = redirectUri;
    }

    public URI getAuthorizationUri() {
        return authorizationUri;
    }

    public void setAuthorizationUri(URI authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    public Duration getStateTtl() {
        return stateTtl;
    }

    public void setStateTtl(Duration stateTtl) {
        this.stateTtl = stateTtl;
    }

    public List<String> getAllowedReturnPaths() {
        return allowedReturnPaths;
    }

    public void setAllowedReturnPaths(List<String> allowedReturnPaths) {
        this.allowedReturnPaths = allowedReturnPaths == null
                ? List.of()
                : List.copyOf(allowedReturnPaths);
    }

    @AssertTrue(message = "state TTL must be between one minute and thirty minutes")
    public boolean isStateTtlWithinAllowedRange() {
        return stateTtl != null
                && !stateTtl.isNegative()
                && stateTtl.compareTo(Duration.ofMinutes(1)) >= 0
                && stateTtl.compareTo(Duration.ofMinutes(30)) <= 0;
    }

    @AssertTrue(message = "allowed return paths must be relative application paths")
    public boolean areReturnPathsRelative() {
        return allowedReturnPaths != null
                && allowedReturnPaths.stream().allMatch(path ->
                path != null && path.startsWith("/") && !path.startsWith("//")
                        && !path.contains("://"));
    }
}

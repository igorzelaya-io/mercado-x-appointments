package hn.shadowcore.mercadox.appointments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import hn.shadowcore.mercadox.appointments.security.AppointmentsTenantContextFilter;
import hn.shadowcore.mercadox.appointments.security.CorrelationIdFilter;
import hn.shadowcore.mercadox.context.filter.JwtAuthFilter;
import hn.shadowcore.mercadox.context.security.JwtVerifier;
import hn.shadowcore.mercadox.library.entity.response.ApiError;
import hn.shadowcore.mercadox.library.entity.response.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class AppointmentsSecurityConfig {

    private static final String CALLBACK_PATH =
            "/api/v1/google-calendar/connections/callback";

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AppointmentsSecurityConfig(JwtVerifier jwtVerifier,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Bean
    public SecurityFilterChain appointmentsSecurityFilterChain(HttpSecurity http) throws Exception {
        CorrelationIdFilter correlationIdFilter = new CorrelationIdFilter();
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtVerifier);
        AppointmentsTenantContextFilter tenantContextFilter =
                new AppointmentsTenantContextFilter();

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CALLBACK_PATH).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(request, response,
                                        HttpStatus.UNAUTHORIZED,
                                        ApiErrorCode.UNAUTHENTICATED,
                                        "MercadoX authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(request, response,
                                        HttpStatus.FORBIDDEN,
                                        ApiErrorCode.FORBIDDEN,
                                        "The authenticated user cannot administer appointment integrations.")))
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, CorrelationIdFilter.class)
                .addFilterAfter(tenantContextFilter, JwtAuthFilter.class)
                .build();
    }

    private void writeSecurityError(HttpServletRequest request,
                                    HttpServletResponse response,
                                    HttpStatus status,
                                    ApiErrorCode code,
                                    String message) throws IOException {
        String correlationId = correlationId(request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(status, code, message, Instant.now(clock), correlationId));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String string ? string : "unavailable";
    }
}

package hn.alturaforge.mercadox.appointments.security;

import hn.alturaforge.mercadox.context.filter.JwtAuthFilter;
import hn.alturaforge.mercadox.context.security.VerifiedJwt;
import hn.alturaforge.mercadox.context.utils.OrgIdContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class AppointmentsTenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_ATTRIBUTE =
            "hn.alturaforge.mercadox.appointments.TENANT_ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Object candidate = request.getAttribute(JwtAuthFilter.VERIFIED_JWT_ATTRIBUTE);
            if (candidate instanceof VerifiedJwt verifiedJwt) {
                establishTenantContext(request, verifiedJwt);
            }
            filterChain.doFilter(request, response);
        } finally {
            OrgIdContextHolder.clear();
        }
    }

    private void establishTenantContext(HttpServletRequest request, VerifiedJwt verifiedJwt) {
        if (!StringUtils.hasText(verifiedJwt.email())) {
            SecurityContextHolder.clearContext();
            request.removeAttribute(JwtAuthFilter.VERIFIED_JWT_ATTRIBUTE);
            return;
        }

        try {
            UUID orgId = UUID.fromString(verifiedJwt.orgId());
            OrgIdContextHolder.setTenantId(orgId.toString());
            request.setAttribute(TENANT_ID_ATTRIBUTE, orgId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            SecurityContextHolder.clearContext();
            request.removeAttribute(JwtAuthFilter.VERIFIED_JWT_ATTRIBUTE);
        }
    }
}

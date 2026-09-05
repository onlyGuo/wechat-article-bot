package ink.icoding.wechat.article.audit;

import ink.icoding.wechat.article.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditLogMapper mapper;

    public AuditInterceptor(AuditLogMapper mapper) { this.mapper = mapper; }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!MUTATING.contains(request.getMethod()) || response.getStatus() >= 400
                || request.getRequestURI().equals("/api/auth/login")) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) return;
        AuditLog log = new AuditLog();
        log.setUserId(user.id());
        log.setAction(request.getMethod());
        log.setResourceType(resourceType(request.getRequestURI()));
        log.setResourceId(request.getRequestURI());
        log.setDetail("HTTP " + response.getStatus());
        log.setIpAddress(clientIp(request));
        log.setCreatedAt(java.time.LocalDateTime.now());
        try { mapper.insert(log); } catch (Exception ignored) { }
    }

    private String resourceType(String uri) {
        String[] segments = uri.split("/");
        return segments.length > 2 ? segments[2].toUpperCase() : "API";
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}

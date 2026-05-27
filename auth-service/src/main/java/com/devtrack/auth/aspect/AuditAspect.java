package com.devtrack.auth.aspect;

import com.devtrack.auth.entity.AuditLog;
import com.devtrack.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

@Aspect
@Component
public class AuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private HttpServletRequest request;

    @AfterReturning(pointcut = "@annotation(com.devtrack.auth.aspect.Auditable)", returning = "result")
    public void auditMethodExecution(JoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Auditable auditable = method.getAnnotation(Auditable.class);

            String action = auditable.action();
            String username = "ANONYMOUS";

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                    !"anonymousUser".equals(authentication.getPrincipal())) {
                username = authentication.getName();
            }

            String ipAddress = "UNKNOWN";
            if (request != null) {
                ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty()) {
                    ipAddress = request.getRemoteAddr();
                }
            }

            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("Method: ").append(method.getName()).append("; ");
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                detailsBuilder.append("Arguments: ").append(Arrays.toString(args));
            }

            String details = detailsBuilder.toString();
            if (details.contains("password")) {
                details = details.replaceAll("password=\\S+", "password=[REDACTED]");
                details = details.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"[REDACTED]\"");
            }

            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .username(username)
                    .details(details)
                    .ipAddress(ipAddress)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
            logger.info("Audit log saved: Action={}", action);

        } catch (Exception e) {
            logger.error("Failed to capture and record method audit log", e);
        }
    }
}

package com.devtrack.auth.service;

import com.devtrack.auth.entity.AuditLog;
import com.devtrack.auth.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public Page<AuditLog> getAuditLogs(String username, String action, Pageable pageable) {
        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasAction = action != null && !action.trim().isEmpty();

        if (hasUsername && hasAction) {
            return auditLogRepository.findByUsernameContainingIgnoreCaseAndActionContainingIgnoreCase(
                    username.trim(), action.trim(), pageable);
        } else if (hasUsername) {
            return auditLogRepository.findByUsernameContainingIgnoreCase(username.trim(), pageable);
        } else if (hasAction) {
            return auditLogRepository.findByActionContainingIgnoreCase(action.trim(), pageable);
        } else {
            return auditLogRepository.findAll(pageable);
        }
    }
}

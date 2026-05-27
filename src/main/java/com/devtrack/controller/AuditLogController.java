package com.devtrack.controller;

import com.devtrack.entity.AuditLog;
import com.devtrack.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogs(username, action, pageable);
        return ResponseEntity.ok(logs);
    }
}

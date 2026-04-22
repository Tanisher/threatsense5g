package com.threatsense.service;

import com.threatsense.model.AuditLog;
import com.threatsense.model.User;
import com.threatsense.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public Page<AuditLog> findFiltered(String username, String action, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditLogRepository.findFiltered(username, action, from, to, pageable);
    }

    public void log(Long userId, String action, String entityType, Long entityId, String detail) {
        User user = null;
        if (userId != null) {
            user = new User();
            user.setId(userId);
        }
        log(user, action, entityType, entityId, detail);
    }

    public void log(User user, String action, String entityType, Long entityId, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .detail(detail)
                .build();
        auditLogRepository.save(auditLog);
    }
}


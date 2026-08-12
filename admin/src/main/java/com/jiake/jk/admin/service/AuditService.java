package com.jiake.jk.admin.service;

import com.jiake.jk.admin.entity.Audit;

import java.util.List;

public interface AuditService {
    void addRecord(Audit audit);

    void deleteAudit(Long id);

    List<Audit> getAudits(Long id, Long adminId, String requestMethod, String requestPath, String createdTime);

    List<Audit> getAudits(Long lastMaxId);
}

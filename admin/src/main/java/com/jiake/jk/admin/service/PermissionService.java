package com.jiake.jk.admin.service;

import java.util.Set;

public interface PermissionService {
    Set<String> getAdminPermissions(Long id);

    Set<String> getAllPermissions();
}

package com.jiake.jk.admin.controller;

import com.jiake.jk.admin.annotations.RequiresPermission;
import com.jiake.jk.admin.entity.Audit;
import com.jiake.jk.admin.service.AuditService;
import com.jiake.jk.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Audit")
@RestController
@RequestMapping("/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Operation(summary = "搜索审计")
    @GetMapping
    @RequiresPermission({"AUDIT:SELECT"})
    public Result<List<Audit>> getAudits(@RequestParam(required = false) Long id,
                                         @RequestParam(required = false) Long adminId,
                                         @RequestParam(required = false) String requestMethod,
                                         @RequestParam(required = false) String requestPath,
                                         @RequestParam(required = false) String createdTime) {
        return Result.success(auditService.getAudits(id, adminId, requestMethod, requestPath, createdTime));
    }

    @Operation(summary = "获取审计列表")
    @GetMapping("/list")
    @RequiresPermission({"AUDIT:SELECT"})
    public Result<List<Audit>> getAudits(@RequestParam Long lastMaxId) {
        return Result.success(auditService.getAudits(lastMaxId));
    }

    @Operation(summary = "删除审计")
    @DeleteMapping("/{id}")
    @RequiresPermission({"AUDIT:DELETE"})
    public Result<Void> deleteAudit(@PathVariable Long id) {
        auditService.deleteAudit(id);
        return Result.success();
    }
}

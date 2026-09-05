package ink.icoding.wechat.article.audit;

import ink.icoding.wechat.article.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogMapper mapper;
    public AuditController(AuditLogMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public ApiResponse<List<AuditLog>> list(@RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(mapper.findRecent(Math.min(500, Math.max(1, limit))));
    }
}

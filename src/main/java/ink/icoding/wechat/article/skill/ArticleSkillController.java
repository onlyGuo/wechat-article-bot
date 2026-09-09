package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class ArticleSkillController {
    private final ArticleSkillService service;
    public ArticleSkillController(ArticleSkillService service) { this.service = service; }
    @GetMapping public ApiResponse<List<ArticleSkill>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/{id}") public ApiResponse<ArticleSkill> get(@PathVariable Long id) { return ApiResponse.ok(service.required(id)); }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> create(@Valid @RequestBody ArticleSkillService.SkillRequest request) {
        return ApiResponse.ok(service.create(request));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> update(@PathVariable Long id, @Valid @RequestBody ArticleSkillService.SkillRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }
    @PostMapping("/{id}/default")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> makeDefault(@PathVariable Long id) { return ApiResponse.ok(service.makeDefault(id)); }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}

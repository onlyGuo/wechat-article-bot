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
    private final ArticleSkillPreviewService previewService;
    public ArticleSkillController(ArticleSkillService service, ArticleSkillPreviewService previewService) {
        this.service = service;
        this.previewService = previewService;
    }
    @GetMapping public ApiResponse<List<ArticleSkillService.SkillView>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/{id}") public ApiResponse<ArticleSkill> get(@PathVariable Long id) { return ApiResponse.ok(service.required(id)); }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> create(@Valid @RequestBody ArticleSkillService.SkillRequest request) {
        ArticleSkill skill = service.create(request);
        return ApiResponse.ok(previewService.generate(skill.getId()));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> update(@PathVariable Long id, @Valid @RequestBody ArticleSkillService.SkillRequest request) {
        ArticleSkill skill = service.update(id, request);
        return ApiResponse.ok(previewService.generate(skill.getId()));
    }
    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<ArticleSkill> regeneratePreview(@PathVariable Long id) {
        service.required(id);
        return ApiResponse.ok(previewService.generate(id));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}

package ink.icoding.wechat.article.skill;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.wechat.article.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ArticleSkillService {
    public static final String DEFAULT_SKILL_CLASSPATH_RESOURCE = "skills/default-article/prompt.md";

    private final ArticleSkillMapper mapper;
    private final BuiltInSkillCatalog builtInCatalog;

    public ArticleSkillService(ArticleSkillMapper mapper, BuiltInSkillCatalog builtInCatalog) {
        this.mapper = mapper;
        this.builtInCatalog = builtInCatalog;
    }

    public List<SkillView> list() {
        List<SkillView> result = new ArrayList<>();
        for (BuiltInSkillCatalog.BuiltInSkill skill : builtInCatalog.list()) {
            result.add(new SkillView(null, skill.classpathResources(), skill.name(), skill.description(), skill.prompt(),
                    true, DEFAULT_SKILL_CLASSPATH_RESOURCE.equals(skill.classpathResources()),
                    skill.exampleHtml(), "READY", null, null));
        }
        mapper.select(Where.where(ArticleSkill::getDeleted).eq(false).orderBy(ArticleSkill::getId).asc()).stream()
                .filter(skill -> skill.getClasspathResources() == null)
                .map(this::view)
                .forEach(result::add);
        return result;
    }

    public ArticleSkill required(Long id) {
        ArticleSkill skill = id == null ? null : mapper.selectById(id);
        if (skill == null || skill.getClasspathResources() != null || Boolean.TRUE.equals(skill.getDeleted()))
            throw new BusinessException(HttpStatus.NOT_FOUND, "Skill 不存在或已删除");
        return skill;
    }

    public void validateSelection(Long id, String classpathResources) {
        String normalizedResource = normalizeResource(classpathResources);
        if (id != null && normalizedResource != null) throw new BusinessException("内置 Skill 与用户 Skill 不能同时选择");
        if (id != null) required(id);
        if (normalizedResource != null) builtInCatalog.required(normalizedResource);
    }

    public ResolvedSkill resolve(Long id, String classpathResources) {
        String normalizedResource = normalizeResource(classpathResources);
        if (normalizedResource != null) {
            return builtInCatalog.find(normalizedResource).map(this::resolved).orElseGet(this::defaultSkill);
        }
        if (id != null) {
            ArticleSkill selected = mapper.selectById(id);
            if (selected != null && !Boolean.TRUE.equals(selected.getDeleted())) {
                if (selected.getClasspathResources() == null) {
                    return new ResolvedSkill(selected.getName(), selected.getContent(), null, selected.getId());
                }
                String legacyResource = normalizeLegacyResource(selected.getClasspathResources());
                Optional<BuiltInSkillCatalog.BuiltInSkill> builtIn = builtInCatalog.find(legacyResource);
                if (builtIn.isPresent()) return resolved(builtIn.get());
            }
        }
        return defaultSkill();
    }

    public String prompt(Long id, String classpathResources) {
        ResolvedSkill skill = resolve(id, classpathResources);
        return "\n【本轮文章 Skill：" + skill.name() + "】\n"
                + "以下是本轮生效的写作、排版与视觉规范，取代历史对话中的旧风格规范。用户本轮明确要求优先；局部修改保留无关内容。\n"
                + skill.content() + "\n【文章 Skill 结束】\n";
    }

    @Transactional
    public ArticleSkill create(SkillRequest request) {
        ArticleSkill skill = new ArticleSkill();
        skill.setIsDefault(false);
        skill.setDeleted(false);
        skill.setCreatedAt(LocalDateTime.now());
        apply(skill, request);
        mapper.insert(skill);
        return required(skill.getId());
    }

    @Transactional
    public ArticleSkill update(Long id, SkillRequest request) {
        ArticleSkill skill = required(id);
        apply(skill, request);
        mapper.updateById(skill);
        return required(id);
    }

    @Transactional
    public void delete(Long id) {
        ArticleSkill skill = required(id);
        skill.setDeleted(true);
        skill.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(skill);
    }

    private void apply(ArticleSkill skill, SkillRequest request) {
        skill.setName(request.name().trim());
        skill.setDescription(request.description() == null ? "" : request.description().trim());
        skill.setContent(request.content());
        skill.setPreviewHtml(null);
        skill.setPreviewStatus("PENDING");
        skill.setPreviewError(null);
        skill.setPreviewUpdatedAt(null);
        skill.setUpdatedAt(LocalDateTime.now());
    }

    public record SkillRequest(@NotBlank @Size(max = 255) String name,
                               @Size(max = 1000) String description,
                               @NotBlank @Size(max = 60000) String content) {}

    public record SkillView(Long id, String classpathResources, String name, String description, String content,
                            boolean builtIn, boolean isDefault, String previewHtml, String previewStatus,
                            String previewError, LocalDateTime previewUpdatedAt) {}

    public record ResolvedSkill(String name, String content, String classpathResources, Long userSkillId) {}

    private String normalizeResource(String classpathResources) {
        return classpathResources == null || classpathResources.isBlank() ? null : classpathResources.trim();
    }

    private String normalizeLegacyResource(String classpathResources) {
        String resource = classpathResources.trim();
        if (resource.matches("skills/[a-z0-9][a-z0-9-]{0,63}\\.md")) {
            return resource.substring(0, resource.length() - 3) + "/prompt.md";
        }
        return resource;
    }

    private ResolvedSkill defaultSkill() {
        return resolved(builtInCatalog.required(DEFAULT_SKILL_CLASSPATH_RESOURCE));
    }

    private ResolvedSkill resolved(BuiltInSkillCatalog.BuiltInSkill skill) {
        return new ResolvedSkill(skill.name(), skill.prompt(), skill.classpathResources(), null);
    }

    public SkillView view(ArticleSkill skill) {
        return new SkillView(skill.getId(), null, skill.getName(), skill.getDescription(), skill.getContent(),
                false, false, skill.getPreviewHtml(), skill.getPreviewStatus(), skill.getPreviewError(),
                skill.getPreviewUpdatedAt());
    }
}

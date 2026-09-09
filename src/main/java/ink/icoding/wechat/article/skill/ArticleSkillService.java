package ink.icoding.wechat.article.skill;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.wechat.article.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArticleSkillService implements ApplicationRunner {
    private final ArticleSkillMapper mapper;

    public ArticleSkillService(ArticleSkillMapper mapper) { this.mapper = mapper; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Seed only once. Even a deleted built-in Skill keeps its row, so user edits are never reset.
        String content = new ClassPathResource("skills/default-article.md")
                .getContentAsString(StandardCharsets.UTF_8);
        mapper.executeSql("INSERT IGNORE INTO ARTICLE_SKILL (id,name,description,content,is_default,deleted,created_at,updated_at) VALUES (1,?,?,?,true,false,NOW(),NOW())",
                "默认公众号风格", "从原有文章提示词迁移的绿色章节排版，可自由编辑。", content);
    }

    public List<ArticleSkill> list() {
        return mapper.select(Where.where(ArticleSkill::getDeleted).eq(false)
                .orderBy(ArticleSkill::getIsDefault).desc().orderBy(ArticleSkill::getId).asc());
    }

    public ArticleSkill required(Long id) {
        ArticleSkill skill = id == null ? null : mapper.selectById(id);
        if (skill == null || Boolean.TRUE.equals(skill.getDeleted()))
            throw new BusinessException(HttpStatus.NOT_FOUND, "Skill 不存在或已删除");
        return skill;
    }

    public void validateSelection(Long id) { if (id != null) required(id); }

    public ArticleSkill resolve(Long id) {
        ArticleSkill selected = id == null ? null : mapper.selectById(id);
        if (selected != null && !Boolean.TRUE.equals(selected.getDeleted())) return selected;
        ArticleSkill fallback = mapper.selectFirst(Where.where(ArticleSkill::getDeleted).eq(false)
                .and(ArticleSkill::getIsDefault).eq(true));
        if (fallback == null) throw new BusinessException("请先设置默认 Skill");
        return fallback;
    }

    public String prompt(Long id) {
        ArticleSkill skill = resolve(id);
        return "\n【本轮文章 Skill：" + skill.getName() + "】\n"
                + "以下是本轮生效的写作、排版与视觉规范，取代历史对话中的旧风格规范。用户本轮明确要求优先；局部修改保留无关内容。\n"
                + skill.getContent() + "\n【文章 Skill 结束】\n";
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
        mapper.lockSkills();
        ArticleSkill skill = required(id);
        apply(skill, request);
        mapper.updateById(skill);
        return required(id);
    }

    @Transactional
    public ArticleSkill makeDefault(Long id) {
        mapper.lockSkills();
        ArticleSkill selected = required(id);
        for (ArticleSkill skill : list()) {
            boolean isDefault = skill.getId().equals(selected.getId());
            if (Boolean.TRUE.equals(skill.getIsDefault()) == isDefault) continue;
            skill.setIsDefault(isDefault);
            skill.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(skill);
        }
        return required(id);
    }

    @Transactional
    public void delete(Long id) {
        mapper.lockSkills();
        ArticleSkill skill = required(id);
        if (Boolean.TRUE.equals(skill.getIsDefault())) throw new BusinessException("请先将其他 Skill 设为默认，再删除当前默认 Skill");
        skill.setDeleted(true);
        skill.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(skill);
    }

    private void apply(ArticleSkill skill, SkillRequest request) {
        skill.setName(request.name().trim());
        skill.setDescription(request.description() == null ? "" : request.description().trim());
        skill.setContent(request.content());
        skill.setUpdatedAt(LocalDateTime.now());
    }

    public record SkillRequest(@NotBlank @Size(max = 255) String name,
                               @Size(max = 1000) String description,
                               @NotBlank @Size(max = 60000) String content) {}
}

package ink.icoding.wechat.article.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInSkillCatalogTests {

    private static final String CHINESE = ".*[\\u4e00-\\u9fff].*";

    @Test
    void discoversEveryBuiltInSkillWithPromptAndExample() throws IOException {
        List<BuiltInSkillCatalog.BuiltInSkill> skills = new BuiltInSkillCatalog().list();

        assertEquals(19, skills.size());
        assertEquals(ArticleSkillService.DEFAULT_SKILL_CLASSPATH_RESOURCE, skills.get(0).classpathResources());
        assertEquals(skills.size(), new HashSet<>(skills.stream()
                .map(BuiltInSkillCatalog.BuiltInSkill::classpathResources).toList()).size());

        for (BuiltInSkillCatalog.BuiltInSkill skill : skills) {
            String resource = skill.classpathResources();
            assertTrue(skill.name().matches(CHINESE), resource + " 的第一行名称必须是中文");
            assertTrue(skill.description().matches(CHINESE), resource + " 的第二行描述必须是中文");
            assertFalse(skill.prompt().isBlank(), resource + " 缺少提示词正文");
            assertTrue(skill.prompt().length() < 5_000, resource + " 的提示词过长");
            assertTrue(skill.prompt().contains("## 配图与图片生成"), resource + " 缺少配图生成规范");
            assertTrue(skill.prompt().contains("publicUrl"), resource + " 缺少素材 URL 约束");
            assertTrue(skill.prompt().contains("禁止"), resource + " 缺少明确的禁用项");
            assertFalse(skill.exampleHtml().isBlank(), resource + " 缺少示例 HTML");

            ClassPathResource example = new ClassPathResource(resource.replace("/prompt.md", "/index.html"));
            assertTrue(example.exists(), resource + " 缺少 index.html");
            String html = example.getContentAsString(StandardCharsets.UTF_8).toLowerCase();
            assertEquals(example.getContentAsString(StandardCharsets.UTF_8), skill.exampleHtml());
            assertFalse(html.contains("<script"), resource + " 的示例不应包含脚本");
        }
    }
}

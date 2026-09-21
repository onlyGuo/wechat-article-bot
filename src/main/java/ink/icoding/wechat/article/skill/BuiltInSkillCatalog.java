package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class BuiltInSkillCatalog {
    private static final String RESOURCE_PATTERN = "classpath*:skills/*/prompt.md";
    private static final Pattern SAFE_RESOURCE = Pattern.compile("skills/[a-z0-9][a-z0-9-]{0,63}/prompt\\.md");
    private static final Pattern CHINESE_TEXT = Pattern.compile(".*[\\u4e00-\\u9fff].*");
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<BuiltInSkill> list() {
        try {
            Map<String, BuiltInSkill> skills = new LinkedHashMap<>();
            for (Resource resource : resolver.getResources(RESOURCE_PATTERN)) {
                BuiltInSkill skill = read(resource, classpathResourceOf(resource));
                skills.putIfAbsent(skill.classpathResources(), skill);
            }
            return skills.values().stream()
                    .sorted(Comparator.comparing((BuiltInSkill skill) ->
                                    !ArticleSkillService.DEFAULT_SKILL_CLASSPATH_RESOURCE.equals(skill.classpathResources()))
                            .thenComparing(BuiltInSkill::classpathResources))
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("读取内置 Skill 目录失败", error);
        }
    }

    public Optional<BuiltInSkill> find(String classpathResources) {
        if (classpathResources == null || !SAFE_RESOURCE.matcher(classpathResources).matches()) return Optional.empty();
        ClassPathResource resource = new ClassPathResource(classpathResources);
        if (!resource.exists()) return Optional.empty();
        try {
            return Optional.of(read(resource, classpathResources));
        } catch (IOException error) {
            throw new IllegalStateException("读取内置 Skill 失败：" + classpathResources, error);
        }
    }

    public BuiltInSkill required(String classpathResources) {
        return find(classpathResources).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "内置 Skill 不存在：" + classpathResources));
    }

    private BuiltInSkill read(Resource resource, String classpathResources) throws IOException {
        List<String> lines = Arrays.asList(resource.getContentAsString(StandardCharsets.UTF_8).split("\\R", -1));
        if (lines.size() < 3 || !CHINESE_TEXT.matcher(lines.get(0)).matches()
                || !CHINESE_TEXT.matcher(lines.get(1)).matches()) {
            throw new IllegalStateException(classpathResources + " 的前两行必须分别是中文名称和中文描述");
        }
        String prompt = String.join("\n", lines.subList(2, lines.size())).trim();
        if (prompt.isBlank()) throw new IllegalStateException(classpathResources + " 缺少提示词正文");
        String examplePath = classpathResources.replace("/prompt.md", "/index.html");
        ClassPathResource example = new ClassPathResource(examplePath);
        if (!example.exists()) throw new IllegalStateException(examplePath + " 不存在");
        String exampleHtml = example.getContentAsString(StandardCharsets.UTF_8);
        return new BuiltInSkill(classpathResources, lines.get(0).trim(), lines.get(1).trim(), prompt, exampleHtml);
    }

    private String classpathResourceOf(Resource resource) throws IOException {
        String location = resource.getURI().toString().replace('\\', '/');
        int promptStart = location.lastIndexOf("/prompt.md");
        int directoryStart = promptStart < 0 ? -1 : location.lastIndexOf('/', promptStart - 1);
        if (directoryStart < 0) throw new IllegalStateException("无法识别内置 Skill 路径：" + location);
        String key = location.substring(directoryStart + 1, promptStart);
        String classpathResources = path(key);
        if (!SAFE_RESOURCE.matcher(classpathResources).matches()) {
            throw new IllegalStateException("内置 Skill 目录名无效：" + key);
        }
        return classpathResources;
    }

    private String path(String key) {
        return "skills/" + key + "/prompt.md";
    }

    public record BuiltInSkill(String classpathResources, String name, String description,
                               String prompt, String exampleHtml) {}
}

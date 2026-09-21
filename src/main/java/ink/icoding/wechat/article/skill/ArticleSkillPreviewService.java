package ink.icoding.wechat.article.skill;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.wechat.article.article.ArticleContentPolicy;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.LlmConfigService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ArticleSkillPreviewService {
    private static final Logger log = LoggerFactory.getLogger(ArticleSkillPreviewService.class);
    private static final String PREVIEW_AGENT_DESCRIPTION = """
            你是微信公众号 Skill 示例文章生成器。你的唯一任务是根据给定 Skill 创作一篇用于视觉预览的中文示例文章。
            示例必须真实体现 Skill 的语气、结构、配色、间距、组件和视觉节奏，让用户一眼判断这个 Skill 的最终效果。
            只输出一个以 main 元素为根的 HTML 片段，不要解释，不要 Markdown 代码围栏，不要输出完整 html/head/body 文档。
            所有样式逐元素写入 style 属性；禁止 style 标签、CSS 选择器、class、div、脚本、事件属性和外部 CSS。
            不使用外部图片；需要视觉内容时使用排版、色块、表格或安全的静态 SVG。正文控制在约 600～1000 个中文字符。
            """;

    private final ArticleSkillMapper mapper;
    private final LlmConfigService llmConfigService;

    public ArticleSkillPreviewService(ArticleSkillMapper mapper, LlmConfigService llmConfigService) {
        this.mapper = mapper;
        this.llmConfigService = llmConfigService;
    }

    public ArticleSkill generate(Long id) {
        ArticleSkill snapshot = required(id);
        snapshot.setPreviewStatus("GENERATING");
        snapshot.setPreviewError(null);
        mapper.updateById(snapshot);
        try {
            String previewHtml = generateHtml(snapshot);
            ArticleSkill current = required(id);
            if (!sameSource(snapshot, current)) return current;
            current.setPreviewHtml(previewHtml);
            current.setPreviewStatus("READY");
            current.setPreviewError(null);
            current.setPreviewUpdatedAt(LocalDateTime.now());
            mapper.updateById(current);
            return current;
        } catch (Exception error) {
            log.warn("生成 Skill 示例失败: skillId={}, message={}", id, readableError(error));
            ArticleSkill current = required(id);
            if (!sameSource(snapshot, current)) return current;
            current.setPreviewHtml(null);
            current.setPreviewStatus("FAILED");
            current.setPreviewError(limit(readableError(error), 2000));
            current.setPreviewUpdatedAt(LocalDateTime.now());
            mapper.updateById(current);
            return current;
        }
    }

    private String generateHtml(ArticleSkill skill) throws Exception {
        LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
        if (!config.available()) throw new BusinessException("LLM 尚未在系统设置中启用或未配置 API Key");

        AgentClient agent = new AgentClient();
        agent.setName("Skill 示例文章生成器");
        agent.setDescription(PREVIEW_AGENT_DESCRIPTION);
        agent.setModel(createModel(config));
        String command = """
                请为下面的 Skill 生成一篇全新的示例文章。自行选择一个适合展示该风格的具体主题，正文不要提及“Skill”“样张”或生成过程。

                Skill 名称：%s
                Skill 描述：%s

                Skill 完整规范：
                %s
                """.formatted(skill.getName(), skill.getDescription(), skill.getContent());
        AgentSessionResult result = agent.createSession().command(command);
        result.execute();
        return normalizeHtml(result.get(), skill.getName());
    }

    private LLMModel createModel(LlmConfigService.RuntimeConfig config) {
        ModelType modelType = switch (config.provider()) {
            case "ANTHROPIC" -> ModelType.Anthropic;
            case "OPENAI_RESPONSES" -> ModelType.OpenAIResponse;
            case "OPENAI_COMPATIBLE" -> ModelType.OpenAI;
            default -> throw new BusinessException("不支持的 LLM 服务类型：" + config.provider());
        };
        return LLMModel.create(modelType, config.baseUrl(), config.modelName(), config.apiKey());
    }

    private String normalizeHtml(String response, String skillName) {
        if (response == null || response.isBlank()) throw new BusinessException("LLM 没有返回示例 HTML");
        String value = response.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            if (firstLine >= 0) value = value.substring(firstLine + 1);
            int closingFence = value.lastIndexOf("```");
            if (closingFence >= 0) value = value.substring(0, closingFence);
        }
        int firstTag = value.indexOf('<');
        if (firstTag < 0) throw new BusinessException("LLM 返回内容中没有 HTML");
        value = value.substring(firstTag).trim();
        String lower = value.toLowerCase();
        int documentEnd = lower.lastIndexOf("</html>");
        if (documentEnd >= 0) value = value.substring(0, documentEnd + 7);
        else {
            int mainEnd = lower.lastIndexOf("</main>");
            if (mainEnd >= 0) value = value.substring(0, mainEnd + 7);
        }
        String fragment = ArticleContentPolicy.sanitize(value);
        try {
            ArticleContentPolicy.requireInlineStyles(fragment);
        } catch (IllegalArgumentException error) {
            throw new BusinessException("生成的示例不符合公众号 HTML 规范：" + error.getMessage());
        }
        var previewBody = Jsoup.parseBodyFragment(fragment).body();
        if (previewBody.children().isEmpty()) {
            throw new BusinessException("LLM 返回的示例 HTML 为空");
        }
        if (!"main".equals(previewBody.child(0).normalName())) {
            throw new BusinessException("生成的示例必须以 <main> 作为根节点");
        }
        String title = Entities.escape(skillName + "示例文章");
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src https: http: data:; font-src data:">
                <title>%s</title>
                </head>
                <body style="margin:0;padding:0;">%s</body>
                </html>
                """.formatted(title, fragment);
    }

    private ArticleSkill required(Long id) {
        ArticleSkill skill = id == null ? null : mapper.selectById(id);
        if (skill == null || skill.getClasspathResources() != null || Boolean.TRUE.equals(skill.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Skill 不存在或已删除");
        }
        return skill;
    }

    private boolean sameSource(ArticleSkill left, ArticleSkill right) {
        return Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getContent(), right.getContent());
    }

    private String readableError(Throwable throwable) {
        for (Throwable current = throwable; current != null && current.getCause() != current; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("敏感信息解密失败")) {
                return "LLM API Key 解密失败：请恢复保存凭据时使用的 APP_SECRET_KEY，或在系统设置中重新填写 API Key";
            }
        }
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "AI 示例生成失败，请稍后重试";
        if (message.contains("HTTP 401")) return "LLM 鉴权失败，请检查 API Key";
        if (message.contains("HTTP 403")) return "LLM 拒绝访问，请检查账号权限或 IP 白名单";
        if (message.contains("HTTP 404")) return "LLM 接口不存在，请检查 Base URL 和模型协议";
        if (message.contains("HTTP 429")) return "LLM 请求过于频繁或额度不足，请稍后重试";
        return message;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

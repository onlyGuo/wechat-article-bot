package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.article.ArticleContentPolicy;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Server-side article drafting tools used by unattended scheduled agents.
 */
public final class ScheduledArticleTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScheduledArticleTools() {
    }

    public static List<Tool> all(DraftState state) {
        return List.of(new ReadDraftTool(state), new SaveDraftTool(state), new SetDraftCoverTool(state));
    }

    public static final class DraftState {
        private String title;
        private String author;
        private String digest;
        private String contentHtml;
        private String sourceUrl;
        private Long coverAssetId;
        private long documentVersion;
        private boolean saved;

        public DraftState(Long defaultCoverAssetId) {
            this.coverAssetId = defaultCoverAssetId;
        }

        private synchronized String read() {
            return json(view());
        }

        private synchronized String save(SaveDraftParam param) {
            if (param.getTitle() == null || param.getTitle().isBlank()) {
                throw new IllegalArgumentException("文章标题不能为空");
            }
            if (param.getContentHtml() == null || param.getContentHtml().isBlank()) {
                throw new IllegalArgumentException("文章正文不能为空");
            }
            if (param.getTitle().length() > 64) throw new IllegalArgumentException("文章标题不能超过64字");
            if (param.getDigest() != null && param.getDigest().length() > 120) {
                throw new IllegalArgumentException("文章摘要不能超过120字");
            }
            ArticleContentPolicy.requireParagraphProse(param.getContentHtml());
            title = param.getTitle().trim();
            author = blankToNull(param.getAuthor());
            digest = blankToNull(param.getDigest());
            contentHtml = param.getContentHtml();
            sourceUrl = blankToNull(param.getSourceUrl());
            saved = true;
            documentVersion++;
            return json(Map.of("message", "文章草稿已保存到本轮任务工作区", "draft", view()));
        }

        private synchronized String setCover(SetDraftCoverParam param) {
            if (param.getAssetId() == null) throw new IllegalArgumentException("封面素材ID不能为空");
            coverAssetId = param.getAssetId();
            documentVersion++;
            return json(Map.of("message", "文章封面已设置", "assetId", coverAssetId,
                    "documentVersion", documentVersion));
        }

        public synchronized Draft snapshot() {
            if (!saved) throw new IllegalStateException("智能体没有通过 save_article_draft 提交文章");
            return new Draft(title, author, digest, contentHtml, sourceUrl, coverAssetId, documentVersion);
        }

        private Map<String, Object> view() {
            java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("title", title == null ? "" : title);
            value.put("author", author == null ? "" : author);
            value.put("digest", digest == null ? "" : digest);
            value.put("contentHtml", contentHtml == null ? "" : contentHtml);
            value.put("sourceUrl", sourceUrl == null ? "" : sourceUrl);
            value.put("coverAssetId", coverAssetId);
            value.put("documentVersion", documentVersion);
            value.put("saved", saved);
            return value;
        }
    }

    @ToolInfo(name = "read_article_draft", description = "读取本次定时创作任务当前的文章草稿。需要检查或继续修改已保存草稿时使用。")
    public static class ReadDraftTool implements Tool<ReadDraftParam> {
        private final DraftState state;
        public ReadDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(ReadDraftParam param) { return state.read(); }
    }

    @Data
    public static class ReadDraftParam extends ToolParam {
        @Param(required = false, description = "读取草稿的原因") private String reason;
    }

    @ToolInfo(name = "save_article_draft", description = "把完整文章保存到本次任务工作区。研究和整理完成后必须调用；再次调用会原子覆盖上一版草稿。正文必须使用系统提示中的公众号视觉模板生成完整内联样式HTML，以01、02等居中章节号、绿色短横线、居中章节标题和自然段组织内容，禁止使用ul、ol、dl或table；可引用素材工具返回的publicUrl插入图片。")
    public static class SaveDraftTool implements Tool<SaveDraftParam> {
        private final DraftState state;
        public SaveDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(SaveDraftParam param) { return state.save(param); }
    }

    @Data
    public static class SaveDraftParam extends ToolParam {
        @Param(description = "完整文章标题，最多64字") private String title;
        @Param(required = false, description = "文章作者") private String author;
        @Param(required = false, description = "文章摘要，最多120字") private String digest;
        @Param(description = "完整文章正文HTML；严格使用系统提示中的公众号视觉模板及内联样式，使用居中章节号、章节标题和p自然段组织行文，不得包含项目符号列表、编号列表、定义列表或表格") private String contentHtml;
        @Param(required = false, description = "最主要的参考来源URL；多个来源应在正文末尾列出") private String sourceUrl;
    }

    @ToolInfo(name = "set_article_draft_cover", description = "把素材库图片设置为本次定时创作文章的封面。assetId必须来自默认封面、素材库检索、网络图片导入或图片生成/编辑工具。")
    public static class SetDraftCoverTool implements Tool<SetDraftCoverParam> {
        private final DraftState state;
        public SetDraftCoverTool(DraftState state) { this.state = state; }
        @Override public String execute(SetDraftCoverParam param) { return state.setCover(param); }
    }

    @Data
    public static class SetDraftCoverParam extends ToolParam {
        @Param(description = "封面素材assetId") private Long assetId;
    }

    public record Draft(String title, String author, String digest, String contentHtml,
                        String sourceUrl, Long coverAssetId, long documentVersion) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("定时文章工具结果序列化失败", error);
        }
    }
}

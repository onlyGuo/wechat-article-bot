package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.article.ArticleContentPolicy;
import lombok.Data;

import java.util.List;

/**
 * 微信文章编辑智能体的最小权限工具集。工具本身只提供 Schema，实际执行由浏览器编辑会话完成。
 */
public final class ArticleEditorTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ArticleEditorTools() {
    }

    public static List<Tool> all(BrowserExecutor executor) {
        return List.of(new ReadArticleTool(executor), new ReadBlocksTool(executor),
                new DeleteBlocksTool(executor), new InsertBlocksTool(executor),
                new ReplaceBlocksTool(executor), new UpdateMetadataTool(executor),
                new UpdateCoverTool(executor));
    }

    @FunctionalInterface
    public interface BrowserExecutor {
        String execute(String toolName, String paramJson) throws Exception;
    }

    @ToolInfo(name = "read_article", description = "读取浏览器中当前文章的标题、摘要、正文逻辑块、文档版本。编辑前必须先调用此工具。")
    public static class ReadArticleTool implements Tool<ReadArticleParam> {
        private final BrowserExecutor executor;
        public ReadArticleTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(ReadArticleParam param) {
            return executeInBrowser(executor, "read_article", param);
        }
    }

    @Data
    public static class ReadArticleParam extends ToolParam {
        @Param(required = false, description = "读取原因，简短说明本次准备检查什么")
        private String reason;
    }

    @ToolInfo(name = "read_blocks", description = "读取当前文章指定逻辑行范围。逻辑行是标题、段落、列表等顶层内容块，不是屏幕换行。")
    public static class ReadBlocksTool implements Tool<ReadBlocksParam> {
        private final BrowserExecutor executor;
        public ReadBlocksTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(ReadBlocksParam param) {
            return executeInBrowser(executor, "read_blocks", param);
        }
    }

    @Data
    public static class ReadBlocksParam extends ToolParam {
        @Param(description = "开始逻辑行，从1开始")
        private Integer startLine;
        @Param(description = "结束逻辑行，包含该行")
        private Integer endLine;
    }

    @ToolInfo(name = "delete_blocks", description = "删除当前文章中连续的逻辑行。必须使用最近一次读取返回的 documentVersion。")
    public static class DeleteBlocksTool implements Tool<DeleteBlocksParam> {
        private final BrowserExecutor executor;
        public DeleteBlocksTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(DeleteBlocksParam param) {
            return executeInBrowser(executor, "delete_blocks", param);
        }
    }

    @Data
    public static class DeleteBlocksParam extends ToolParam {
        @Param(description = "开始逻辑行，从1开始")
        private Integer startLine;
        @Param(description = "结束逻辑行，包含该行")
        private Integer endLine;
        @Param(description = "最近一次读取工具返回的文档版本")
        private Long expectedDocumentVersion;
    }

    @ToolInfo(name = "insert_blocks", description = "在指定逻辑行之前或之后插入一个或多个完整HTML内容块。blocks中的每项必须是完整的p、h2、h3、blockquote、hr或figure元素。创作正文时禁止使用列表和表格。")
    public static class InsertBlocksTool implements Tool<InsertBlocksParam> {
        private final BrowserExecutor executor;
        public InsertBlocksTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(InsertBlocksParam param) {
            requireParagraphProse(param.getBlocks());
            return executeInBrowser(executor, "insert_blocks", param);
        }
    }

    @Data
    public static class InsertBlocksParam extends ToolParam {
        @Param(description = "锚点逻辑行；空文章时填写1")
        private Integer line;
        @Param(description = "插入位置", enums = {"BEFORE", "AFTER"})
        private String position;
        @Param(description = "依次插入的完整HTML内容块")
        private String[] blocks;
        @Param(description = "最近一次读取工具返回的文档版本")
        private Long expectedDocumentVersion;
    }

    @ToolInfo(name = "replace_blocks", description = "原子替换连续逻辑行，适合改写已有段落。blocks中的每项必须是完整HTML内容块，创作正文时禁止使用列表和表格。")
    public static class ReplaceBlocksTool implements Tool<ReplaceBlocksParam> {
        private final BrowserExecutor executor;
        public ReplaceBlocksTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(ReplaceBlocksParam param) {
            requireParagraphProse(param.getBlocks());
            return executeInBrowser(executor, "replace_blocks", param);
        }
    }

    @Data
    public static class ReplaceBlocksParam extends ToolParam {
        @Param(description = "开始逻辑行，从1开始")
        private Integer startLine;
        @Param(description = "结束逻辑行，包含该行")
        private Integer endLine;
        @Param(description = "依次替换为这些完整HTML内容块")
        private String[] blocks;
        @Param(description = "最近一次读取工具返回的文档版本")
        private Long expectedDocumentVersion;
    }

    @ToolInfo(name = "update_metadata", description = "修改文章标题或摘要。未提供的字段保持不变，必须使用最近一次读取返回的documentVersion。")
    public static class UpdateMetadataTool implements Tool<UpdateMetadataParam> {
        private final BrowserExecutor executor;
        public UpdateMetadataTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(UpdateMetadataParam param) {
            return executeInBrowser(executor, "update_metadata", param);
        }
    }

    @Data
    public static class UpdateMetadataParam extends ToolParam {
        @Param(required = false, description = "完整的新标题，最多64字")
        private String title;
        @Param(required = false, description = "完整的新摘要，最多120字")
        private String digest;
        @Param(description = "最近一次读取工具返回的文档版本")
        private Long expectedDocumentVersion;
    }

    @ToolInfo(name = "update_cover", description = "把素材库中的图片设置为当前文章封面。assetId必须来自用户上传、素材库检索、网络图片导入或图片生成/编辑工具的成功结果。")
    public static class UpdateCoverTool implements Tool<UpdateCoverParam> {
        private final BrowserExecutor executor;
        public UpdateCoverTool(BrowserExecutor executor) { this.executor = executor; }
        @Override
        public String execute(UpdateCoverParam param) {
            return executeInBrowser(executor, "update_cover", param);
        }
    }

    @Data
    public static class UpdateCoverParam extends ToolParam {
        @Param(description = "要设为封面的素材库图片assetId")
        private Long assetId;
        @Param(description = "最近一次读取工具返回的文档版本")
        private Long expectedDocumentVersion;
    }

    private static String executeInBrowser(BrowserExecutor executor, String toolName, ToolParam param) {
        try {
            return executor.execute(toolName, MAPPER.writeValueAsString(param));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("工具参数序列化失败", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(error.getMessage(), error);
        }
    }

    private static void requireParagraphProse(String[] blocks) {
        if (blocks == null) return;
        ArticleContentPolicy.requireParagraphProse(String.join("", blocks));
    }
}

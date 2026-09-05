package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ArticleMediaTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SafeWebService webService;
    private final AssetService assetService;
    private final ImageGenerationService imageService;

    public ArticleMediaTools(SafeWebService webService, AssetService assetService,
                             ImageGenerationService imageService) {
        this.webService = webService;
        this.assetService = assetService;
        this.imageService = imageService;
    }

    public List<Tool> create(Long accountId, Long userId) {
        return create(accountId, userId, (toolName, paramJson, action) -> action.get());
    }

    public List<Tool> create(Long accountId, Long userId, MutationExecutor mutationExecutor) {
        return List.of(new SearchWebTool(), new BrowseWebpageTool(), new SearchWebImagesTool(),
                new ListAssetsTool(accountId), new ImportWebImageTool(accountId, userId, mutationExecutor),
                new GenerateImageTool(accountId, userId, mutationExecutor),
                new EditImageTool(accountId, userId, mutationExecutor));
    }

    @FunctionalInterface
    public interface MutationExecutor {
        String execute(String toolName, String paramJson, Supplier<String> action);
    }

    @ToolInfo(name = "search_web", description = "搜索公开网页，返回标题、链接和摘要。需要事实资料或外部来源时使用。")
    public class SearchWebTool implements Tool<SearchWebParam> {
        @Override public String execute(SearchWebParam param) {
            return json(webService.searchWeb(param.getQuery(), value(param.getMaxResults(), 5)));
        }
    }

    @Data
    public static class SearchWebParam extends ToolParam {
        @Param(description = "搜索关键词") private String query;
        @Param(required = false, description = "结果数量，1到10") private Integer maxResults;
    }

    @ToolInfo(name = "browse_webpage", description = "打开一个公开网页并提取标题和正文。必须先有明确URL，禁止访问内网。")
    public class BrowseWebpageTool implements Tool<BrowseWebpageParam> {
        @Override public String execute(BrowseWebpageParam param) { return json(webService.browse(param.getUrl())); }
    }

    @Data
    public static class BrowseWebpageParam extends ToolParam {
        @Param(description = "要阅读的公开 HTTP/HTTPS 网页地址") private String url;
    }

    @ToolInfo(name = "search_web_images", description = "搜索与文章主题相关的网络图片候选，只返回图片和来源页地址；使用前应确认内容相关并通过import_web_image导入素材库。")
    public class SearchWebImagesTool implements Tool<SearchWebImagesParam> {
        @Override public String execute(SearchWebImagesParam param) {
            return json(webService.searchImages(param.getQuery(), value(param.getMaxResults(), 6)));
        }
    }

    @Data
    public static class SearchWebImagesParam extends ToolParam {
        @Param(description = "图片搜索关键词，应包含主题和期望视觉风格") private String query;
        @Param(required = false, description = "结果数量，1到10") private Integer maxResults;
    }

    @ToolInfo(name = "list_image_assets", description = "检索文章素材库中的图片。优先复用用户提供或已有的合适素材；结果包含assetId和publicUrl。")
    public class ListAssetsTool implements Tool<ListAssetsParam> {
        private final Long accountId;
        public ListAssetsTool(Long accountId) { this.accountId = accountId; }
        @Override public String execute(ListAssetsParam param) {
            return json(assetService.search(accountId, param.getKeyword(), value(param.getMaxResults(), 10))
                    .stream().map(ArticleMediaTools::assetView).toList());
        }
    }

    @Data
    public static class ListAssetsParam extends ToolParam {
        @Param(required = false, description = "文件名或图片描述关键词，留空返回最近素材") private String keyword;
        @Param(required = false, description = "结果数量，1到20") private Integer maxResults;
    }

    @ToolInfo(name = "import_web_image", description = "把已选定的公网图片下载并保存到素材库。必须保留来源页面URL；成功后使用返回的publicUrl通过insert_blocks插入文章。")
    public class ImportWebImageTool implements Tool<ImportWebImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        public ImportWebImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
        }
        @Override public String execute(ImportWebImageParam param) {
            return mutationExecutor.execute("import_web_image", json(param), () -> {
                SafeWebService.BinaryResponse image = webService.downloadImage(param.getImageUrl());
                Asset asset = assetService.saveImage(accountId, param.getFilename(), image.contentType(), image.bytes(),
                        "WEB_IMPORT", param.getSourcePageUrl(), param.getDescription(), userId);
                return json(assetView(asset));
            });
        }
    }

    @Data
    public static class ImportWebImageParam extends ToolParam {
        @Param(description = "图片原始公网URL") private String imageUrl;
        @Param(description = "图片所在的来源页面URL，用于溯源") private String sourcePageUrl;
        @Param(required = false, description = "保存文件名") private String filename;
        @Param(description = "图片内容和适用位置的简短描述") private String description;
    }

    @ToolInfo(name = "generate_image", description = "使用后台配置的图片模型创作配图并保存到素材库。提示词应描述主体、构图、风格、比例且避免在图中生成文字；成功后使用publicUrl通过insert_blocks插入文章。")
    public class GenerateImageTool implements Tool<GenerateImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        public GenerateImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
        }
        @Override public String execute(GenerateImageParam param) {
            return mutationExecutor.execute("generate_image", json(param), () ->
                    json(assetView(imageService.generate(accountId, param.getPrompt(), param.getFilename(), userId))));
        }
    }

    @Data
    public static class GenerateImageParam extends ToolParam {
        @Param(description = "详细的图片创作提示词") private String prompt;
        @Param(required = false, description = "保存到素材库的文件名") private String filename;
    }

    @ToolInfo(name = "edit_image", description = "使用图片模型编辑素材库中的图片并另存为新素材，不覆盖原图；成功后使用publicUrl通过insert_blocks插入文章。")
    public class EditImageTool implements Tool<EditImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        public EditImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
        }
        @Override public String execute(EditImageParam param) {
            return mutationExecutor.execute("edit_image", json(param), () ->
                    json(assetView(imageService.edit(accountId, param.getAssetId(), param.getPrompt(),
                            param.getFilename(), userId))));
        }
    }

    @Data
    public static class EditImageParam extends ToolParam {
        @Param(description = "要编辑的素材assetId") private Long assetId;
        @Param(description = "需要对图片进行的具体修改，说明要保留和改变的内容") private String prompt;
        @Param(required = false, description = "新图片保存文件名") private String filename;
    }

    private static Map<String, Object> assetView(Asset asset) {
        return Map.ofEntries(
                Map.entry("assetId", asset.getId()),
                Map.entry("publicUrl", asset.getPublicUrl()),
                Map.entry("filename", asset.getOriginalName()),
                Map.entry("contentType", asset.getContentType()),
                Map.entry("sourceType", asset.getSourceType() == null ? "UNKNOWN" : asset.getSourceType()),
                Map.entry("description", asset.getDescription() == null ? "" : asset.getDescription()));
    }

    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }

    private static String json(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("工具结果序列化失败", exception); }
    }
}

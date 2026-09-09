package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.skill.ArticleSkillService;
import ink.icoding.wechat.article.common.PageResult;
import ink.icoding.wechat.article.wechat.WechatClient;
import jakarta.validation.constraints.NotBlank;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ArticleService {
    private static final Pattern LOCAL_ASSET_NAME = Pattern.compile(
            "(?i)[a-f0-9]{32}\\.(?:jpg|png|gif|webp)");
    private final ArticleMapper mapper;
    private final ArticleRevisionMapper revisionMapper;
    private final CurrentUserService currentUserService;
    private final AssetService assetService;
    private final WechatAccountService accountService;
    private final WechatClient wechatClient;
    private final ArticleSkillService skillService;

    public ArticleService(ArticleMapper mapper, ArticleRevisionMapper revisionMapper,
                          CurrentUserService currentUserService, AssetService assetService,
                          WechatAccountService accountService, WechatClient wechatClient,
                          ArticleSkillService skillService) {
        this.mapper = mapper;
        this.revisionMapper = revisionMapper;
        this.currentUserService = currentUserService;
        this.assetService = assetService;
        this.accountService = accountService;
        this.wechatClient = wechatClient;
        this.skillService = skillService;
    }

    public PageResult<Article> list(Long accountId, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        return new PageResult<>(mapper.findPage(accountId, status, keyword, (safePage - 1) * safeSize, safeSize),
                mapper.countPage(accountId, status, keyword), safePage, safeSize);
    }

    public Article required(Long id) {
        Article article = mapper.findById(id);
        if (article == null) throw new BusinessException(HttpStatus.NOT_FOUND, "文章不存在");
        return article;
    }

    @Transactional
    public Article create(ArticleRequest request, String sourceType) {
        return createWithUser(request, sourceType, currentUserService.required().id());
    }

    @Transactional
    public Article createForTask(ArticleRequest request, Long userId) {
        return createWithUser(request, "SCHEDULED", userId);
    }

    private Article createWithUser(ArticleRequest request, String sourceType, Long userId) {
        if (!"SCHEDULED".equals(sourceType)) skillService.validateSelection(request.skillId());
        Article article = new Article();
        article.setAccountId(request.accountId());
        article.setSkillId(request.skillId());
        article.setTitle(request.title() == null || request.title().isBlank() ? "未命名文章" : request.title());
        article.setAuthor(request.author());
        article.setDigest(request.digest());
        article.setContentHtml(clean(request.contentHtml()));
        article.setContentText(Jsoup.parse(article.getContentHtml()).text());
        article.setCoverAssetId(request.coverAssetId());
        article.setCoverUrl(resolveCover(request.coverAssetId(), request.coverUrl()));
        article.setSourceUrl(request.sourceUrl());
        article.setSourceType(sourceType == null ? "MANUAL" : sourceType);
        article.setBusinessStatus("DRAFT");
        article.setWorkflowStatus("EDITING");
        article.setWechatStatus("NOT_SYNCED");
        article.setRevision(1);
        article.setDeleted(false);
        article.setCreatedBy(userId);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        mapper.insert(article);
        snapshot(article, "MANUAL", "创建文章", article.getCreatedBy());
        return required(article.getId());
    }

    public boolean existsBySourceUrl(String sourceUrl) {
        return sourceUrl != null && !sourceUrl.isBlank() && mapper.countBySourceUrl(sourceUrl) > 0;
    }

    @Transactional
    public Article update(Long id, ArticleRequest request, String changeSource, String summary) {
        return updateWithUser(id, request, changeSource, summary, currentUserService.required().id());
    }

    @Transactional
    public Article updateByAi(Long id, ArticleRequest request, String summary, Long userId) {
        return updateWithUser(id, request, "AI", summary, userId);
    }

    private Article updateWithUser(Long id, ArticleRequest request, String changeSource, String summary, Long userId) {
        Article existing = required(id);
        if (!Objects.equals(existing.getSkillId(), request.skillId())) skillService.validateSelection(request.skillId());
        if (request.revision() == null) throw new BusinessException("缺少文章版本号");
        Article article = new Article();
        article.setId(id);
        article.setAccountId(request.accountId());
        article.setSkillId(request.skillId());
        article.setTitle(request.title() == null || request.title().isBlank() ? "未命名文章" : request.title());
        article.setAuthor(request.author());
        article.setDigest(request.digest());
        article.setContentHtml(clean(request.contentHtml()));
        article.setContentText(Jsoup.parse(article.getContentHtml()).text());
        article.setCoverAssetId(request.coverAssetId());
        article.setCoverUrl(resolveCover(request.coverAssetId(), request.coverUrl()));
        article.setSourceUrl(request.sourceUrl());
        if (sameEditableContent(existing, article)) {
            return existing;
        }
        if (mapper.updateContent(article, request.revision()) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "文章已被其他操作修改，请刷新后重试");
        }
        Article updated = required(id);
        snapshot(updated, changeSource == null ? "MANUAL" : changeSource,
                summary == null ? "编辑文章" : summary, userId);
        return updated;
    }

    public List<ArticleRevision> revisions(Long id) {
        required(id);
        return revisionMapper.findRevisions(id);
    }

    @Transactional
    public Article rollback(Long id, Integer revision) {
        Article current = required(id);
        ArticleRevision target = revisionMapper.findRevision(id, revision);
        if (target == null) throw new BusinessException("指定版本不存在");
        ArticleRequest request = new ArticleRequest(current.getAccountId(), target.getTitle(), current.getAuthor(),
                target.getDigest(), target.getContentHtml(), current.getCoverAssetId(), current.getCoverUrl(),
                current.getSourceUrl(), current.getRevision(), current.getSkillId());
        return update(id, request, "ROLLBACK", "回滚到版本 " + revision);
    }

    /*
     * Keep WeChat material uploads outside a database transaction. A remote material upload cannot
     * be rolled back; retaining its media id prevents a later draft failure from creating duplicate
     * permanent cover materials on retry.
     */
    public Article syncDraft(Long id) {
        return syncDraft(id, progress -> {});
    }

    public Article syncDraft(Long id, WechatProgressListener progress) {
        progress.onProgress(new WechatProgress("VALIDATING", "正在检查文章和公众号配置", 5));
        Article article = required(id);
        if (article.getAccountId() == null) throw new BusinessException("请先选择目标公众号");
        if (article.getCoverAssetId() == null) throw new BusinessException("同步微信草稿前必须设置封面图片");
        progress.onProgress(new WechatProgress("COVER", "正在准备微信封面素材", 15));
        String thumbMediaId = assetService.ensureWechatThumb(article.getCoverAssetId(), article.getAccountId());
        progress.onProgress(new WechatProgress("CONTENT", "正在处理正文图片和微信排版", 35));
        String wechatContent = prepareWechatContent(article, progress);
        Map<String, Object> payload = wechatPayload(article, thumbMediaId, wechatContent);
        if (article.getWechatMediaId() == null || article.getWechatMediaId().isBlank()) {
            progress.onProgress(new WechatProgress("DRAFT", "正在创建微信草稿", 72));
            String mediaId = wechatClient.addDraft(article.getAccountId(), payload);
            mapper.markWechatDraft(id, mediaId);
        } else {
            progress.onProgress(new WechatProgress("DRAFT", "正在更新微信草稿", 72));
            wechatClient.updateDraft(article.getAccountId(), article.getWechatMediaId(), payload);
            mapper.markWechatDraft(id, article.getWechatMediaId());
        }
        progress.onProgress(new WechatProgress("SAVING", "微信已响应，正在保存同步状态", 94));
        Article updated = required(id);
        progress.onProgress(new WechatProgress("DONE", "文章已同步到微信草稿箱", 100));
        return updated;
    }

    /*
     * Do not wrap draft creation and publish submission in one database transaction. They are two
     * irreversible remote operations: if publishing is unauthorized or fails, the successfully
     * created WeChat draft and its media id must remain persisted for manual publishing/retry.
     */
    public Article publish(Long id) {
        return publish(id, progress -> {});
    }

    public Article publish(Long id, WechatProgressListener progress) {
        progress.onProgress(new WechatProgress("VALIDATING", "正在检查文章发布状态", 5));
        Article article = required(id);
        if (article.getAccountId() == null) throw new BusinessException("请先选择目标公众号");
        if (article.getWechatMediaId() == null || article.getWechatMediaId().isBlank()) {
            progress.onProgress(new WechatProgress("DRAFT", "尚无微信草稿，将先完成草稿同步", 10));
            article = syncDraft(id, step -> progress.onProgress(new WechatProgress(
                    step.stage(), step.message(), Math.min(70, Math.max(10, step.percent() * 7 / 10)))));
        } else {
            progress.onProgress(new WechatProgress("DRAFT", "已找到同步的微信草稿", 65));
        }
        progress.onProgress(new WechatProgress("PUBLISH", "正在向微信提交发布任务", 80));
        String publishId = wechatClient.publish(article.getAccountId(), article.getWechatMediaId());
        progress.onProgress(new WechatProgress("SAVING", "微信已受理，正在保存发布状态", 95));
        mapper.markPublishing(id, publishId);
        Article updated = required(id);
        progress.onProgress(new WechatProgress("DONE", "发布任务已提交给微信", 100));
        return updated;
    }

    @Transactional
    public PublishStatus refreshPublishStatus(Long id) {
        Article article = required(id);
        if (article.getWechatPublishId() == null) throw new BusinessException("文章没有进行中的发布任务");
        Map<String, Object> response = wechatClient.publishStatus(article.getAccountId(), article.getWechatPublishId());
        int status = ((Number) response.getOrDefault("publish_status", 1)).intValue();
        String articleId = response.get("article_id") == null ? null : String.valueOf(response.get("article_id"));
        if (status == 0) {
            mapper.updatePublishResult(id, "PUBLISHED", "READY", "PUBLISHED", articleId, LocalDateTime.now());
        } else if (status >= 2) {
            mapper.updatePublishResult(id, "FAILED", "FAILED", "DRAFT", null, null);
        }
        return new PublishStatus(status, publishMessage(status), required(id));
    }

    public void delete(Long id) {
        required(id);
        mapper.softDelete(id);
    }

    private Map<String, Object> wechatPayload(Article article, String thumbMediaId, String wechatContent) {
        WechatAccount account = accountService.required(article.getAccountId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", article.getTitle());
        payload.put("author", blankToDefault(article.getAuthor(), account.getDefaultAuthor()));
        payload.put("digest", article.getDigest() == null ? "" : article.getDigest());
        payload.put("content", wechatContent);
        payload.put("content_source_url", article.getSourceUrl() == null ? "" : article.getSourceUrl());
        payload.put("thumb_media_id", thumbMediaId);
        payload.put("need_open_comment", 0);
        payload.put("only_fans_can_comment", 0);
        return payload;
    }

    String prepareWechatContent(Article article) {
        return prepareWechatContent(article, progress -> {});
    }

    private String prepareWechatContent(Article article, WechatProgressListener progress) {
        Document document = Jsoup.parseBodyFragment(article.getContentHtml() == null ? "" : article.getContentHtml());
        document.outputSettings().prettyPrint(false);
        Map<String, String> uploadedUrls = new HashMap<>();
        List<Element> localImages = document.select("img[src]").stream()
                .filter(image -> localStorageName(image.attr("src")) != null).toList();
        for (int index = 0; index < localImages.size(); index++) {
            Element image = localImages.get(index);
            String storageName = localStorageName(image.attr("src"));
            String wechatUrl = uploadedUrls.computeIfAbsent(storageName,
                    name -> assetService.ensureWechatContentImage(name, article.getAccountId()));
            image.attr("src", wechatUrl);
            int completed = index + 1;
            progress.onProgress(new WechatProgress("CONTENT",
                    "正在处理正文图片 " + completed + "/" + localImages.size(),
                    35 + completed * 30 / localImages.size()));
        }
        if (localImages.isEmpty()) {
            progress.onProgress(new WechatProgress("CONTENT", "正文排版已准备完成", 65));
        }
        return ArticleContentPolicy.formatForWechat(document.body().html());
    }

    private String localStorageName(String source) {
        try {
            String path = URI.create(source).getPath();
            if (path == null || !path.startsWith("/uploads/")) return null;
            String storageName = path.substring("/uploads/".length());
            return LOCAL_ASSET_NAME.matcher(storageName).matches() ? storageName : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void snapshot(Article article, String source, String summary, Long userId) {
        ArticleRevision revision = new ArticleRevision();
        revision.setArticleId(article.getId());
        revision.setRevision(article.getRevision());
        revision.setTitle(article.getTitle());
        revision.setDigest(article.getDigest());
        revision.setContentHtml(article.getContentHtml());
        revision.setChangeSource(source);
        revision.setChangeSummary(summary);
        revision.setCreatedBy(userId);
        revision.setCreatedAt(LocalDateTime.now());
        revisionMapper.insert(revision);
    }

    private boolean sameEditableContent(Article left, Article right) {
        return Objects.equals(left.getAccountId(), right.getAccountId())
                && Objects.equals(left.getSkillId(), right.getSkillId())
                && Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getAuthor(), right.getAuthor())
                && Objects.equals(left.getDigest(), right.getDigest())
                && Objects.equals(left.getContentHtml(), right.getContentHtml())
                && Objects.equals(left.getCoverAssetId(), right.getCoverAssetId())
                && Objects.equals(left.getCoverUrl(), right.getCoverUrl())
                && Objects.equals(left.getSourceUrl(), right.getSourceUrl());
    }

    private String resolveCover(Long assetId, String coverUrl) {
        if (assetId == null) return coverUrl;
        Asset asset = assetService.required(assetId);
        return asset.getPublicUrl();
    }

    private String clean(String html) {
        return ArticleContentPolicy.sanitize(html);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private String publishMessage(int status) {
        return switch (status) {
            case 0 -> "发布成功";
            case 1 -> "发布中";
            case 2 -> "原创校验失败";
            case 3 -> "发布失败";
            case 4 -> "平台审核未通过";
            case 5 -> "发布成功后被用户删除";
            case 6 -> "发布成功后被平台封禁";
            default -> "未知状态 " + status;
        };
    }

    public record ArticleRequest(Long accountId, String title, String author, String digest, String contentHtml,
                                 Long coverAssetId, String coverUrl, String sourceUrl, Integer revision, Long skillId) {}
    public record PublishStatus(int code, String message, Article article) {}
    public record WechatProgress(String stage, String message, int percent) {}

    @FunctionalInterface
    public interface WechatProgressListener {
        void onProgress(WechatProgress progress);
    }
}

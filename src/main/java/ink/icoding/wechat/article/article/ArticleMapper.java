package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleMapper extends SmartMapper<Article> {
    default List<Article> findPage(Long accountId, String status, String keyword, int offset, int pageSize) {
        Where where = articleWhere(accountId, status, keyword)
                .orderBy(Article::getUpdatedAt).desc()
                .limit(Math.max(0, offset), Math.max(1, pageSize));
        return selectWithRelations(where);
    }

    default long countPage(Long accountId, String status, String keyword) {
        return count(articleWhere(accountId, status, keyword));
    }

    default Article findById(Long id) {
        List<Article> articles = selectWithRelations(Where.where(Article::getId).eq(id)
                .and(Article::getDeleted).eq(false).limit(1));
        return articles.isEmpty() ? null : articles.get(0);
    }

    default int updateContent(Article changes, int expectedRevision) {
        Article article = selectById(changes.getId());
        if (article == null || Boolean.TRUE.equals(article.getDeleted())
                || article.getRevision() == null || article.getRevision() != expectedRevision) return 0;
        article.setAccountId(changes.getAccountId());
        article.setSkillId(changes.getSkillId());
        article.setTitle(changes.getTitle());
        article.setAuthor(changes.getAuthor());
        article.setDigest(changes.getDigest());
        article.setContentHtml(changes.getContentHtml());
        article.setContentText(changes.getContentText());
        article.setCoverUrl(changes.getCoverUrl());
        article.setCoverAssetId(changes.getCoverAssetId());
        article.setSourceUrl(changes.getSourceUrl());
        article.setWorkflowStatus("EDITING");
        article.setRevision(expectedRevision + 1);
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }

    default int markWechatDraft(Long id, String mediaId) {
        Article article = selectById(id);
        if (article == null) return 0;
        article.setWechatMediaId(mediaId);
        article.setWechatStatus("WECHAT_DRAFT");
        article.setWorkflowStatus("READY");
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }

    default int markPublishing(Long id, String publishId) {
        Article article = selectById(id);
        if (article == null) return 0;
        article.setWechatPublishId(publishId);
        article.setWechatStatus("PUBLISHING");
        article.setWorkflowStatus("PUBLISHING");
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }

    default int updatePublishResult(Long id, String wechatStatus, String workflowStatus, String businessStatus,
                                    String articleId, LocalDateTime publishedAt) {
        Article article = selectById(id);
        if (article == null) return 0;
        article.setWechatStatus(wechatStatus);
        article.setWorkflowStatus(workflowStatus);
        article.setBusinessStatus(businessStatus);
        article.setWechatArticleId(articleId);
        article.setPublishedAt(publishedAt);
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }

    default int softDelete(Long id) {
        Article article = selectById(id);
        if (article == null) return 0;
        article.setDeleted(true);
        article.setUpdatedAt(LocalDateTime.now());
        return updateById(article);
    }

    default long countDrafts() {
        return count(activeWhere().and(Article::getBusinessStatus).eq("DRAFT"));
    }

    default long countPublished() {
        return count(activeWhere().and(Article::getBusinessStatus).eq("PUBLISHED"));
    }

    default long countFailed() {
        return count(activeWhere().and(Article::getWorkflowStatus).eq("FAILED"));
    }

    default long countBySourceUrl(String sourceUrl) {
        return count(activeWhere().and(Article::getSourceUrl).eq(sourceUrl));
    }

    private static Where activeWhere() {
        return Where.where(Article::getDeleted).eq(false);
    }

    private static Where articleWhere(Long accountId, String status, String keyword) {
        Where where = activeWhere()
                .ifAnd(Article::getAccountId).eq(accountId)
                .ifAnd(Article::getBusinessStatus).eq(status);
        if (keyword != null && !keyword.isBlank()) {
            where.and(Where.or(
                    Where.where(Article::getTitle).like(keyword),
                    Where.where(Article::getContentText).like(keyword)));
        }
        return where;
    }
}

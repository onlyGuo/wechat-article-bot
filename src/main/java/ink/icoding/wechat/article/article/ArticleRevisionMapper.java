package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ArticleRevisionMapper extends SmartMapper<ArticleRevision> {
    default List<ArticleRevision> findRevisions(Long articleId) {
        return select(Where.where(ArticleRevision::getArticleId).eq(articleId)
                .orderBy(ArticleRevision::getRevision).desc());
    }

    default ArticleRevision findRevision(Long articleId, Integer revision) {
        return selectFirst(Where.where(ArticleRevision::getArticleId).eq(articleId)
                .and(ArticleRevision::getRevision).eq(revision));
    }
}

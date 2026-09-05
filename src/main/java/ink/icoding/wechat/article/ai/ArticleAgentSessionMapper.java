package ink.icoding.wechat.article.ai;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleAgentSessionMapper extends SmartMapper<ArticleAgentSession> {
    default ArticleAgentSession findByArticleId(Long articleId) {
        return selectFirst(Where.where(ArticleAgentSession::getArticleId).eq(articleId)
                .orderBy(ArticleAgentSession::getId).asc());
    }
}

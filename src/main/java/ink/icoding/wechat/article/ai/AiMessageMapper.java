package ink.icoding.wechat.article.ai;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiMessageMapper extends SmartMapper<AiMessage> {
    default List<AiMessage> findByArticleId(Long articleId) {
        return select(Where.where(AiMessage::getArticleId).eq(articleId)
                .orderBy(AiMessage::getCreatedAt).asc()
                .orderBy(AiMessage::getId).asc());
    }

    default List<AiMessage> findRecent(Long articleId, int limit) {
        return select(Where.where(AiMessage::getArticleId).eq(articleId)
                .orderBy(AiMessage::getCreatedAt).desc()
                .orderBy(AiMessage::getId).desc()
                .limit(Math.max(1, limit)));
    }

    default long totalTokens() {
        return selectAll().stream().mapToLong(message ->
                value(message.getPromptTokens()) + value(message.getCompletionTokens())).sum();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}

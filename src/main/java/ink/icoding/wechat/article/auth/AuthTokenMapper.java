package ink.icoding.wechat.article.auth;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import ink.icoding.wechat.article.auth.model.AuthToken;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface AuthTokenMapper extends SmartMapper<AuthToken> {
    default AuthToken findValid(String tokenHash) {
        return selectFirst(Where.where(AuthToken::getTokenHash).eq(tokenHash)
                .and(AuthToken::getExpiresAt).gt(LocalDateTime.now()));
    }

    default int delete(String tokenHash) {
        return delete(Where.where(AuthToken::getTokenHash).eq(tokenHash));
    }

    default int deleteExpired() {
        return delete(Where.where(AuthToken::getExpiresAt).lte(LocalDateTime.now()));
    }

    default int deleteByUserId(Long userId) {
        return delete(Where.where(AuthToken::getUserId).eq(userId));
    }
}

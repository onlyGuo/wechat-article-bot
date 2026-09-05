package ink.icoding.wechat.article.follower;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WechatFollowerMapper extends SmartMapper<WechatFollower> {
    default List<WechatFollower> findPage(Long accountId, String keyword, int offset, int pageSize) {
        Where where = followerWhere(accountId, keyword)
                .orderBy(WechatFollower::getUpdatedAt).desc()
                .limit(Math.max(0, offset), Math.max(1, pageSize));
        return selectWithRelations(where);
    }

    default long countPage(Long accountId, String keyword) {
        return count(followerWhere(accountId, keyword));
    }

    default long countSubscribed() {
        return count(Where.where(WechatFollower::getSubscribed).eq(true));
    }

    default int upsert(WechatFollower incoming) {
        WechatFollower follower = selectFirst(Where.where(WechatFollower::getAccountId).eq(incoming.getAccountId())
                .and(WechatFollower::getOpenId).eq(incoming.getOpenId()));
        incoming.setUpdatedAt(LocalDateTime.now());
        if (follower == null) return insert(incoming);
        incoming.setId(follower.getId());
        return updateById(incoming);
    }

    private static Where followerWhere(Long accountId, String keyword) {
        Where where = Where.where().ifAnd(WechatFollower::getAccountId).eq(accountId);
        if (keyword != null && !keyword.isBlank()) {
            where.and(Where.or(
                    Where.where(WechatFollower::getNickname).like(keyword),
                    Where.where(WechatFollower::getOpenId).like(keyword),
                    Where.where(WechatFollower::getRemark).like(keyword)));
        }
        return where;
    }
}

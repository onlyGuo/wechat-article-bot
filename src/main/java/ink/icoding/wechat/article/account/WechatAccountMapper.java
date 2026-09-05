package ink.icoding.wechat.article.account;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WechatAccountMapper extends SmartMapper<WechatAccount> {
    default List<WechatAccount> findAll() {
        return select(Where.where().orderBy(WechatAccount::getCreatedAt).desc());
    }

    default WechatAccount findById(Long id) {
        return selectById(id);
    }

    default long count() {
        return count(Where.where());
    }

    default int update(WechatAccount account) {
        account.setUpdatedAt(LocalDateTime.now());
        return updateById(account);
    }

    default int updateToken(Long id, String tokenEncrypted, LocalDateTime tokenExpiresAt, String connectionStatus) {
        WechatAccount account = selectById(id);
        if (account == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        account.setTokenEncrypted(tokenEncrypted);
        account.setTokenExpiresAt(tokenExpiresAt);
        account.setConnectionStatus(connectionStatus);
        account.setLastCheckedAt(now);
        account.setUpdatedAt(now);
        return updateById(account);
    }

    default int updateConnection(Long id, String status) {
        WechatAccount account = selectById(id);
        if (account == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        account.setConnectionStatus(status);
        account.setLastCheckedAt(now);
        account.setUpdatedAt(now);
        return updateById(account);
    }

    default int delete(Long id) {
        return deleteById(id);
    }
}

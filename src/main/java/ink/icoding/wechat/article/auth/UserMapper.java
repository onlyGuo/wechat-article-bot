package ink.icoding.wechat.article.auth;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import ink.icoding.wechat.article.auth.model.SystemUser;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper extends SmartMapper<SystemUser> {
    default SystemUser findByUsername(String username) {
        return selectFirst(Where.where(SystemUser::getUsername).eq(username));
    }

    default SystemUser findById(Long id) {
        return selectById(id);
    }

    default long count() {
        return count(Where.where());
    }

    default List<SystemUser> findAll() {
        return select(Where.where().orderBy(SystemUser::getCreatedAt).desc());
    }

    default int update(SystemUser user) {
        user.setUpdatedAt(LocalDateTime.now());
        return updateById(user);
    }

    default int updatePassword(Long id, String passwordHash) {
        SystemUser user = selectById(id);
        if (user == null) return 0;
        user.setPasswordHash(passwordHash);
        user.setUpdatedAt(LocalDateTime.now());
        return updateById(user);
    }

    default int touchLogin(Long id) {
        SystemUser user = selectById(id);
        if (user == null) return 0;
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return updateById(user);
    }
}

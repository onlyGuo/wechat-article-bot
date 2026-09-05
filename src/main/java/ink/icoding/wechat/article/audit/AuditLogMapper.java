package ink.icoding.wechat.article.audit;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuditLogMapper extends SmartMapper<AuditLog> {
    default List<AuditLog> findRecent(int limit) {
        return selectWithRelations(Where.where().orderBy(AuditLog::getCreatedAt).desc()
                .limit(Math.max(1, limit)));
    }
}

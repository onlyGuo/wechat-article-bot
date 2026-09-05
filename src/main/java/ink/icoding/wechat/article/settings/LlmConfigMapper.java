package ink.icoding.wechat.article.settings;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmConfigMapper extends SmartMapper<LlmConfig> {
    default LlmConfig current() {
        return selectFirst(Where.where().orderBy(LlmConfig::getId).asc());
    }
}

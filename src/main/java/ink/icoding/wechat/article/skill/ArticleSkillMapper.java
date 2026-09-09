package ink.icoding.wechat.article.skill;

import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ArticleSkillMapper extends SmartMapper<ArticleSkill> {
    // Serialize default changes and deletion across application instances.
    @Select("SELECT id FROM ARTICLE_SKILL ORDER BY id FOR UPDATE")
    List<Long> lockSkills();
}

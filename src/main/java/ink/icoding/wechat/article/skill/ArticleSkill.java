package ink.icoding.wechat.article.skill;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("article_skill")
public class ArticleSkill extends PO {
    @ID private Long id;
    private String name;
    @TableField(length = 1000) private String description;
    @TableField(columnType = "MEDIUMTEXT") private String content;
    private Boolean isDefault;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

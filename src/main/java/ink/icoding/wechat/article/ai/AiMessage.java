package ink.icoding.wechat.article.ai;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("ai_message")
public class AiMessage extends PO {
    @ID
    private Long id;
    private Long articleId;
    private String role;
    @TableField(length = 65535)
    private String content;
    private String status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long createdBy;
    private LocalDateTime createdAt;
}

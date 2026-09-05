package ink.icoding.wechat.article.ai;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent4j 文章会话的完整序列化状态。 */
@Data
@SmartMeta
@TableName("article_agent_session")
public class ArticleAgentSession extends PO {
    @ID
    private Long id;
    private Long articleId;
    @TableField(length = 16_777_216)
    private String serializedSession;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("article_revision")
public class ArticleRevision extends PO {
    @ID
    private Long id;
    private Long articleId;
    private Integer revision;
    private String title;
    @TableField(length = 500)
    private String digest;
    @TableField(length = 65535)
    private String contentHtml;
    private String changeSource;
    @TableField(length = 500)
    private String changeSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
}

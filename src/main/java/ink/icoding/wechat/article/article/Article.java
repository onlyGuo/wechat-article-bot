package ink.icoding.wechat.article.article;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import ink.icoding.wechat.article.account.WechatAccount;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("article")
public class Article extends PO {
    @ID
    private Long id;
    private Long accountId;
    @TableField(exist = false, link = WechatAccount.class, linkField = "name", self = "accountId", target = "id")
    private String accountName;
    private String title;
    private String author;
    @TableField(length = 500)
    private String digest;
    @TableField(length = 65535)
    private String contentHtml;
    @TableField(length = 65535)
    private String contentText;
    @TableField(length = 500)
    private String coverUrl;
    private Long coverAssetId;
    @TableField(length = 1000)
    private String sourceUrl;
    private String sourceType;
    private String businessStatus;
    private String workflowStatus;
    private String wechatStatus;
    private String wechatMediaId;
    private String wechatArticleId;
    private String wechatPublishId;
    private Integer revision;
    private Boolean deleted;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

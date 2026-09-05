package ink.icoding.wechat.article.account;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("wechat_account")
public class WechatAccount extends PO {
    @ID
    private Long id;
    private String name;
    private String appId;
    @TableField(length = 65535)
    private String appSecretEncrypted;
    private String originalId;
    private String accountType;
    private Boolean verified;
    @TableField(length = 500)
    private String avatarUrl;
    private String defaultAuthor;
    private String defaultStyle;
    private String status;
    private String connectionStatus;
    @TableField(length = 65535)
    private String capabilities;
    @TableField(length = 65535)
    private String tokenEncrypted;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

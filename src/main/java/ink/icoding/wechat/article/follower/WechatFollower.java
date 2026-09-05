package ink.icoding.wechat.article.follower;

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
@TableName("wechat_follower")
public class WechatFollower extends PO {
    @ID
    private Long id;
    private Long accountId;
    @TableField(exist = false, link = WechatAccount.class, linkField = "name", self = "accountId", target = "id")
    private String accountName;
    private String openId;
    private String unionId;
    private String nickname;
    @TableField(length = 500)
    private String avatarUrl;
    private String remark;
    @TableField(length = 500)
    private String tags;
    private Boolean subscribed;
    private LocalDateTime subscribedAt;
    private LocalDateTime updatedAt;
}

package ink.icoding.wechat.article.schedule;

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
@TableName("schedule_task")
public class ScheduleTask extends PO {
    @ID
    private Long id;
    private String name;
    private Long accountId;
    private Long skillId;
    @TableField(exist = false, link = WechatAccount.class, linkField = "name", self = "accountId", target = "id")
    private String accountName;
    private Long coverAssetId;
    private String cronExpression;
    private String timezone;
    @TableField(length = 65535)
    private String aiPrompt;
    private String outputMode;
    private Boolean enabled;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

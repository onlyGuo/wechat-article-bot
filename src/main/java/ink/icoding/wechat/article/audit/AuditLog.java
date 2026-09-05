package ink.icoding.wechat.article.audit;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import ink.icoding.wechat.article.auth.model.SystemUser;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("audit_log")
public class AuditLog extends PO {
    @ID
    private Long id;
    private Long userId;
    @TableField(exist = false, link = SystemUser.class, linkField = "username", self = "userId", target = "id")
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    @TableField(length = 65535)
    private String detail;
    private String ipAddress;
    private LocalDateTime createdAt;
}

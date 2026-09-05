package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("task_run")
public class TaskRun extends PO {
    @ID
    private Long id;
    private Long taskId;
    private String triggerType;
    private String status;
    private Integer fetchedCount;
    private Integer generatedCount;
    private Long articleId;
    private Integer toolCallCount;
    @TableField(length = 65535)
    private String message;
    @TableField(length = 65535)
    private String executionLog;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

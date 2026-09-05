package ink.icoding.wechat.article.asset;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("asset")
public class Asset extends PO {
    @ID
    private Long id;
    private Long accountId;
    private String originalName;
    private String storageName;
    @TableField(length = 1000)
    private String storagePath;
    @TableField(length = 1000)
    private String publicUrl;
    private String contentType;
    private Long fileSize;
    private String sourceType;
    @TableField(length = 2000)
    private String sourceUrl;
    @TableField(length = 1000)
    private String description;
    private String wechatMediaId;
    @TableField(length = 1000)
    private String wechatContentUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
}

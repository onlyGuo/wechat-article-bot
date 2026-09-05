package ink.icoding.wechat.article.settings;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("llm_config")
public class LlmConfig extends PO {
    @ID
    private Long id;
    private String provider;
    @TableField(length = 500)
    private String baseUrl;
    private String modelName;
    @TableField(length = 65535)
    private String apiKeyEncrypted;
    @TableField(length = 500)
    private String imageBaseUrl;
    private String imageModelName;
    @TableField(length = 65535)
    private String imageApiKeyEncrypted;
    private Boolean enabled;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

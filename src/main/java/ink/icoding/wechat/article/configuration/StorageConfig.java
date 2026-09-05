package ink.icoding.wechat.article.configuration;

import ink.icoding.wechat.article.asset.AssetService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import ink.icoding.wechat.article.audit.AuditInterceptor;

@Configuration
public class StorageConfig implements WebMvcConfigurer {
    private final AssetService assetService;
    private final AuditInterceptor auditInterceptor;

    public StorageConfig(AssetService assetService, AuditInterceptor auditInterceptor) {
        this.assetService = assetService;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = assetService.getStoragePath().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}

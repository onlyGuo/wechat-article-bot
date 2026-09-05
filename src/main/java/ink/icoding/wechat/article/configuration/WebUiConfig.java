package ink.icoding.wechat.article.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Set;

@Configuration
public class WebUiConfig implements WebMvcConfigurer {
    private static final Set<String> BACKEND_ROOTS = Set.of("api", "uploads", "assets");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new SpaPathResourceResolver());
    }

    private static final class SpaPathResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (!isFrontendRoute(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        private boolean isFrontendRoute(String resourcePath) {
            if (resourcePath == null || resourcePath.isBlank() || resourcePath.contains(".")) {
                return false;
            }
            String root = resourcePath.split("/", 2)[0];
            return !BACKEND_ROOTS.contains(root);
        }
    }
}

package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/llm")
@PreAuthorize("hasRole('ADMIN')")
public class LlmConfigController {
    private final LlmConfigService service;

    public LlmConfigController(LlmConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<LlmConfigService.ConfigView> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<LlmConfigService.ConfigView> update(@Valid @RequestBody LlmConfigService.UpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}

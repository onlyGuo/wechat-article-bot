package ink.icoding.wechat.article.ai;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/articles/{articleId}/ai")
public class ArticleAiController {
    private final ArticleAiService service;

    public ArticleAiController(ArticleAiService service) {
        this.service = service;
    }

    @GetMapping("/messages")
    public ApiResponse<List<AiMessage>> messages(@PathVariable Long articleId) {
        return ApiResponse.ok(service.messages(articleId));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public SseEmitter chat(@PathVariable Long articleId, @Valid @RequestBody ChatRequest request,
                           HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return service.chat(articleId, request.instruction(), request.assetIds());
    }

    @PostMapping("/sessions/{sessionId}/tools/{callId}/result")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Void> toolResult(@PathVariable Long articleId, @PathVariable String sessionId,
                                        @PathVariable String callId,
                                        @RequestBody ArticleAiService.ToolResultRequest request) {
        service.completeTool(articleId, sessionId, callId, request);
        return ApiResponse.ok();
    }

    public record ChatRequest(@NotBlank String instruction, List<Long> assetIds) {}
}

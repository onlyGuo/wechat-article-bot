package ink.icoding.wechat.article.follower;

import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/followers")
public class WechatFollowerController {
    private final WechatFollowerService service;

    public WechatFollowerController(WechatFollowerService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<WechatFollower>> list(@RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(accountId, keyword, page, pageSize));
    }

    @PostMapping("/sync/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<WechatFollowerService.SyncResult> sync(@PathVariable Long accountId) {
        return ApiResponse.ok(service.sync(accountId));
    }
}

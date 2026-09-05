package ink.icoding.wechat.article.account;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class WechatAccountController {
    private final WechatAccountService service;

    public WechatAccountController(WechatAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WechatAccountService.AccountView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<WechatAccountService.AccountView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<WechatAccountService.AccountView> create(@Valid @RequestBody WechatAccountService.AccountRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<WechatAccountService.AccountView> update(@PathVariable Long id,
            @Valid @RequestBody WechatAccountService.AccountRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<WechatAccountService.AccountView> test(@PathVariable Long id) {
        return ApiResponse.ok(service.test(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

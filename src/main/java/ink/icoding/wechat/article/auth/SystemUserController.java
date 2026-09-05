package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/system-users")
@PreAuthorize("hasRole('ADMIN')")
public class SystemUserController {
    private final SystemUserService service;

    public SystemUserController(SystemUserService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<SystemUserService.UserView>> list() { return ApiResponse.ok(service.list()); }

    @PostMapping
    public ApiResponse<SystemUserService.UserView> create(@Valid @RequestBody SystemUserService.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SystemUserService.UserView> update(@PathVariable Long id,
            @Valid @RequestBody SystemUserService.UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/password")
    public ApiResponse<Void> password(@PathVariable Long id,
            @Valid @RequestBody SystemUserService.PasswordRequest request) {
        service.resetPassword(id, request);
        return ApiResponse.ok();
    }
}

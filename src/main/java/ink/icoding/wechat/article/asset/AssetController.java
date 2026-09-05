package ink.icoding.wechat.article.asset;

import ink.icoding.wechat.article.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Asset>> list(@RequestParam(required = false) Long accountId) {
        return ApiResponse.ok(service.list(accountId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Asset> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.required(id));
    }

    @PostMapping
    public ApiResponse<Asset> upload(@RequestParam(required = false) Long accountId,
                                     @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(accountId, file));
    }
}

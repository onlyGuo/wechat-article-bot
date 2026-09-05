package ink.icoding.wechat.article.asset;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AssetService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final AssetMapper mapper;
    private final CurrentUserService currentUserService;
    private final WechatClient wechatClient;
    private final Path storagePath;
    private final ConcurrentMap<String, Object> wechatMaterialLocks = new ConcurrentHashMap<>();

    public AssetService(AssetMapper mapper, CurrentUserService currentUserService, WechatClient wechatClient,
                        @Value("${app.storage.path}") String storagePath) {
        this.mapper = mapper;
        this.currentUserService = currentUserService;
        this.wechatClient = wechatClient;
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public List<Asset> list(Long accountId) {
        return mapper.findAll(accountId);
    }

    public Asset required(Long id) {
        Asset asset = mapper.findById(id);
        if (asset == null) throw new BusinessException("素材不存在");
        return asset;
    }

    public Asset upload(Long accountId, MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("请选择文件");
        if (!ALLOWED_TYPES.contains(file.getContentType())) throw new BusinessException("仅支持 JPG、PNG、GIF、WebP 图片");
        try {
            return saveImage(accountId, file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                    "USER_UPLOAD", null, null, currentUserService.required().id());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("素材保存失败：" + exception.getMessage());
        }
    }

    public String ensureWechatThumb(Long assetId, Long accountId) {
        if (accountId == null) throw new BusinessException("上传微信封面前必须选择公众号");
        synchronized (wechatMaterialLocks.computeIfAbsent("thumb:" + accountId + ":" + assetId,
                ignored -> new Object())) {
            Asset asset = required(assetId);
            if (asset.getAccountId() != null && !accountId.equals(asset.getAccountId())) {
                throw new BusinessException("封面素材不属于当前公众号：" + asset.getOriginalName());
            }
            if (asset.getWechatMediaId() != null && !asset.getWechatMediaId().isBlank()) {
                if (asset.getAccountId() == null) {
                    mapper.updateWechatMedia(assetId, accountId, asset.getWechatMediaId());
                }
                return asset.getWechatMediaId();
            }
            String mediaId = wechatClient.uploadThumb(accountId, resolveStoragePath(asset));
            mapper.updateWechatMedia(assetId, accountId, mediaId);
            return mediaId;
        }
    }

    public String ensureWechatContentImage(String storageName, Long accountId) {
        synchronized (wechatMaterialLocks.computeIfAbsent("content:" + accountId + ":" + storageName,
                ignored -> new Object())) {
            Asset asset = mapper.findByStorageName(storageName);
            if (asset == null) throw new BusinessException("正文引用的素材不存在：" + storageName);
            if (asset.getAccountId() != null && !asset.getAccountId().equals(accountId)) {
                throw new BusinessException("正文图片不属于当前公众号：" + asset.getOriginalName());
            }
            if (asset.getWechatContentUrl() != null && !asset.getWechatContentUrl().isBlank()) {
                return asset.getWechatContentUrl();
            }
            String url = wechatClient.uploadArticleImage(accountId, resolveStoragePath(asset));
            mapper.updateWechatContentUrl(asset.getId(), url);
            return url;
        }
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public Asset saveImage(Long accountId, String originalName, String contentType, byte[] bytes,
                           String sourceType, String sourceUrl, String description, Long userId) {
        if (!ALLOWED_TYPES.contains(contentType)) throw new BusinessException("仅支持 JPG、PNG、GIF、WebP 图片");
        if (bytes == null || bytes.length == 0) throw new BusinessException("图片内容为空");
        if (bytes.length > 10 * 1024 * 1024) throw new BusinessException("图片不能超过 10MB");
        validateImageSignature(contentType, bytes);
        try {
            Files.createDirectories(storagePath);
            String storageName = UUID.randomUUID().toString().replace("-", "") + extensionForType(contentType);
            Path target = storagePath.resolve(storageName).normalize();
            if (!target.startsWith(storagePath)) throw new BusinessException("文件路径不合法");
            Files.write(target, bytes);
            Asset asset = new Asset();
            asset.setAccountId(accountId);
            asset.setOriginalName(originalName == null || originalName.isBlank() ? storageName : originalName);
            asset.setStorageName(storageName);
            asset.setStoragePath(storageName);
            asset.setPublicUrl("/uploads/" + storageName);
            asset.setContentType(contentType);
            asset.setFileSize((long) bytes.length);
            asset.setSourceType(sourceType);
            asset.setSourceUrl(sourceUrl);
            asset.setDescription(description);
            asset.setCreatedBy(userId);
            asset.setCreatedAt(LocalDateTime.now());
            mapper.insert(asset);
            return required(asset.getId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("素材保存失败：" + exception.getMessage());
        }
    }

    public byte[] readBytes(Long assetId) {
        Asset asset = required(assetId);
        try {
            return Files.readAllBytes(resolveStoragePath(asset));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("素材文件读取失败：" + exception.getMessage());
        }
    }

    Path resolveStoragePath(Asset asset) {
        String storedValue = asset.getStoragePath();
        if (storedValue != null && !storedValue.isBlank()) {
            try {
                Path stored = Path.of(storedValue);
                Path candidate = (stored.isAbsolute() ? stored : storagePath.resolve(stored)).normalize();
                if (candidate.startsWith(storagePath) && Files.isRegularFile(candidate)) {
                    migrateStoragePath(asset, candidate.getFileName().toString());
                    return candidate;
                }
            } catch (Exception ignored) {
                // Try the portable storage name below. Old databases may contain a path from another OS.
            }
        }

        String storageName = asset.getStorageName();
        if ((storageName == null || storageName.isBlank()) && storedValue != null && !storedValue.isBlank()) {
            try {
                storageName = Path.of(storedValue).getFileName().toString();
            } catch (Exception ignored) {
                storageName = null;
            }
        }
        if (storageName != null && !storageName.isBlank()) {
            Path fallback = storagePath.resolve(storageName).normalize();
            if (fallback.startsWith(storagePath) && Files.isRegularFile(fallback)) {
                migrateStoragePath(asset, storageName);
                return fallback;
            }
        }

        String displayName = storageName == null || storageName.isBlank() ? asset.getOriginalName() : storageName;
        throw new BusinessException("素材文件不存在：" + displayName + "；当前素材目录为 " + storagePath
                + "。请确认 data/uploads 已挂载到该目录，或通过 STORAGE_PATH 指定实际目录");
    }

    private void migrateStoragePath(Asset asset, String storageName) {
        if (storageName.equals(asset.getStoragePath())) return;
        mapper.updateStoragePath(asset.getId(), storageName);
        asset.setStoragePath(storageName);
    }

    public List<Asset> search(Long accountId, String keyword, int limit) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return mapper.findAll(accountId).stream()
                .filter(asset -> normalized.isBlank()
                        || contains(asset.getOriginalName(), normalized)
                        || contains(asset.getDescription(), normalized))
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String extensionForType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }

    private void validateImageSignature(String contentType, byte[] bytes) {
        boolean valid = switch (contentType) {
            case "image/jpeg" -> bytes.length > 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8;
            case "image/png" -> bytes.length > 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case "image/gif" -> bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
            case "image/webp" -> bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
        if (!valid) throw new BusinessException("图片内容与文件类型不匹配");
    }
}

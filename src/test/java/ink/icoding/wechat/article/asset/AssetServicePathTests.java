package ink.icoding.wechat.article.asset;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServicePathTests {
    @TempDir
    Path storageDirectory;

    @Test
    void resolvesLegacyAbsolutePathAgainstCurrentStorageDirectoryAndMigratesIt() throws Exception {
        AssetMapper mapper = mock(AssetMapper.class);
        Asset asset = asset(7L, "cover.webp",
                "/Users/someone/old-project/data/uploads/cover.webp");
        byte[] content = {1, 2, 3, 4};
        Files.write(storageDirectory.resolve("cover.webp"), content);
        when(mapper.findById(7L)).thenReturn(asset);
        AssetService service = service(mapper);

        assertArrayEquals(content, service.readBytes(7L));
        assertEquals("cover.webp", asset.getStoragePath());
        verify(mapper).updateStoragePath(7L, "cover.webp");
    }

    @Test
    void reportsConfiguredStorageDirectoryWhenMigratedFileIsMissing() {
        AssetMapper mapper = mock(AssetMapper.class);
        when(mapper.findById(8L)).thenReturn(asset(8L, "missing.png", "/old/data/uploads/missing.png"));
        AssetService service = service(mapper);

        BusinessException error = assertThrows(BusinessException.class, () -> service.readBytes(8L));
        assertTrue(error.getMessage().contains("missing.png"));
        assertTrue(error.getMessage().contains(storageDirectory.toAbsolutePath().normalize().toString()));
        assertTrue(error.getMessage().contains("STORAGE_PATH"));
    }

    @Test
    void uploadsArticleImageOnceAndCachesWechatContentUrl() throws Exception {
        AssetMapper mapper = mock(AssetMapper.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Asset asset = asset(9L, "inline.png", "inline.png");
        asset.setAccountId(3L);
        Files.write(storageDirectory.resolve("inline.png"), new byte[]{1, 2, 3});
        when(mapper.findByStorageName("inline.png")).thenReturn(asset);
        when(wechatClient.uploadArticleImage(3L, storageDirectory.resolve("inline.png")))
                .thenReturn("https://mmbiz.qpic.cn/example");
        AssetService service = service(mapper, wechatClient);

        assertEquals("https://mmbiz.qpic.cn/example", service.ensureWechatContentImage("inline.png", 3L));
        verify(mapper).updateWechatContentUrl(9L, "https://mmbiz.qpic.cn/example");
    }

    @Test
    void reusesCachedWechatContentUrl() {
        AssetMapper mapper = mock(AssetMapper.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Asset asset = asset(10L, "cached.jpg", "cached.jpg");
        asset.setAccountId(3L);
        asset.setWechatContentUrl("https://mmbiz.qpic.cn/cached");
        when(mapper.findByStorageName("cached.jpg")).thenReturn(asset);
        AssetService service = service(mapper, wechatClient);

        assertEquals("https://mmbiz.qpic.cn/cached", service.ensureWechatContentImage("cached.jpg", 3L));
        verify(wechatClient, never()).uploadArticleImage(3L, storageDirectory.resolve("cached.jpg"));
    }

    @Test
    void reusesLegacyCoverMediaIdAndBindsPreviouslyUnassignedAssetToAccount() {
        AssetMapper mapper = mock(AssetMapper.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Asset asset = asset(11L, "cover.jpg", "cover.jpg");
        asset.setWechatMediaId("existing-media-id");
        when(mapper.findById(11L)).thenReturn(asset);
        AssetService service = service(mapper, wechatClient);

        assertEquals("existing-media-id", service.ensureWechatThumb(11L, 3L));

        verify(mapper).updateWechatMedia(11L, 3L, "existing-media-id");
        verify(wechatClient, never()).uploadThumb(3L, storageDirectory.resolve("cover.jpg"));
    }

    @Test
    void uploadsNewCoverOnceAndPersistsAccountScopedMediaId() throws Exception {
        AssetMapper mapper = mock(AssetMapper.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Asset asset = asset(12L, "new-cover.png", "new-cover.png");
        asset.setAccountId(3L);
        Files.write(storageDirectory.resolve("new-cover.png"), new byte[]{1, 2, 3});
        when(mapper.findById(12L)).thenReturn(asset);
        when(wechatClient.uploadThumb(3L, storageDirectory.resolve("new-cover.png")))
                .thenReturn("new-media-id");
        AssetService service = service(mapper, wechatClient);

        assertEquals("new-media-id", service.ensureWechatThumb(12L, 3L));

        verify(wechatClient).uploadThumb(3L, storageDirectory.resolve("new-cover.png"));
        verify(mapper).updateWechatMedia(12L, 3L, "new-media-id");
    }

    private AssetService service(AssetMapper mapper) {
        return service(mapper, mock(WechatClient.class));
    }

    private AssetService service(AssetMapper mapper, WechatClient wechatClient) {
        return new AssetService(mapper, mock(CurrentUserService.class), wechatClient,
                storageDirectory.toString());
    }

    private Asset asset(Long id, String storageName, String storagePath) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setOriginalName(storageName);
        asset.setStorageName(storageName);
        asset.setStoragePath(storagePath);
        return asset;
    }
}

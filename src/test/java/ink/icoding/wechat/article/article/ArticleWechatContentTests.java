package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleWechatContentTests {
    private static final String STORAGE_NAME = "0123456789abcdef0123456789abcdef.webp";

    @Test
    void replacesLocalImagesWithWechatUrlsAndKeepsParagraphSpacing() {
        AssetService assetService = mock(AssetService.class);
        when(assetService.ensureWechatContentImage(STORAGE_NAME, 5L))
                .thenReturn("https://mmbiz.qpic.cn/wechat-image");
        ArticleService service = service(assetService);
        Article article = new Article();
        article.setAccountId(5L);
        article.setContentHtml("<p><img src=\"/uploads/" + STORAGE_NAME + "\" alt=\"配图\"></p>"
                + "<p>正文</p><p><img src=\"/uploads/" + STORAGE_NAME + "\"></p>");

        String content = service.prepareWechatContent(article);

        assertEquals(2, content.split("https://mmbiz.qpic.cn/wechat-image", -1).length - 1);
        assertTrue(content.contains("alt=\"配图\""));
        assertEquals(3, content.split("margin-bottom: 16px", -1).length - 1);
        verify(assetService, times(1)).ensureWechatContentImage(STORAGE_NAME, 5L);
    }

    @Test
    void leavesNonLocalImagesUntouched() {
        AssetService assetService = mock(AssetService.class);
        ArticleService service = service(assetService);
        Article article = new Article();
        article.setAccountId(5L);
        article.setContentHtml("<p><img src=\"https://example.com/image.png\"></p>");

        String content = service.prepareWechatContent(article);

        assertTrue(content.contains("src=\"https://example.com/image.png\""));
    }

    @Test
    void reportsRealDraftSynchronizationStagesInOrder() {
        ArticleMapper mapper = mock(ArticleMapper.class);
        AssetService assetService = mock(AssetService.class);
        WechatAccountService accountService = mock(WechatAccountService.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Article article = article(false);
        when(mapper.findById(1L)).thenReturn(article);
        when(assetService.ensureWechatThumb(9L, 5L)).thenReturn("thumb-media-id");
        when(accountService.required(5L)).thenReturn(new WechatAccount());
        when(wechatClient.addDraft(eq(5L), anyMap())).thenReturn("draft-media-id");
        ArticleService service = new ArticleService(mapper, mock(ArticleRevisionMapper.class),
                mock(CurrentUserService.class), assetService, accountService, wechatClient);
        List<ArticleService.WechatProgress> events = new ArrayList<>();

        service.syncDraft(1L, events::add);

        assertIterableEquals(List.of("VALIDATING", "COVER", "CONTENT", "CONTENT", "DRAFT", "SAVING", "DONE"),
                events.stream().map(ArticleService.WechatProgress::stage).toList());
        assertEquals(100, events.get(events.size() - 1).percent());
        verify(wechatClient).addDraft(eq(5L), anyMap());
    }

    @Test
    void reportsPublishSubmissionStagesWhenDraftAlreadyExists() {
        ArticleMapper mapper = mock(ArticleMapper.class);
        WechatClient wechatClient = mock(WechatClient.class);
        Article article = article(true);
        when(mapper.findById(1L)).thenReturn(article);
        when(wechatClient.publish(5L, "existing-draft-id")).thenReturn("publish-id");
        ArticleService service = new ArticleService(mapper, mock(ArticleRevisionMapper.class),
                mock(CurrentUserService.class), mock(AssetService.class), mock(WechatAccountService.class),
                wechatClient);
        List<ArticleService.WechatProgress> events = new ArrayList<>();

        service.publish(1L, events::add);

        assertIterableEquals(List.of("VALIDATING", "DRAFT", "PUBLISH", "SAVING", "DONE"),
                events.stream().map(ArticleService.WechatProgress::stage).toList());
        assertEquals(100, events.get(events.size() - 1).percent());
        verify(mapper).markPublishing(1L, "publish-id");
    }

    private Article article(boolean synchronizedDraft) {
        Article article = new Article();
        article.setId(1L);
        article.setAccountId(5L);
        article.setCoverAssetId(9L);
        article.setTitle("测试文章");
        article.setContentHtml("<p>正文</p>");
        if (synchronizedDraft) article.setWechatMediaId("existing-draft-id");
        return article;
    }

    private ArticleService service(AssetService assetService) {
        return new ArticleService(mock(ArticleMapper.class), mock(ArticleRevisionMapper.class),
                mock(CurrentUserService.class), assetService, mock(WechatAccountService.class),
                mock(WechatClient.class));
    }
}

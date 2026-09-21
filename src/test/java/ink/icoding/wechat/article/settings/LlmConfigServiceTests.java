package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmConfigServiceTests {

    @Test
    void reportsHowToRecoverWhenStoredApiKeyCannotBeDecrypted() {
        String encrypted = new CryptoService("original-secret-key").encrypt("test-api-key");
        LlmConfigService service = new LlmConfigService(null, new CryptoService("different-secret-key"), null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.decryptCredential(encrypted, "LLM API Key"));
        assertTrue(error.getMessage().contains("LLM API Key 解密失败"));
        assertTrue(error.getMessage().contains("APP_SECRET_KEY"));
        assertTrue(error.getMessage().contains("重新填写"));
    }
}

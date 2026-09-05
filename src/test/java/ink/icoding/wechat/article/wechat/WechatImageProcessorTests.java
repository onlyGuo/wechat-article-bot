package ink.icoding.wechat.article.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatImageProcessorTests {
    @TempDir
    Path directory;

    @Test
    void keepsSupportedSmallPngWithoutCreatingTemporaryFile() throws Exception {
        Path source = directory.resolve("small.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), "png", source.toFile());

        try (WechatImageProcessor.PreparedImage prepared = WechatImageProcessor.prepare(source)) {
            assertEquals(source, prepared.path());
            assertFalse(prepared.temporary());
        }
        assertTrue(Files.exists(source));
    }

    @Test
    void convertsAndCompressesOversizedImageToWechatCompatibleJpeg() throws Exception {
        Path source = directory.resolve("large.png");
        BufferedImage image = new BufferedImage(1400, 1400, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        }
        ImageIO.write(image, "png", source.toFile());
        assertTrue(Files.size(source) >= WechatImageProcessor.MAX_FILE_SIZE);

        Path temporary;
        try (WechatImageProcessor.PreparedImage prepared = WechatImageProcessor.prepare(source)) {
            temporary = prepared.path();
            assertTrue(prepared.temporary());
            assertTrue(temporary.getFileName().toString().endsWith(".jpg"));
            assertTrue(Files.size(temporary) < WechatImageProcessor.MAX_FILE_SIZE);
            assertNotNull(ImageIO.read(temporary.toFile()));
        }
        assertFalse(Files.exists(temporary));
    }
}

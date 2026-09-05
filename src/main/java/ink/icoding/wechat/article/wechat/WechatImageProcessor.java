package ink.icoding.wechat.article.wechat;

import ink.icoding.wechat.article.common.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

final class WechatImageProcessor {
    static final long MAX_FILE_SIZE = 1024L * 1024L;
    private static final int MIN_DIMENSION = 320;

    private WechatImageProcessor() {
    }

    static PreparedImage prepare(Path source) {
        try {
            String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
            if ((name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                    && Files.size(source) < MAX_FILE_SIZE) {
                return new PreparedImage(source, false);
            }
            BufferedImage decoded = ImageIO.read(source.toFile());
            if (decoded == null) throw new BusinessException("微信正文图片无法解码：" + source.getFileName());
            Path converted = Files.createTempFile("wechat-content-image-", ".jpg");
            try {
                encodeUnderLimit(decoded, converted);
                return new PreparedImage(converted, true);
            } catch (Exception exception) {
                Files.deleteIfExists(converted);
                throw exception;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("微信正文图片处理失败：" + exception.getMessage());
        }
    }

    private static void encodeUnderLimit(BufferedImage source, Path target) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage current = flatten(source, width, height);
        while (true) {
            for (float quality : new float[]{0.9f, 0.82f, 0.72f, 0.62f, 0.52f}) {
                writeJpeg(current, target, quality);
                if (Files.size(target) < MAX_FILE_SIZE) return;
            }
            if (Math.min(width, height) <= MIN_DIMENSION) {
                throw new BusinessException("图片压缩后仍超过微信正文图片 1MB 限制");
            }
            width = Math.max(MIN_DIMENSION, Math.round(width * 0.8f));
            height = Math.max(MIN_DIMENSION, Math.round(height * 0.8f));
            current = flatten(source, width, height);
        }
    }

    private static BufferedImage flatten(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new BusinessException("当前 Java 环境缺少 JPEG 编码器");
        ImageWriter writer = writers.next();
        Files.deleteIfExists(target);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    record PreparedImage(Path path, boolean temporary) implements AutoCloseable {
        @Override
        public void close() {
            if (!temporary) return;
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Temporary files are also cleaned by the operating system; upload result is unaffected.
            }
        }
    }
}

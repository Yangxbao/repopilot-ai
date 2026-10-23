package com.repopilot.service.index;

import com.repopilot.common.exception.BizException;
import com.repopilot.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZIP 导入安全测试：路径穿越、压缩炸弹、超大文件、垃圾文件过滤。
 */
class ZipImporterTest {

    private final ZipImporter importer = new ZipImporter();
    private final AppProperties.Upload limits = new AppProperties.Upload(30, 5000, 512, 30, 500);

    @TempDir
    Path tempDir;

    private Path destDir;

    @BeforeEach
    void setUp() {
        destDir = tempDir.resolve("dest");
    }

    @Test
    @DisplayName("zipSlip：../ 路径穿越条目被拒绝")
    void zipSlipRejected() {
        byte[] zip = zip("evil/../../evil.txt", "malicious");
        BizException e = assertThrows(BizException.class,
                () -> importer.extract(new ByteArrayInputStream(zip), destDir, limits));
        assertTrue(e.getMessage().contains("穿越"));
    }

    @Test
    @DisplayName("zipSlip：绝对路径与盘符条目被拒绝")
    void absolutePathRejected() {
        byte[] zip = zip("/etc/evil.txt", "x");
        assertThrows(BizException.class,
                () -> importer.extract(new ByteArrayInputStream(zip), destDir, limits));
    }

    @Test
    @DisplayName("不是 ZIP 文件时报 BAD_ZIP")
    void notAZipRejected() {
        byte[] garbage = "this is not a zip file".getBytes(StandardCharsets.UTF_8);
        assertThrows(BizException.class,
                () -> importer.extract(new ByteArrayInputStream(garbage), destDir, limits));
    }

    @Test
    @DisplayName("超过单文件大小限制的条目被拒绝")
    void oversizedFileRejected() {
        byte[] big = new byte[600 * 1024];
        byte[] zip = zip("big.txt", new String(big, StandardCharsets.ISO_8859_1));
        BizException e = assertThrows(BizException.class,
                () -> importer.extract(new ByteArrayInputStream(zip), destDir, limits));
        assertTrue(e.getMessage().contains("512"));
    }

    @Test
    @DisplayName("超高压缩比（压缩炸弹特征）被拒绝")
    void compressionBombRejected() {
        // 100KB 全 0 文本可压缩到 ~200 字节，压缩比远超 500
        byte[] zeros = new byte[100 * 1024];
        byte[] zip = zip("zeros.txt", new String(zeros, StandardCharsets.ISO_8859_1));
        assertThrows(BizException.class,
                () -> importer.extract(new ByteArrayInputStream(zip), destDir, limits));
    }

    @Test
    @DisplayName("正常项目解压成功：白名单文本保留")
    void normalProjectExtracted() throws IOException {
        byte[] zip = zip(
                "src/main/java/App.java", "public class App {}",
                "README.md", "# Demo",
                "config.yml", "key: value");
        var result = importer.extract(new ByteArrayInputStream(zip), destDir, limits);
        assertEquals(3, result.keptFiles());
        assertTrue(Files.exists(destDir.resolve("src/main/java/App.java")));
        assertTrue(Files.readString(destDir.resolve("README.md")).contains("Demo"));
    }

    @Test
    @DisplayName("垃圾与危险文件被过滤：.git、node_modules、target、.env、二进制")
    void junkFilesSkipped() throws IOException {
        byte[] pngLike = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x00, 0x00, 0x00, 0x00};
        byte[] zip = zip(
                ".git/config", "[core]",
                "node_modules/pkg/index.js", "module.exports = 1",
                "target/classes/App.class", "compiled",
                ".env", "SECRET=1",
                "logo.png", new String(pngLike, StandardCharsets.ISO_8859_1),
                "src/App.java", "public class App {}");
        var result = importer.extract(new ByteArrayInputStream(zip), destDir, limits);
        assertEquals(1, result.keptFiles());
        assertTrue(Files.exists(destDir.resolve("src/App.java")));
        assertFalse(Files.exists(destDir.resolve(".env")));
    }

    /**
     * 用 (名称, 内容, 名称, 内容...) 的形式构造内存中的 ZIP 字节。
     */
    private static byte[] zip(String... nameContentPairs) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i + 1 < nameContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zos.write(nameContentPairs[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }
}

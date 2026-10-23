package com.repopilot.service.index;

import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import com.repopilot.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * ZIP 安全校验与解压。
 *
 * <p>针对导入不可信压缩包的常见攻击面逐一设防：
 * <ul>
 *   <li>ZIP 路径穿越（Zip Slip）：拒绝 ".." / 绝对路径 / 盘符 / NUL 字符，
 *       并做 canonical path 二次校验；</li>
 *   <li>压缩炸弹：限制 ZIP 体积、条目数、单文件解压后大小、总体积与压缩比；</li>
 *   <li>垃圾与危险文件：只保留白名单内的文本扩展名，跳过 .git、node_modules、
 *       target 等目录与 .env、*.pem 等疑似密钥文件；</li>
 *   <li>二进制伪装：文件头 8KB 内出现 NUL 字节视为二进制，跳过。</li>
 * </ul>
 * 任何校验失败都会抛出 BizException 并清理已解压内容。
 * 注意：导入过程只读取文件内容，绝不执行其中的任何脚本或程序。
 */
@Component
public class ZipImporter {

    /** 允许索引的扩展名 → 语言名映射（小写、不含点）。 */
    private static final java.util.Map<String, String> EXT_LANGUAGE = buildExtLanguage();

    /** 无扩展名但允许索引的特殊文件名。注意刻意不包含 .env。 */
    private static final Set<String> SPECIAL_FILENAMES = new HashSet<>(
            Arrays.asList("dockerfile", "makefile", "license", ".gitignore", ".gitattributes", ".editorconfig"));

    /** 一律跳过的路径段（目录名或文件名，小写）。 */
    private static final Set<String> EXCLUDED_SEGMENTS = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "dist", "out", "bin",
            ".idea", ".vscode", ".gradle", ".mvn", "__pycache__", ".next", ".nuxt", "vendor",
            "coverage", "logs", ".pytest_cache", ".terraform", ".DS_Store"));

    /** 一律跳过的疑似密钥/证书文件名（即使扩展名碰巧在白名单内）。 */
    private static final Set<String> SECRET_FILENAMES = new HashSet<>(Arrays.asList(
            ".env", ".env.local", ".env.production", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519"));

    private static final int MAX_PATH_SEGMENTS = 30;
    private static final int BINARY_SNIFF_BYTES = 8192;

    public record ExtractResult(int keptFiles, long totalBytes) {
    }

    /**
     * 解压 ZIP 到 destDir，只保留安全白名单内的文本文件。
     *
     * @throws BizException BAD_ZIP（非法/危险压缩包）或 UPLOAD_TOO_LARGE（超限）
     */
    public ExtractResult extract(InputStream zipStream, Path destDir, AppProperties.Upload limits) {
        // 先把流写入临时文件并施加硬上限，避免恶意超大流拖垮内存/磁盘
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("repopilot-upload-", ".zip");
            long maxZipBytes = limits.maxZipMb() * 1024L * 1024L;
            long copied = copyBounded(zipStream, tempFile, maxZipBytes, null);
            if (copied > maxZipBytes) {
                throw new BizException(ErrorCode.UPLOAD_TOO_LARGE, "ZIP 包超过 " + limits.maxZipMb() + "MB 限制");
            }
            Files.createDirectories(destDir);
            return extractZipFile(tempFile, destDir, limits);
        } catch (ZipException e) {
            cleanupDirectory(destDir);
            throw new BizException(ErrorCode.BAD_ZIP, "不是有效的 ZIP 文件：" + e.getMessage());
        } catch (IOException e) {
            cleanupDirectory(destDir);
            throw new BizException(ErrorCode.BAD_ZIP, "读取压缩包失败：" + e.getMessage());
        } catch (BizException e) {
            cleanupDirectory(destDir);
            throw e;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private ExtractResult extractZipFile(Path zipPath, Path destDir, AppProperties.Upload limits)
            throws IOException {
        int entryCount = 0;
        int keptFiles = 0;
        long totalBytes = 0;
        long maxFileBytes = limits.maxFileKb() * 1024L;
        long maxTotalBytes = limits.maxTotalMb() * 1024L * 1024L;

        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > limits.maxEntries()) {
                    throw new BizException(ErrorCode.BAD_ZIP,
                            "压缩包含条目超过 " + limits.maxEntries() + " 个，疑似压缩炸弹");
                }

                String normalized = validateEntryName(entry.getName());

                // 体积与压缩比校验（基于中央目录声明值，解压时还会做二次限制）
                long declaredSize = entry.getSize();
                if (declaredSize > maxFileBytes) {
                    throw new BizException(ErrorCode.BAD_ZIP,
                            "文件 " + normalized + " 超过单文件 " + limits.maxFileKb() + "KB 限制");
                }
                long compressedSize = entry.getCompressedSize();
                if (declaredSize > 0 && compressedSize > 0
                        && declaredSize / compressedSize > limits.maxCompressionRatio()) {
                    throw new BizException(ErrorCode.BAD_ZIP,
                            "文件 " + normalized + " 压缩比异常，疑似压缩炸弹");
                }

                if (isExcluded(normalized) || !isAllowedTextFile(normalized)) {
                    continue;
                }

                Path target = destDir.resolve(normalized).normalize();
                // 二次保险：规范化后必须仍位于目标目录内
                if (!target.startsWith(destDir)) {
                    throw new BizException(ErrorCode.BAD_ZIP, "非法路径：" + entry.getName());
                }

                try (InputStream raw = zipFile.getInputStream(entry)) {
                    // 用 PushbackInputStream 做二进制探测：预读的字节要"退回"，
                    // 否则正式复制时内容已被消费，解压出的会是空文件
                    var in = new java.io.PushbackInputStream(raw, BINARY_SNIFF_BYTES);
                    byte[] head = in.readNBytes(BINARY_SNIFF_BYTES);
                    boolean binary = false;
                    for (byte b : head) {
                        if (b == 0) {
                            binary = true;
                            break;
                        }
                    }
                    if (binary) {
                        continue;
                    }
                    in.unread(head);
                    Files.createDirectories(target.getParent());
                    long written = copyBounded(in, target, maxFileBytes, entry);
                    if (written > maxFileBytes) {
                        throw new BizException(ErrorCode.BAD_ZIP,
                                "文件 " + normalized + " 实际大小超过 " + limits.maxFileKb() + "KB 限制");
                    }
                    totalBytes += written;
                    if (totalBytes > maxTotalBytes) {
                        throw new BizException(ErrorCode.BAD_ZIP,
                                "解压后总大小超过 " + limits.maxTotalMb() + "MB 限制");
                    }
                    keptFiles++;
                }
            }
        }
        return new ExtractResult(keptFiles, totalBytes);
    }

    /** 路径穿越与非法字符校验，返回规范化后的相对路径（/ 分隔）。 */
    String validateEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new BizException(ErrorCode.BAD_ZIP, "压缩包含非法文件名");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new BizException(ErrorCode.BAD_ZIP, "拒绝绝对路径条目：" + name);
        }
        String[] segments = normalized.split("/");
        if (segments.length > MAX_PATH_SEGMENTS) {
            throw new BizException(ErrorCode.BAD_ZIP, "路径层级过深：" + name);
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new BizException(ErrorCode.BAD_ZIP, "拒绝相对路径穿越条目：" + name);
            }
        }
        return normalized;
    }

    static boolean isExcluded(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("/")) {
            if (EXCLUDED_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (SECRET_FILENAMES.contains(fileName)) {
            return true;
        }
        // 锁文件 / sourcemap / 构建日志对理解代码没有价值
        return fileName.endsWith(".min.js") || fileName.endsWith(".min.css")
                || fileName.endsWith(".log") || fileName.endsWith(".lock") || fileName.endsWith(".map");
    }

    static boolean isAllowedTextFile(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (SPECIAL_FILENAMES.contains(fileName)) {
            return true;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return EXT_LANGUAGE.containsKey(fileName.substring(dot + 1));
    }

    static String languageOf(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String lang = EXT_LANGUAGE.get(fileName.substring(dot + 1));
            if (lang != null) {
                return lang;
            }
        }
        if (fileName.equals("dockerfile")) {
            return "Dockerfile";
        }
        if (fileName.equals("makefile")) {
            return "Makefile";
        }
        return "Text";
    }

    static String extensionOf(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        int slash = lower.lastIndexOf('/');
        if (dot < 0 || dot < slash || dot == lower.length() - 1) {
            return "";
        }
        return lower.substring(dot + 1);
    }

    /** 带上限的复制；超过 maxBytes 返回 maxBytes+1（由调用方决定如何报错）。 */
    private static long copyBounded(InputStream in, Path target, long maxBytes, ZipEntry entry) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) {
                    return total;
                }
                out.write(buffer, 0, read);
            }
            return total;
        }
    }

    public static void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static java.util.Map<String, String> buildExtLanguage() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("java", "Java");
        map.put("kt", "Kotlin");
        map.put("xml", "XML");
        map.put("yml", "YAML");
        map.put("yaml", "YAML");
        map.put("json", "JSON");
        map.put("md", "Markdown");
        map.put("markdown", "Markdown");
        map.put("sql", "SQL");
        map.put("js", "JavaScript");
        map.put("jsx", "JavaScript");
        map.put("ts", "TypeScript");
        map.put("tsx", "TypeScript");
        map.put("html", "HTML");
        map.put("htm", "HTML");
        map.put("css", "CSS");
        map.put("scss", "CSS");
        map.put("properties", "Properties");
        map.put("txt", "Text");
        map.put("gradle", "Gradle");
        map.put("vue", "Vue");
        map.put("py", "Python");
        map.put("go", "Go");
        map.put("rs", "Rust");
        map.put("sh", "Shell");
        map.put("ini", "INI");
        map.put("cfg", "INI");
        map.put("conf", "INI");
        map.put("csv", "CSV");
        return java.util.Collections.unmodifiableMap(map);
    }
}

package com.repopilot.service.index;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置示例项目（演示数据）的加载器。
 *
 * <p>示例项目打包在 classpath 的 samples/taskflow/ 目录下。
 * 使用 PathMatchingResourcePatternResolver 保证在 IDE 开发（文件系统）
 * 和打成 jar（嵌套资源）两种运行方式下都能读取到。
 */
@Component
public class SampleProjectProvider {

    public static final String SAMPLE_NAME = "示例项目 TaskFlow（演示数据）";
    private static final String CLASSPATH_ROOT = "classpath*:samples/taskflow/";

    public record SampleFile(String path, String content) {
    }

    public List<SampleFile> loadFiles() {
        List<SampleFile> files = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_ROOT + "**");
            for (Resource resource : resources) {
                if (resource.getFilename() == null) {
                    continue; // 目录
                }
                // 把 classpath 资源还原成相对路径（用 / 分隔）
                String url = resource.getURL().toString();
                int idx = url.indexOf("samples/taskflow/");
                String relative = idx >= 0 ? url.substring(idx + "samples/taskflow/".length()) : resource.getFilename();
                if (relative.isBlank() || relative.endsWith("/")) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    files.add(new SampleFile(relative, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取内置示例项目失败", e);
        }
        // ClassPathResource 目录兜底：确保至少能加载到文件
        if (files.isEmpty() && new ClassPathResource("samples/taskflow/README.md").exists()) {
            throw new IllegalStateException("内置示例项目为空，请检查打包配置");
        }
        return files;
    }
}

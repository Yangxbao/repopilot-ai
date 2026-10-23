package com.repopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RepoPilot AI 后端入口。
 *
 * <p>整体分层与常规 Spring Boot 单体一致：
 * Controller（接口层）→ Service（业务层）→ Mapper（数据层），
 * 其中检索、AI 适配、Agent 工具各自独立成包，便于后续替换实现。
 */
@SpringBootApplication
@MapperScan("com.repopilot.mapper")
public class RepoPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepoPilotApplication.class, args);
    }
}

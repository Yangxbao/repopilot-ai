package com.repopilot.dto.qa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提问请求。 */
public record QaRequest(
        @NotBlank(message = "问题不能为空")
        @Size(min = 2, max = 1000, message = "问题长度必须为 2-1000 字符")
        String question
) {
}

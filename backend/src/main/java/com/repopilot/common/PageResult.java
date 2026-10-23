package com.repopilot.common;

import java.util.List;

/**
 * 分页查询的统一返回结构。
 *
 * @param list  当前页数据
 * @param total 总记录数
 * @param page  当前页码（从 1 开始）
 * @param size  每页大小
 */
public record PageResult<T>(List<T> list, long total, long page, long size) {
}

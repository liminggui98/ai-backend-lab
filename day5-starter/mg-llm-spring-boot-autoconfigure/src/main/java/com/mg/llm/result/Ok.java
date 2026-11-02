package com.mg.llm.result;

/**
 * 大模型 API 一次调用结果
 *
 * @param prompt 提示
 * @param body   回答
 * @param usage  使用情况
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record Ok(String prompt, String body, Usage usage) implements LLMResult {
    public Ok {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body 不能为空");
        }
        if (usage == null) {
            throw new IllegalArgumentException("usage 不能为空");
        }
    }
}
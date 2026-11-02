package com.mg.llm.result;

/**
 * 一次成功的 LLM 调用结果。
 *
 * @param prompt 提示
 * @param body   回答
 * @param usage  token 用量
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
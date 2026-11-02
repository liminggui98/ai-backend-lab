package com.mg.llm.result;

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
package com.mg.llm.result;

public record Usage(long promptTokens, long completionTokens) {
    public Usage {
        if (promptTokens < 0) {
            throw new IllegalArgumentException("promptTokens 不能为负数");
        }
        if (completionTokens < 0) {
            throw new IllegalArgumentException("completionTokens 不能为负数");
        }
    }
}

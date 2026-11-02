package com.mg.llm.result;

/**
 * token 用量：输入 / 输出，均不可为负。
 *
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 * @author LiMingGui98
 * @since 2026-11-02
 */
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

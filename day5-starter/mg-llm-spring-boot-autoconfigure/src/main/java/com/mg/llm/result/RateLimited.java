package com.mg.llm.result;

/**
 * 被限流（HTTP 429）。
 *
 * @param code    状态码 / 错误码
 * @param type    错误类型
 * @param message 错误详情
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record RateLimited(String code, String type, String message) implements LLMResult {
}

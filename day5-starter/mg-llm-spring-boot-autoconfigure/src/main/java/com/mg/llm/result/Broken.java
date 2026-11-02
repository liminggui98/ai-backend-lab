package com.mg.llm.result;

/**
 * 服务方错误（HTTP 非 200 或空内容）。
 *
 * @param code    状态码 / 错误码
 * @param type    错误类型（如 http_error / response / stream）
 * @param message 错误详情
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record Broken(String code, String type, String message) implements LLMResult {
}

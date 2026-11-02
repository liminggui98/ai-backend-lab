package com.mg.llm.result;

/**
 * 客户端侧异常（网络 / IO / 中断）。
 *
 * @param message 异常描述
 * @param e       原始异常
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record ClientException(String message, Exception e) implements LLMResult {
}

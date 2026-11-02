package com.mg.llm.result;

/**
 * 请求超时。
 *
 * @param prompt 触发超时的提问
 * @param cost   耗时
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record TimedOut(String prompt, long cost) implements LLMResult {
}

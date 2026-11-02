package com.mg.llm.result;

/**
 * 一次 LLM 调用结果的可穷尽类型。
 *
 * <p>sealed 保证消费方 switch 时必须覆盖全部实现。
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
public sealed interface LLMResult permits Ok, RateLimited, TimedOut, Broken, ClientException {
}

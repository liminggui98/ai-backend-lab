package com.mg.llm.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * {@link LLMOpenAIClient} 的构造参数。
 *
 * <p>required 字段（provider / model / baseUrl / apiKey）由客户端构造器校验非空白；
 * 可选字段（requestTimeout / connectTimeout / temperature）为 null 时由客户端兜底默认值。
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
@Getter
@Builder
@AllArgsConstructor
public class LLMOpenAIClientOptions {
    private String provider;

    private String model;

    private String baseUrl;

    private String apiKey;

    private Long requestTimeout;

    private Long connectTimeout;

    private Float temperature;
}

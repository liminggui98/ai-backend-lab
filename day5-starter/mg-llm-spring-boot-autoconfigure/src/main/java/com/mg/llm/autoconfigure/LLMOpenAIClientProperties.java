package com.mg.llm.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 {@code llm.*} 配置。record + 构造器绑定：不可变，字段即组件。
 */
@ConfigurationProperties(prefix = "llm")
public record LLMOpenAIClientProperties(
        OpenAIClientConfig deepseek,
        OpenAIClientConfig zhipu,
        OpenAIClientConfig bailian) {

    /**
     * 单个 OpenAI 兼容 provider 的连接配置。
     *
     * @param baseUrl 服务地址
     * @param apiKey  鉴权 key
     * @param model   模型名
     */
    public record OpenAIClientConfig(String baseUrl, String apiKey, String model, Long timeout, Float temperature) {
    }
}

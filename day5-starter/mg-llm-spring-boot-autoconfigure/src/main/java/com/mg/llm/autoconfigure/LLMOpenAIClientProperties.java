package com.mg.llm.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 {@code llm.*} 配置。record + 构造器绑定：不可变，字段即组件。
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
@ConfigurationProperties(prefix = "llm")
public record LLMOpenAIClientProperties(
        OpenAIClientConfig deepseek,
        OpenAIClientConfig zhipu,
        OpenAIClientConfig bailian) {

    /**
     * 单个 OpenAI 兼容 provider 的连接配置。
     *
     * @param baseUrl        服务地址
     * @param apiKey         鉴权 key
     * @param model          模型名
     * @param requestTimeout 请求超时（秒），null 时客户端默认 60
     * @param connectTimeout 建连超时（秒），null 时客户端默认 10
     * @param temperature    采样温度，范围 [0, 2]，null 时客户端默认 0.7
     */
    public record OpenAIClientConfig(String baseUrl, String apiKey, String model,
                                     Long requestTimeout, Long connectTimeout, Float temperature) {
    }
}

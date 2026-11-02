package com.mg.llm.autoconfigure;

import com.mg.llm.client.LLMOpenAIClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 按配置装配 OpenAI 兼容协议的 LLM 客户端。
 *
 * <p>每个 provider 各自一个 {@link LLMOpenAIClient} Bean，只有对应
 * {@code api-key}（bailian 额外要求 {@code base-url}）非空白时才装配；
 * 使用方可用同名 Bean 覆盖默认实现（{@link ConditionalOnMissingBean}）。
 */
@AutoConfiguration
@EnableConfigurationProperties(LLMOpenAIClientProperties.class)
public class LLMOpenAIClientAutoConfiguration {

    @Bean(name = "deepseekClient")
    @ConditionalOnNonBlankProperty("llm.deepseek.api-key")
    @ConditionalOnMissingBean(name = "deepseekClient")
    public LLMOpenAIClient deepseekClient(LLMOpenAIClientProperties properties) {
        return client("deepseek", properties.deepseek());
    }

    @Bean(name = "zhipuClient")
    @ConditionalOnNonBlankProperty("llm.zhipu.api-key")
    @ConditionalOnMissingBean(name = "zhipuClient")
    public LLMOpenAIClient zhipuClient(LLMOpenAIClientProperties properties) {
        return client("zhipu", properties.zhipu());
    }

    @Bean(name = "bailianClient")
    @ConditionalOnNonBlankProperty({"llm.bailian.base-url", "llm.bailian.api-key"})
    @ConditionalOnMissingBean(name = "bailianClient")
    public LLMOpenAIClient bailianClient(LLMOpenAIClientProperties properties) {
        return client("bailian", properties.bailian());
    }

    private static LLMOpenAIClient client(String provider, LLMOpenAIClientProperties.OpenAIClientConfig config) {
        return new LLMOpenAIClient(provider, config.model(), config.baseUrl(), config.apiKey());
    }
}

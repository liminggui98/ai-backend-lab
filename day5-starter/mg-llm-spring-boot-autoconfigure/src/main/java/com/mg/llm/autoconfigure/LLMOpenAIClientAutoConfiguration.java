package com.mg.llm.autoconfigure;

import com.mg.llm.client.LLMOpenAIClient;
import com.mg.llm.client.LLMOpenAIClientOptions;
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
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
@AutoConfiguration
@EnableConfigurationProperties(LLMOpenAIClientProperties.class)
public class LLMOpenAIClientAutoConfiguration {

    /**
     * 装配 DeepSeek 客户端：仅当 {@code llm.deepseek.api-key} 非空白时生效，可被同名 Bean 覆盖。
     */
    @Bean(name = "deepseekClient")
    @ConditionalOnNonBlankProperty("llm.deepseek.api-key")
    @ConditionalOnMissingBean(name = "deepseekClient")
    public LLMOpenAIClient deepseekClient(LLMOpenAIClientProperties properties) {
        return client("deepseek", properties.deepseek());
    }

    /**
     * 装配智谱客户端：仅当 {@code llm.zhipu.api-key} 非空白时生效，可被同名 Bean 覆盖。
     */
    @Bean(name = "zhipuClient")
    @ConditionalOnNonBlankProperty("llm.zhipu.api-key")
    @ConditionalOnMissingBean(name = "zhipuClient")
    public LLMOpenAIClient zhipuClient(LLMOpenAIClientProperties properties) {
        return client("zhipu", properties.zhipu());
    }

    /**
     * 装配百炼客户端：仅当 {@code llm.bailian.base-url} 与 {@code llm.bailian.api-key} 均非空白时生效，可被同名 Bean 覆盖。
     */
    @Bean(name = "bailianClient")
    @ConditionalOnNonBlankProperty({"llm.bailian.base-url", "llm.bailian.api-key"})
    @ConditionalOnMissingBean(name = "bailianClient")
    public LLMOpenAIClient bailianClient(LLMOpenAIClientProperties properties) {
        return client("bailian", properties.bailian());
    }

    private static LLMOpenAIClient client(String provider, LLMOpenAIClientProperties.OpenAIClientConfig config) {
        LLMOpenAIClientOptions llmOpenAIClientOptions = LLMOpenAIClientOptions.builder()
                .provider(provider)
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .model(config.model())
                .requestTimeout(config.requestTimeout())
                .connectTimeout(config.connectTimeout())
                .temperature(config.temperature())
                .build();

        return new LLMOpenAIClient(llmOpenAIClientOptions);
    }
}

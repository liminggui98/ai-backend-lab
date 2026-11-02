package com.mg.llm.autoconfigure;

import com.mg.llm.client.LLMOpenAIClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(LLMOpenAIClientProperties.class)
public class LLMOpenAIClientAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "llm.deepseek.api-key")
    public LLMOpenAIClient deepseekClient(LLMOpenAIClientProperties properties) {
        return new LLMOpenAIClient("deepseek", properties.getDeepseek().getModel(), properties.getDeepseek().getBaseUrl(), properties.getDeepseek().getApiKey());
    }

    @Bean
    @ConditionalOnProperty(name = "llm.zhipu.api-key")
    public LLMOpenAIClient zhipuClient(LLMOpenAIClientProperties properties) {
        return new LLMOpenAIClient("zhipu", properties.getZhipu().getModel(), properties.getZhipu().getBaseUrl(), properties.getZhipu().getApiKey());
    }

    @Bean
    @ConditionalOnProperty(name = "llm.bailian.base-url")
    @ConditionalOnProperty(name = "llm.bailian.api-key")
    public LLMOpenAIClient bailianClient(LLMOpenAIClientProperties properties) {
        return new LLMOpenAIClient("bailian", properties.getBailian().getModel(), properties.getBailian().getBaseUrl(), properties.getBailian().getApiKey());
    }
}

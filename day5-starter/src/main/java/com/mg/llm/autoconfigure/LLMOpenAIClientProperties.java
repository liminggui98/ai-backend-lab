package com.mg.llm.autoconfigure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm")
public class LLMOpenAIClientProperties {
    private OpenAIClientConfig deepseek;

    private OpenAIClientConfig zhipu;

    private OpenAIClientConfig bailian;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIClientConfig {
        String baseUrl;

        String apiKey;

        String model;
    }
}

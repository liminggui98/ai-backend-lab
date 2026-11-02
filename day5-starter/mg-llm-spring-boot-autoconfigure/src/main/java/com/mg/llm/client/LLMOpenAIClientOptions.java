package com.mg.llm.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMOpenAIClientOptions {
    String provider;

    String model;

    String baseUrl;

    String apiKey;

    Long timeout;

    Float temperature;
}

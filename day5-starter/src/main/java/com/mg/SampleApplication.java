package com.mg;

import com.mg.llm.client.ChatCompletionRequest;
import com.mg.llm.client.LLMOpenAIClient;
import com.mg.llm.message.Message;
import com.mg.llm.result.LLMResult;
import lombok.extern.java.Log;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * 可运行的冒烟示例：装配 starter 后，对每个已配置 provider 发一次请求。
 *
 * <p>注意：这只是演示用法，不属于 starter 本身。正式发布的 starter 应把
 * 示例移到独立的 samples 模块，不要在 {@code src/main} 里放 {@code @SpringBootApplication}。
 */
@Log
@SpringBootApplication
public class SampleApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(SampleApplication.class, args);

        ObjectProvider<LLMOpenAIClient> llmOpenAIClientProvider = context.getBeanProvider(LLMOpenAIClient.class);
        llmOpenAIClientProvider.stream()
                .forEach(llmOpenAIClient -> {
                    Message systemMessage = Message.system("你是一个简洁的助手");
                    Message userMessage = Message.user("用一句话介绍你自己");

                    ChatCompletionRequest request = new ChatCompletionRequest(systemMessage, userMessage, false);
                    LLMResult llmResult = llmOpenAIClient.chatCompletion(request);

                    log.info("provider: %s, model: %s, 调用结果：%s".formatted(llmOpenAIClient.getProvider(), llmOpenAIClient.getModel(), llmResult));
                });
    }
}

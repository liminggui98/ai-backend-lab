package com.mg;

import com.alibaba.fastjson2.JSONObject;
import com.mg.llm.client.ChatCompletionRequest;
import com.mg.llm.client.LLMOpenAIClient;
import com.mg.llm.prompt.Message;
import com.mg.llm.result.LLMResult;
import lombok.extern.java.Log;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Log
@SpringBootApplication
public class AutoConfiguration {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(AutoConfiguration.class, args);

        ObjectProvider<LLMOpenAIClient> llmOpenAIClientProvider = context.getBeanProvider(LLMOpenAIClient.class);
        llmOpenAIClientProvider.stream()
                .forEach(llmOpenAIClient -> {
                    Message systemMessage = Message.system("你是一个简洁的助手");
                    Message userMessage = Message.user("用一句话介绍你自己");

                    ChatCompletionRequest request = new ChatCompletionRequest(systemMessage, userMessage, false);
                    LLMResult llmResult = llmOpenAIClient.chatCompletion(request);

                    log.info(JSONObject.toJSONString(llmResult));
                });
    }
}

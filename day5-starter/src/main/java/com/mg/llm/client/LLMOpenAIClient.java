package com.mg.llm.client;

import com.mg.llm.prompt.Message;
import com.mg.llm.result.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客服端
 */
public class LLMOpenAIClient {
    private final HttpClient client;

    private String provider;

    private String model;

    private String baseUrl;

    private String appKey;

    public LLMOpenAIClient(String provider, String model, String baseUrl, String appKey) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.appKey = appKey;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public LLMResult chatCompletion(ChatCompletionRequest chatCompletionRequest) {
        StringBuilder message = new StringBuilder();

        List<Message> messages = List.of(chatCompletionRequest.getSystemMessage(), chatCompletionRequest.getUserMessage());
        messages.forEach(m -> {
            message.append("""
                    "role": %s,
                    "content": %s
                    """.formatted(m.role(), m.content()));
        });

        String prompt = """
                {
                    "model": %s,
                    "message": %s,
                    "stream: %b,
                    "top_n": "0.9",
                    "temperature": "1"
                }
                """.formatted(model, message, chatCompletionRequest.getStream());

        return request(prompt, chatCompletionRequest);
    }

    private LLMResult request(String prompt, ChatCompletionRequest chatCompletionRequest) {
        URI uri;
        try {
            uri = new URI("%s/chat/completion".formatted(baseUrl));
        } catch (URISyntaxException e) {
            return new ClientException("初始化 uri %s/chat/completion 失败".formatted(baseUrl), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + appKey)
                .header("Content-Type", "application/json")
                .header("Accept", chatCompletionRequest.getStream() ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(prompt))
                .timeout(Duration.ofSeconds(10))
                .build();
        if (chatCompletionRequest.getStream()) {
            return streamRequest(prompt, request);
        } else {
            return request(prompt, request);
        }
    }

    private LLMResult streamRequest(String prompt, HttpRequest request) {
        long start = System.nanoTime();
        HttpResponse<Stream<String>> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                return new Broken("", "", prompt);
            }

            Usage usage = null;
            StringBuilder content = new StringBuilder();

            Stream<String> lines = response.body();
            for (String line : (Iterable<String>) lines::iterator) {
                if (line == null || line.isBlank()) {
                    continue;
                }

                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5);
                if (data.contains("[DONE]")) {
                    break;
                }
            }
            return new Ok(prompt, null, null);
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            return new ClientException("请求中断", e);
        }
    }

    private LLMResult request(String prompt, HttpRequest request) {
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new Ok(prompt, "", null);
            }
            return new Ok(prompt, "{}", null);
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            return new ClientException("请求中断", e);
        }
    }
}

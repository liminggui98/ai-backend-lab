package com.mg.llm.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mg.llm.result.Broken;
import com.mg.llm.result.ClientException;
import com.mg.llm.result.LLMResult;
import com.mg.llm.result.Ok;
import com.mg.llm.result.Usage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客户端。
 *
 * <p>统一返回 {@link LLMResult}（成功 {@link Ok} / 服务方错误 {@link Broken} /
 * 客户端异常 {@link ClientException}），调用方据此判断是否可重试。
 *
 * <p>不可变：连接配置在构造时固定，{@link HttpClient} 可安全复用。
 */
public class LLMOpenAIClient {

    private final HttpClient client;

    private final String provider;

    private final String model;

    private final String baseUrl;

    private final String apiKey;

    public LLMOpenAIClient(String provider, String model, String baseUrl, String apiKey) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public LLMResult chatCompletion(ChatCompletionRequest chatCompletionRequest) {
        URI uri;
        try {
            uri = new URI("%s/chat/completions".formatted(baseUrl));
        } catch (URISyntaxException e) {
            return new ClientException("初始化 uri %s/chat/completions 失败".formatted(baseUrl), e);
        }

        String prompt = promptOf(chatCompletionRequest);
        String requestStr = chatCompletionRequest.getCompletionChatReqText(model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", chatCompletionRequest.getStream() ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestStr))
                .timeout(Duration.ofSeconds(10))
                .build();

        if (chatCompletionRequest.getStream()) {
            return streamRequest(prompt, request);
        } else {
            return request(prompt, request);
        }
    }

    /**
     * 取用户实际提问内容，作为 {@link Ok#prompt()} 存进结果；用户消息缺失时回退到系统消息。
     */
    private static String promptOf(ChatCompletionRequest req) {
        if (req.getUserMessage() != null) {
            return req.getUserMessage().content();
        }
        if (req.getSystemMessage() != null) {
            return req.getSystemMessage().content();
        }
        return "";
    }

    /**
     * 非流式：整体返回一个 JSON，直接从 body 里解析 content 与 usage。
     */
    private LLMResult request(String prompt, HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ClientException("请求中断", e);
        }

        if (response.statusCode() != 200) {
            return new Broken(String.valueOf(response.statusCode()), "http_error", response.body());
        }

        JSONObject body = JSONObject.parseObject(response.body());
        String content = extractContent(body);
        if (content.isBlank()) {
            return new Broken("empty_content", "response", response.body());
        }
        return new Ok(prompt, content, extractUsage(body));
    }

    /**
     * 流式：逐行读取 SSE，累加 delta.content，最后一个带 usage 的 chunk 记录 token 用量。
     */
    private LLMResult streamRequest(String prompt, HttpRequest request) {
        HttpResponse<Stream<String>> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ClientException("请求中断", e);
        }

        if (response.statusCode() != 200) {
            return new Broken(String.valueOf(response.statusCode()), "http_error", prompt);
        }

        StringBuilder content = new StringBuilder();
        Usage usage = new Usage(0, 0);

        Iterator<String> lines = response.body().iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                break;
            }
            if (data.isEmpty()) {
                continue;
            }

            JSONObject chunk = JSONObject.parseObject(data);
            content.append(extractDeltaContent(chunk));
            if (chunk.containsKey("usage")) {
                usage = extractUsage(chunk);
            }
        }

        String body = content.toString();
        if (body.isBlank()) {
            return new Broken("empty_content", "stream", prompt);
        }
        return new Ok(prompt, body, usage);
    }

    /**
     * 非流式响应：取 choices[0].message.content。
     */
    private static String extractContent(JSONObject body) {
        JSONArray choices = body.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject first = choices.getJSONObject(0);
        if (first == null) {
            return "";
        }
        JSONObject message = first.getJSONObject("message");
        if (message == null) {
            return "";
        }
        String content = message.getString("content");
        return content == null ? "" : content;
    }

    /**
     * 流式 chunk：取 choices[0].delta.content（本次增量）。
     */
    private static String extractDeltaContent(JSONObject chunk) {
        JSONArray choices = chunk.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject first = choices.getJSONObject(0);
        if (first == null) {
            return "";
        }
        JSONObject delta = first.getJSONObject("delta");
        if (delta == null) {
            return "";
        }
        String content = delta.getString("content");
        return content == null ? "" : content;
    }

    /**
     * 从 usage 对象提取 prompt_tokens / completion_tokens，缺失时按 0 处理。
     */
    private static Usage extractUsage(JSONObject body) {
        JSONObject usage = body.getJSONObject("usage");
        if (usage == null) {
            return new Usage(0, 0);
        }
        return new Usage(usage.getLongValue("prompt_tokens"), usage.getLongValue("completion_tokens"));
    }
}

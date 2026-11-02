package com.mg.llm.client;

import com.alibaba.fastjson2.JSONObject;
import com.mg.llm.result.*;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客服端
 */
@Data
public class LLMOpenAIClient {
    /**
     * 匹配响应中 content 字段的 JSON 字符串值，兼容内部转义字符。
     */
    static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * 匹配响应中 reasoning_content 字段的 JSON 字符串值，兼容内部转义字符。
     */
    static final Pattern REASONING_CONTENT_PATTERN = Pattern.compile("\"reasoning_content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * 匹配 usage 中 prompt_tokens 数值的正则。
     */
    private static final Pattern PROMPT_TOKENS_PATTERN = Pattern.compile("\"prompt_tokens\"\\s*:\\s*(\\d+)");

    /**
     * 匹配 usage 中 completion_tokens 数值的正则。
     */
    private static final Pattern COMPLETION_TOKENS_PATTERN = Pattern.compile("\"completion_tokens\"\\s*:\\s*(\\d+)");

    /**
     * 匹配 usage 中 total_tokens 数值的正则。
     */
    private static final Pattern TOTAL_TOKENS_PATTERN = Pattern.compile("\"total_tokens\"\\s*:\\s*(\\d+)");

    private final HttpClient client;

    private String provider;

    private String model;

    private String baseUrl;

    private String apiKey;

    public LLMOpenAIClient(String provider, String model, String baseUrl, String apiKey) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public LLMResult chatCompletion(ChatCompletionRequest chatCompletionRequest) {
        URI uri;
        try {
            uri = new URI("%s/chat/completion".formatted(baseUrl));
        } catch (URISyntaxException e) {
            return new ClientException("初始化 uri %s/chat/completion 失败".formatted(baseUrl), e);
        }

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
            return streamRequest(requestStr, request);
        } else {
            return request(requestStr, request);
        }
    }


    private LLMResult streamRequest(String prompt, HttpRequest request) {
        long start = System.nanoTime();
        HttpResponse<Stream<String>> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                return new Broken("", "", prompt);
            }

            Usage usage = null;
            StringBuilder content = new StringBuilder();

            Iterator<String> respLinesIter = response.body().iterator();
            while (respLinesIter.hasNext()) {
                String line = respLinesIter.next();
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) {
                    break;
                }


                JSONObject dataObj = JSONObject.parseObject(data);

                usage = extractUsage(data);
            }

            return new Ok(prompt, "{}", new Usage(0, 0));
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            return new ClientException("请求中断", e);
        }
    }

    private LLMResult request(String prompt, HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new Ok(prompt, "{}", new Usage(0, 0));
            }

            Usage usage = new Usage(0, 0);
            return new Ok(prompt, "{}", usage);
        } catch (IOException e) {
            return new ClientException("请求IO异常", e);
        } catch (InterruptedException e) {
            return new ClientException("请求中断", e);
        }
    }

    /**
     * 若该行 SSE 数据包含完整的 usage（含 total_tokens），返回新 usage，否则保留原值。
     *
     * @param data 去掉 data: 前缀的 SSE 数据行
     * @return 更新后的 usage JSON
     */
    private static Usage extractUsage(String data) {
        long promptTokens = 0;
        long completionTokens = 0;

        String perUsage = jsonObjectAfter(data, "usage");
        if (perUsage.contains("total_tokens")) {
            // 提取 prompt_tokens、completion_tokens
            Matcher matcher = PROMPT_TOKENS_PATTERN.matcher(perUsage);
            if (matcher.find()) {
            }
        }
        return new Usage(promptTokens, completionTokens);
    }

    /**
     * 判断一行 SSE 数据是否包含 content 或 reasoning_content 内容字段。
     *
     * @param data 去掉 data: 前缀的 SSE 数据行
     * @return true 表示包含内容字段，可视为首 token 到达
     */
    private static boolean hasContent(String data) {
        return CONTENT_PATTERN.matcher(data).find()
                || REASONING_CONTENT_PATTERN.matcher(data).find();
    }

    /**
     * 从 JSON 文本中提取 key 之后紧跟的 JSON 对象，匹配花括号配对并忽略字符串内的花括号。
     *
     * @param body JSON 文本
     * @param key  要提取对象的字段名
     * @return key 对应的 JSON 对象文本；未找到或括号不匹配时返回提示文案
     */
    static String jsonObjectAfter(String body, String key) {
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) {
            return "(未找到 " + key + ")";
        }

        int start = body.indexOf('{', i);
        if (start < 0) {
            return "(未找到 " + key + " 的对象)";
        }

        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int p = start; p < body.length(); p++) {
            char c = body.charAt(p);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return body.substring(start, p + 1);
            }
        }
        return "(括号不匹配)";
    }
}

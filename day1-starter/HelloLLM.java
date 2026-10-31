import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 不依赖任何框架，直接用 JDK 自带的 HttpClient 调用三家公司的非流式/流式接口。
 * <p>
 * 运行：
 * export DEEPSEEK_API_KEY=sk-xxx DEEPSEEK_MODEL=deepseek-flash DEEPSEEK_BASE_URL=https://api.deepseek.com
 * export BAILIAN_API_KEY=sk-xxx BAILIAN_MODEL=qwen3.7-plus BAILIAN_BASE_URL=https://ws-xxx.cn-xxx.maas.aliyuncs.com/compatible-mode/v1
 * export ZHIPU_API_KEY=sk-xxx ZHIPU_MODEL=glm-4-flash ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4
 * <p>
 * java HelloLLM.java
 */
public class HelloLLM {
    static final Pattern CONTEN_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    static HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record LLMProvider(String providerName, String baseUrl, String model, String apiKey) {
        public LLMProvider {
            if (providerName == null || providerName.isBlank()) {
                throw new IllegalArgumentException("providerName cannot be blank");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model cannot be blank");
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl cannot be blank");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey cannot be blank");
            }
        }
    }

    static List<LLMProvider> LLM_PROVIDERS = new ArrayList<>();

    static {
        LLM_PROVIDERS.add(new LLMProvider("DeepSeek", System.getenv("DEEPSEEK_BASE_URL"), System.getenv("DEEPSEEK_MODEL"), System.getenv("DEEPSEEK_API_KEY")));
        LLM_PROVIDERS.add(new LLMProvider("百炼", System.getenv("BAILIAN_BASE_URL"), System.getenv("BAILIAN_MODEL"), System.getenv("BAILIAN_API_KEY")));
        LLM_PROVIDERS.add(new LLMProvider("智谱", System.getenv("ZHIPU_BASE_URL"), System.getenv("ZHIPU_MODEL"), System.getenv("ZHIPU_API_KEY")));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        for (LLMProvider provider : LLM_PROVIDERS) {
            // 非流式输出
            System.out.printf("""
                    ---模型商：%s，模型：%s，非流式输出---
                    """, provider.providerName(), provider.model());
            call(provider, false);

            // 流式输出
            System.out.printf("""
                    ---模型商：%s，模型：%s，流式输出---
                    """, provider.providerName(), provider.model());
            call(provider, true);
        }
    }

    private static void call(LLMProvider llmProvider, boolean stream) throws IOException, InterruptedException {
        String payload = """
                {
                  "model": "%s",
                  "stream": %b,
                  "max_tokens": %s,
                  "temperature": 0.3,
                  "messages": [
                    {"role": "system", "content": "你是一个简洁的技术助手，回答不超过三句话。"},
                    {"role": "user", "content": "用一句话解释什么是 RAG"}
                  ]
                }
                """.formatted(llmProvider.model(), stream, 1024);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmProvider.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + llmProvider.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        long start = System.nanoTime();
        if (!stream) {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                System.err.println("HTTP " + response.statusCode());
                System.out.println(response.body());
                return;
            }

            String answer = "";
            Matcher matcher = CONTEN_PATTERN.matcher(response.body());
            if (matcher.find()) {
                answer = matcher.group(1);
            }

            System.out.printf("""
                            request: %s
                            payload: %s
                            answer: %s
                            usage: %s
                            total: %.2f s %n
                            """,
                    request,
                    payload,
                    answer,
                    jsonObjectAfter(response.body(), "usage"),
                    (System.nanoTime() - start) / 1e9);
            return;
        }

        HttpResponse<Stream<String>> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            System.err.println("HTTP " + response.statusCode());
            response.body().forEach(System.err::println);
            return;
        }

        double firstTokenTime = -1;
        Pattern content = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        StringBuilder answer = new StringBuilder();
        StringBuilder usage = new StringBuilder();
        for (String line : (Iterable<String>) response.body()::iterator) {
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5)
                    .trim();
            if (data.equals("[DONE]")) {
                break;
            }

            Matcher m = content.matcher(data);
            while (m.find()) {
                String piece = unescape(m.group(1));
                if (piece.isEmpty()) {
                    continue;
                }
                if (firstTokenTime < 0) {
                    firstTokenTime = (System.nanoTime() - start) / 1_000_000.0;
                }
                answer.append(piece);
            }

            String perUsage = jsonObjectAfter(data, "usage");
            if (perUsage.contains("tokens")) {
                usage.append(perUsage);
            }
        }

        System.out.printf("""
                        request: %s
                        payload: %s
                        answer: %s
                        firstToken: %.2f ms
                        usage: %s
                        total: %.2f s %n
                        """,
                request,
                payload,
                answer,
                firstTokenTime,
                usage,
                (System.nanoTime() - start) / 1e9);
    }

    static String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

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

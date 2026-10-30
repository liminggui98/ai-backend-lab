import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Day 1：不依赖任何框架，直接用 JDK 自带的 HttpClient 调一次流式接口。
 * <p>
 * 运行：
 * export DEEPSEEK_API_KEY=sk-xxx
 * java HelloLLM.java
 * <p>
 * 三个 provider 都可以跑，换 LLM_BASE_URL / LLM_MODEL / LLM_API_KEY 即可：
 * DeepSeek : https://api.deepseek.com                     deepseek-chat
 * 通义百炼  : https://dashscope.aliyuncs.com/compatible-mode/v1   qwen-plus
 * 智谱      : https://open.bigmodel.cn/api/paas/v4        glm-4-flash
 */
public class HelloLLM {

    static final String BASE_URL = env("LLM_BASE_URL", "https://api.deepseek.com");
    static final String MODEL = env("LLM_MODEL", "deepseek-chat");
    static final String API_KEY = firstNonBlank(
            System.getenv("LLM_API_KEY"),
            System.getenv("DEEPSEEK_API_KEY"),
            System.getenv("DASHSCOPE_API_KEY"),
            System.getenv("ZHIPU_API_KEY"));
    static final boolean STREAM = !"false".equalsIgnoreCase(env("LLM_STREAM", "true"));
    static final String MAX_TOKENS = env("LLM_MAX_TOKENS", "1024");   // 有默认值，永远有字段

    public static void main(String[] args) throws Exception {
        if (API_KEY == null) {
            System.err.println("没有找到 API Key。先执行： export DEEPSEEK_API_KEY=sk-xxx");
            System.exit(1);
        }

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
                """.formatted(MODEL, STREAM, MAX_TOKENS);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        long start = System.nanoTime();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (!STREAM) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            System.out.println(payload);
            System.out.println("---");
            System.out.println(body);
            System.out.println("---");
            System.out.println("finish_reason = " + grab(body, "\"finish_reason\"\\s*:\\s*\"([^\"]*)\""));
            System.out.println("usage         = " + jsonObjectAfter(body, "usage"));
            System.out.printf("总耗时：%.2f s%n", (System.nanoTime() - start) / 1e9);
            return;
        }

        // 按行读取 SSE；每行形如： data: {"choices":[{"delta":{"content":"你"}}]}
        HttpResponse<Stream<String>> response =
                client.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            System.err.println("HTTP " + response.statusCode());
            response.body().forEach(System.err::println);
            System.exit(2);
        }

        Pattern content = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        StringBuilder answer = new StringBuilder();
        boolean firstToken = true;

        for (String line : (Iterable<String>) response.body()::iterator) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) break;

            Matcher m = content.matcher(data);
            while (m.find()) {
                String piece = unescape(m.group(1));
                if (piece.isEmpty()) continue;   // 首帧只带 role、content 为空，不能算首 token
                if (firstToken) {
                    System.out.printf("首 token 延迟：%.0f ms%n%n", (System.nanoTime() - start) / 1_000_000.0);
                    firstToken = false;
                }
                answer.append(piece);
                System.out.print(piece);
            }
        }

        System.out.printf("%n%n总耗时：%.2f s，输出 %d 字%n",
                (System.nanoTime() - start) / 1e9, answer.length());
    }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    static String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    static String grab(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? unescape(m.group(1)) : "(未找到)";
    }

    static String jsonObjectAfter(String body, String key) {
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) return "(未找到 " + key + ")";
        int start = body.indexOf('{', i);
        if (start < 0) return "(未找到 " + key + " 的对象)";
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int p = start; p < body.length(); p++) {
            char c = body.charAt(p);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
            } else if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return body.substring(start, p + 1);
        }
        return "(括号不匹配)";
    }
}

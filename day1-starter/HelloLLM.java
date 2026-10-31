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
 * export BAILIAN_API_KEY=sk-xxx BAILIAN_MODEL=qwen-plus BAILIAN_BASE_URL=https://ws-xxx.cn-xxx.maas.aliyuncs.com/compatible-mode/v1
 * export ZHIPU_API_KEY=sk-xxx ZHIPU_MODEL=glm-4-flash ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4
 * <p>
 * java HelloLLM.java
 */
public class HelloLLM {
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

    /**
     * 结果表格行格式：provider / model / 是否流式 / 首token / 总耗时 / tokens(p/c/t) / tok/s。
     */
    private static final String RESULT_ROW_FORMAT = "%-9s %-16s %-16s %9s %9s %16s %7s%n";

    public record LLMProvider(String providerName, String baseUrl, String model, String apiKey) {
        public LLMProvider {
            requireNotBlank(providerName, "providerName");
            requireNotBlank(model, "%s model".formatted(providerName));
            requireNotBlank(baseUrl, "%s baseUrl".formatted(providerName));
            requireNotBlank(apiKey, "%s apiKey".formatted(providerName));
        }
    }

    /**
     * 校验字符串非空，为空或仅含空白时抛出 IllegalArgumentException。
     *
     * @param value 待校验的字符串
     * @param field 字段描述，用于异常提示
     */
    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    private static final List<LLMProvider> LLM_PROVIDERS = new ArrayList<>();

    static {
        loadProvider("deepseek", "DeepSeek", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL", "DEEPSEEK_API_KEY");
        loadProvider("zhipu", "智谱", "ZHIPU_BASE_URL", "ZHIPU_MODEL", "ZHIPU_API_KEY");
        loadProvider("bailian", "百炼", "BAILIAN_BASE_URL", "BAILIAN_MODEL", "BAILIAN_API_KEY");

        if (LLM_PROVIDERS.isEmpty()) {
            System.out.println("没有配置任何模型商");
            throw new RuntimeException("没有配置任何模型商，请检查.env文件");
        }
    }

    /**
     * 从环境变量加载单个模型商配置。
     * 配置缺失时仅打印提示并跳过，避免影响其他已配置的模型商。
     *
     * @param name        模型商标识，用于记录与打印
     * @param displayName 模型商中文显示名
     * @param baseUrlEnv  基础地址对应的环境变量名
     * @param modelEnv    模型名对应的环境变量名
     * @param apiKeyEnv   API Key 对应的环境变量名
     */
    private static void loadProvider(String name, String displayName, String baseUrlEnv, String modelEnv, String apiKeyEnv) {
        try {
            LLM_PROVIDERS.add(new LLMProvider(name, System.getenv(baseUrlEnv), System.getenv(modelEnv), System.getenv(apiKeyEnv)));
        } catch (RuntimeException e) {
            System.out.println("加载" + displayName + "配置失败");
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        printHeader();
        // 10 轮压测：每轮流式 + 非流式
        runBenchmark(10);
    }

    /**
     * 按指定轮数执行压测，每轮依次进行流式与非流式调用。
     *
     * @param rounds 压测轮数
     * @throws IOException          网络异常
     * @throws InterruptedException 请求被中断
     */
    private static void runBenchmark(int rounds) throws IOException, InterruptedException {
        for (int i = 0; i < rounds; i++) {
            callAllProviders(true);
            callAllProviders(false);
        }
    }

    /**
     * 以指定方式依次调用所有已配置的模型商。
     *
     * @param stream 是否流式输出
     * @throws IOException          网络异常
     * @throws InterruptedException 请求被中断
     */
    private static void callAllProviders(boolean stream) throws IOException, InterruptedException {
        for (LLMProvider provider : LLM_PROVIDERS) {
            call(provider, stream);
        }
    }

    /**
     * 调用单个模型商，根据 stream 参数选择流式或非流式方式。
     *
     * @param llmProvider 模型商配置
     * @param stream      是否流式输出
     * @throws IOException          网络异常
     * @throws InterruptedException 请求被中断
     */
    private static void call(LLMProvider llmProvider, boolean stream) throws IOException, InterruptedException {
        String payload = buildPayload(llmProvider, stream);
        HttpRequest request = buildRequest(llmProvider, payload, stream);
        HttpClient client = newHttpClient();
        if (stream) {
            callStream(llmProvider, client, request);
        } else {
            callNonStream(llmProvider, client, request);
        }
    }

    /**
     * 创建带连接超时配置的 HTTP 客户端。
     *
     * @return 配置完成的 HttpClient
     */
    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 构建 chat/completions 请求体 JSON。
     *
     * @param provider 模型商配置
     * @param stream   是否流式输出
     * @return 请求体 JSON 字符串
     */
    private static String buildPayload(LLMProvider provider, boolean stream) {
        String streamOptions = stream ? "\"stream_options\": {\"include_usage\": true}," : "";
        return """
                {
                  "model": "%s",
                  "stream": %b,
                  %s
                  "max_tokens": %s,
                  "temperature": 0.3,
                  "messages": [
                    {"role": "system", "content": "你是一个简洁的技术助手，回答不超过三句话。"},
                    {"role": "user", "content": "用一句话解释什么是 RAG"}
                  ]
                }
                """.formatted(provider.model(), stream, streamOptions, 1024);
    }

    /**
     * 构建 chat/completions 的 HTTP 请求。
     *
     * @param provider 模型商配置
     * @param payload  请求体 JSON 字符串
     * @param stream   是否流式输出，决定 Accept 请求头
     * @return 构建完成的 HTTP 请求
     */
    private static HttpRequest buildRequest(LLMProvider provider, String payload, boolean stream) {
        return HttpRequest.newBuilder()
                .uri(URI.create(provider.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + provider.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 非流式调用：一次性解析完整响应中的 usage 并打印。
     *
     * @param provider 模型商配置
     * @param client   HTTP 客户端
     * @param request  构建完成的 HTTP 请求
     * @throws IOException          网络异常
     * @throws InterruptedException 请求被中断
     */
    private static void callNonStream(LLMProvider provider, HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (isHttpError(response.statusCode())) {
            System.out.println(response.body());
            return;
        }
        long totalMs = elapsedMs(start);

        String usage = jsonObjectAfter(response.body(), "usage");
        printResult(provider, false, "-", totalMs + "ms", tokenSummary(usage), "-");
    }

    /**
     * 流式调用：逐行解析 SSE 数据，统计首 token 耗时、总耗时与 token 用量并打印。
     *
     * @param provider 模型商配置
     * @param client   HTTP 客户端
     * @param request  构建完成的 HTTP 请求
     * @throws IOException          网络异常
     * @throws InterruptedException 请求被中断
     */
    private static void callStream(LLMProvider provider, HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
        if (isHttpError(response.statusCode())) {
            response.body().forEach(System.err::println);
            return;
        }

        long firstTokenMs = -1;
        String usage = "{}";
        for (String line : responseLines(response)) {
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                break;
            }

            usage = updateUsage(usage, data);

            if (!hasContent(data)) {
                continue;
            }

            if (firstTokenMs < 0) {
                firstTokenMs = elapsedMs(start);
            }
        }

        long totalMs = elapsedMs(start);
        String completionTokens = tokenValue(COMPLETION_TOKENS_PATTERN, usage);

        printResult(provider, true, firstTokenMs + "ms", totalMs + "ms", tokenSummary(usage),
                tokensPerSecond(completionTokens, firstTokenMs, totalMs));
    }

    /**
     * 计算自 start 时刻起经过的毫秒数。
     *
     * @param start 起始时刻（纳秒）
     * @return 经过的毫秒数
     */
    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * 将流式响应体包装为逐行可遍历的 Iterable。
     *
     * @param response 流式 HTTP 响应
     * @return 逐行 Iterable
     */
    private static Iterable<String> responseLines(HttpResponse<Stream<String>> response) {
        return response.body()::iterator;
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
     * 若该行 SSE 数据包含完整的 usage（含 total_tokens），返回新 usage，否则保留原值。
     *
     * @param current 当前已保存的 usage JSON
     * @param data    去掉 data: 前缀的 SSE 数据行
     * @return 更新后的 usage JSON
     */
    private static String updateUsage(String current, String data) {
        String perUsage = jsonObjectAfter(data, "usage");
        return perUsage.contains("total_tokens") ? perUsage : current;
    }

    /**
     * 判断 HTTP 状态码是否表示请求失败，失败时打印状态码到错误流。
     *
     * @param statusCode HTTP 状态码
     * @return true 表示请求失败，调用方应直接返回
     */
    private static boolean isHttpError(int statusCode) {
        if (statusCode == 200) {
            return false;
        }
        System.err.println("HTTP " + statusCode);
        return true;
    }

    /**
     * 打印结果表格表头（不含 answer 列）。
     */
    private static void printHeader() {
        System.out.printf(RESULT_ROW_FORMAT,
                "provider", "model", "是否流式", "首token", "总耗时", "tokens(p/c/t)", "tok/s");
        System.out.println("-".repeat(88));
    }

    /**
     * 按表格格式打印一行调用结果。
     *
     * @param provider     模型商配置
     * @param stream       是否流式输出
     * @param firstToken   首 token 耗时（毫秒），非流式显示 "-"
     * @param total        总耗时（毫秒）
     * @param tokens       token 用量汇总，格式 "prompt/completion/total"
     * @param tokensPerSec 每秒 token 数（保留一位小数），无法计算时显示 "-"
     */
    private static void printResult(LLMProvider provider, boolean stream, String firstToken,
                                    String total, String tokens, String tokensPerSec) {
        System.out.printf(RESULT_ROW_FORMAT,
                provider.providerName(), provider.model(), stream ? "是" : "否",
                firstToken, total, tokens, tokensPerSec);
    }

    /**
     * 汇总 usage 中的 token 用量，格式为 "prompt/completion/total"，缺失字段以 "-" 占位。
     *
     * @param usage usage JSON 字符串
     * @return token 用量汇总字符串
     */
    private static String tokenSummary(String usage) {
        return tokenValue(PROMPT_TOKENS_PATTERN, usage) + "/"
                + tokenValue(COMPLETION_TOKENS_PATTERN, usage) + "/"
                + tokenValue(TOTAL_TOKENS_PATTERN, usage);
    }

    /**
     * 用指定正则从 usage JSON 中提取 token 数值，未匹配时返回 "-"。
     *
     * @param pattern 匹配 token 字段的正则
     * @param usage   usage JSON 字符串
     * @return token 数值字符串
     */
    private static String tokenValue(Pattern pattern, String usage) {
        Matcher m = pattern.matcher(usage);
        return m.find() ? m.group(1) : "-";
    }

    /**
     * 计算每秒生成的 token 数并格式化为字符串。
     * 首 token 未记录、completion token 缺失或时间跨度为非正数时返回 "-"，避免除零或解析异常。
     *
     * @param completionTokens completion token 数（"-" 表示未知）
     * @param firstTokenMs     首 token 耗时（毫秒，-1 表示未记录）
     * @param totalMs          总耗时（毫秒）
     * @return 每秒 token 数（保留一位小数），无法计算时返回 "-"
     */
    private static String tokensPerSecond(String completionTokens, long firstTokenMs, long totalMs) {
        if (firstTokenMs < 0 || "-".equals(completionTokens)) {
            return "-";
        }
        long spanMs = totalMs - firstTokenMs;
        if (spanMs <= 0) {
            return "-";
        }
        return "%.1f".formatted(Double.parseDouble(completionTokens) * 1000.0 / spanMs);
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

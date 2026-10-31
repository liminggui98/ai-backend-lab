import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Day 3：统一 LLM 客户端（零依赖，只用 JDK 21）。
 *
 * <p>Day 1 的 {@code HelloLLM.java} 只证明了「能调通一次」，这个类要解决的是
 * 「三家能当成一家用」：同一个方法签名，换 provider 只换配置。
 *
 * <h2>要封装掉的四件事</h2>
 * <ol>
 *   <li><b>provider 适配</b>：{@link OpenAiCompatible} 一份实现，靠 baseUrl + model + key 区分三家；
 *       调用方拿到的永远是 {@link Reply}，看不到任何 provider 字段。</li>
 *   <li><b>流式解析统一</b>：三家的 chunk 都归一成 {@link Chunk}（正文 / 推理 / 结束原因），
 *       推理模型的 {@code reasoning_content} 单独一个字段，不和答案混在一起。</li>
 *   <li><b>超时与错误归一</b>：{@code HttpRequest.timeout} 只管到响应头，body 流另设「读空闲超时」
 *       看门狗（Day 1 在百炼上卡过两分钟）；429 / 5xx 退避重试；错误统一抛 {@link IllegalStateException}。</li>
 *   <li><b>usage 记账</b>：每次调用带回 prompt / completion / total token，W6 的成本模型直接吃这份数据。</li>
 * </ol>
 *
 * <h2>为什么不引 JSON 库</h2>
 * <p>W0 还是纯 JDK 阶段，W1 才上 Spring / Jackson。这里自带一个 60 行的最小 JSON 解析器，
 * 只为解析响应；请求体手工拼，够用。等 W1 换成 Spring AI 时，这一层会被替换掉，
 * 但 {@link Chunk} / {@link Reply} 这层抽象应该保留。
 *
 * <h2>运行</h2>
 * <pre>
 *   java LLMClient.java --selftest                 # 离线自检，不联网、不花钱
 *   java LLMClient.java                            # 跑所有已配置的 provider，流式打印
 *   java LLMClient.java --provider deepseek        # 只跑一家
 *   java LLMClient.java --provider all --chat      # 非流式（拿 usage 更稳）
 *   java LLMClient.java --prompt "用一句话解释 RAG"
 * </pre>
 *
 * <h2>环境变量</h2>
 * <p>按 provider 分开命名，避免 Day 1 那个坑——三段 {@code LLM_*} 全开时，后 source 的会静默覆盖前面的。
 * <pre>
 *   DEEPSEEK_API_KEY=sk-xxx                    （可选 DEEPSEEK_BASE_URL / DEEPSEEK_MODEL）
 *   ZHIPU_API_KEY=xxx                          （可选 ZHIPU_BASE_URL / ZHIPU_MODEL）
 *   BAILIAN_API_KEY=sk-ws-xxx                  （必填 BAILIAN_BASE_URL，空间级 key 只能配空间级域名）
 * </pre>
 */
public final class LLMClient {

    // ==================================================================
    // 一、数据模型
    // ==================================================================

    public record Message(String role, String content) {
        public static Message system(String text) { return new Message("system", text); }
        public static Message user(String text)   { return new Message("user", text); }
    }

    /** usage 三件套：输入 / 输出 / 合计。注意输入和输出单价不同，别只看 total。 */
    public record Usage(int promptTokens, int completionTokens, int totalTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0);
        public boolean isEmpty() { return totalTokens == 0; }
        public String shortText() { return promptTokens + "/" + completionTokens + "/" + totalTokens; }
    }

    /** 流式增量帧。reasoning != "" 说明这是推理模型的「思考」阶段，还没到答案。 */
    public record Chunk(String delta, String reasoning, String finishReason) {
        public boolean isEmpty() { return delta.isEmpty() && reasoning.isEmpty(); }
    }

    public record Options(double temperature, int maxTokens, Duration idleTimeout, int maxRetries) {
        public static Options defaults() {
            return new Options(0.3, 1024, Duration.ofSeconds(20), 2);
        }
        public Options temperature(double v)     { return new Options(v, maxTokens, idleTimeout, maxRetries); }
        public Options maxTokens(int v)          { return new Options(temperature, v, idleTimeout, maxRetries); }
        public Options idleTimeout(Duration v)   { return new Options(temperature, maxTokens, v, maxRetries); }
        public Options maxRetries(int v)         { return new Options(temperature, maxTokens, idleTimeout, v); }
    }

    /**
     * 一次调用的结果。firstTokenMs < 0 表示没测到（非流式）。
     * stream 模式下它才是真正的首 token 延迟；chat 模式下等于总耗时。
     */
    public record Reply(String provider, String model, String text, String reasoning,
                        String finishReason, Usage usage, long firstTokenMs, long totalMs) {

        /** 首 token 之后的生成速度，单位 token/s。 */
        public double tokensPerSecond() {
            long span = totalMs - firstTokenMs;
            if (firstTokenMs < 0 || span <= 0 || usage.completionTokens() == 0) return 0;
            return usage.completionTokens() * 1000.0 / span;
        }
    }

    // ==================================================================
    // 二、Provider 抽象
    // ==================================================================

    public interface Provider {
        String name();

        /** 非流式，返回完整答案。usage 最准，适合做成本核算。 */
        Reply chat(List<Message> messages, Options opts);

        /** 流式，逐帧回调。onChunk 可以为 null。 */
        Reply stream(List<Message> messages, Options opts, Consumer<Chunk> onChunk);
    }

    /**
     * 一份实现通吃三家：DeepSeek / 智谱 / 百炼都提供 OpenAI 兼容端点。
     * 差异只在 baseUrl、model、key 三个配置项上——这也正是 Day 1 验证过的结论。
     */
    public static final class OpenAiCompatible implements Provider {

        private final String name;
        private final String baseUrl;
        private final String model;
        private final String apiKey;
        private final HttpClient http;

        public OpenAiCompatible(String name, String baseUrl, String model, String apiKey) {
            if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException(name + " 缺少 API key");
            if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException(name + " 缺少 baseUrl");
            this.name = name;
            this.baseUrl = stripTrailingSlash(baseUrl);
            this.model = model;
            this.apiKey = apiKey;
            this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }

        @Override public String name() { return name; }

        // ---------------- 非流式 ----------------

        @Override
        public Reply chat(List<Message> messages, Options opts) {
            long start = System.nanoTime();
            String body = post(messages, opts);
            long total = elapsedMs(start);

            Map<String, Object> root = asMap(Json.parse(body));
            Map<String, Object> choice = firstChoice(root);
            Map<String, Object> message = asMap(choice.get("message"));

            return new Reply(
                    name,
                    str(root.get("model"), model),
                    str(message.get("content"), ""),
                    str(message.get("reasoning_content"), ""),
                    str(choice.get("finish_reason"), ""),
                    usageOf(root.get("usage")),
                    -1,
                    total);
        }

        // ---------------- 流式 ----------------

        @Override
        public Reply stream(List<Message> messages, Options opts, Consumer<Chunk> onChunk) {
            long start = System.nanoTime();
            HttpRequest request = request(messages, opts, true);

            HttpResponse<Stream<String>> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new RuntimeException("发起流式请求失败（" + name + "）", e);
            }

            if (response.statusCode() != 200) {
                String err = response.body().limit(20).reduce("", (a, b) -> a + b + "\n");
                throw new IllegalStateException("HTTP " + response.statusCode() + " @ " + name + "\n" + err);
            }

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            String finishReason = "";
            String modelName = model;
            Usage usage = Usage.EMPTY;
            long firstTokenMs = -1;

            Stream<String> lines = response.body();
            AtomicLong lastActivity = new AtomicLong(System.nanoTime());
            AtomicBoolean aborted = new AtomicBoolean(false);
            long idleMs = Math.max(1_000, opts.idleTimeout().toMillis());

            // 看门狗：body 流没有超时，长时间收不到数据就主动关流。
            ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "llm-idle-watchdog-" + name);
                t.setDaemon(true);
                return t;
            });
            ScheduledFuture<?> future = watchdog.scheduleAtFixedRate(() -> {
                if (System.nanoTime() - lastActivity.get() > idleMs * 1_000_000L) {
                    aborted.set(true);
                    try {
                        lines.close();
                    } catch (Exception ignored) {
                        // 关流本身失败没什么可做的，异常会从读线程浮出来
                    }
                }
            }, idleMs, 500, TimeUnit.MILLISECONDS);

            try (Stream<String> stream = lines) {
                for (String line : (Iterable<String>) stream::iterator) {
                    lastActivity.set(System.nanoTime());
                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) {
                        if (data.equals("[DONE]")) break;
                        continue;
                    }

                    Map<String, Object> root = asMap(Json.parse(data));
                    modelName = str(root.get("model"), modelName);
                    if (root.get("usage") != null) usage = usageOf(root.get("usage"));

                    Map<String, Object> choice = firstChoice(root);
                    if (choice.isEmpty()) continue;

                    String fr = str(choice.get("finish_reason"), "");
                    if (!fr.isEmpty()) finishReason = fr;

                    Map<String, Object> delta = asMap(choice.get("delta"));
                    String r = str(delta.get("reasoning_content"), "");
                    String d = str(delta.get("content"), "");

                    // 首帧只带 role，content 是空串——用它算首 token 会把延迟算低。
                    if (r.isEmpty() && d.isEmpty()) continue;

                    if (firstTokenMs < 0) firstTokenMs = elapsedMs(start);
                    if (!r.isEmpty()) reasoning.append(r);
                    if (!d.isEmpty()) text.append(d);
                    if (onChunk != null) onChunk.accept(new Chunk(d, r, fr));
                }
            } catch (RuntimeException e) {
                if (aborted.get()) {
                    throw new RuntimeException(
                            "流式读超时：" + idleMs + " ms 没收到任何字节（" + name + "）", e);
                }
                throw e;
            } finally {
                future.cancel(false);
                watchdog.shutdownNow();
            }

            return new Reply(name, modelName, text.toString(), reasoning.toString(),
                    finishReason, usage, firstTokenMs, elapsedMs(start));
        }

        // ---------------- 内部：HTTP ----------------

        private String post(List<Message> messages, Options opts) {
            HttpRequest request = request(messages, opts, false);

            for (int attempt = 0; ; attempt++) {
                try {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) return response.body();

                    boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                    if (retryable && attempt < opts.maxRetries()) {
                        sleep(300L << attempt);
                        continue;
                    }
                    throw new IllegalStateException(
                            "HTTP " + response.statusCode() + " @ " + name + "\n" + response.body());
                } catch (IOException e) {
                    if (attempt < opts.maxRetries()) {
                        sleep(300L << attempt);
                        continue;
                    }
                    throw new RuntimeException("请求失败（" + name + "）", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("请求被中断", e);
                }
            }
        }

        private HttpRequest request(List<Message> messages, Options opts, boolean stream) {
            StringBuilder body = new StringBuilder(256);
            body.append("{\"model\":").append(Json.quote(model));
            body.append(",\"stream\":").append(stream);
            body.append(",\"temperature\":").append(opts.temperature());
            body.append(",\"max_tokens\":").append(opts.maxTokens());
            body.append(",\"messages\":[");
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) body.append(',');
                Message m = messages.get(i);
                body.append("{\"role\":").append(Json.quote(m.role()))
                    .append(",\"content\":").append(Json.quote(m.content())).append('}');
            }
            body.append("]}");

            return HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", stream ? "text/event-stream" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        }
    }

    // ==================================================================
    // 三、配置装配
    // ==================================================================

    /**
     * 从环境变量装配可用的 provider。没配 key 的会被跳过，不会抛异常——
     * 这样本地只配了 DeepSeek 也能直接跑。
     */
    public static List<Provider> fromEnv() {
        List<Provider> providers = new ArrayList<>();

        add(providers, "deepseek",
                env("DEEPSEEK_API_KEY"),
                envOr("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
                envOr("DEEPSEEK_MODEL", "deepseek-chat"));

        add(providers, "zhipu",
                env("ZHIPU_API_KEY"),
                envOr("ZHIPU_BASE_URL", "https://open.bigmodel.cn/api/paas/v4"),
                envOr("ZHIPU_MODEL", "glm-4-flash"));

        // 百炼：空间级 key 必须配空间级域名，混用会 403 AccessDenied.Unpurchased。
        add(providers, "bailian",
                env("BAILIAN_API_KEY"),
                envOr("BAILIAN_BASE_URL", "https://ws-pjpffg7677688nuc.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"),
                envOr("BAILIAN_MODEL", "qwen-plus"));

        return providers;
    }

    private static void add(List<Provider> into, String name, String key, String baseUrl, String model) {
        if (isBlank(key) || isBlank(baseUrl)) return;
        into.add(new OpenAiCompatible(name, baseUrl, model, key));
    }

    // ==================================================================
    // 四、入口
    // ==================================================================

    public static void main(String[] args) {
        if (hasFlag(args, "--selftest")) {
            selftest();
            return;
        }

        String wanted = arg(args, "--provider", "all");
        String prompt = arg(args, "--prompt", "用一句话解释什么是 RAG");
        boolean streaming = !hasFlag(args, "--chat");
        Options opts = Options.defaults()
                .idleTimeout(Duration.ofMillis(Long.parseLong(arg(args, "--idle-ms", "20000"))))
                .maxTokens(Integer.parseInt(arg(args, "--max-tokens", "1024")));

        List<Provider> providers = fromEnv();
        if (providers.isEmpty()) {
            System.err.println("没有可用的 provider。至少配一个：");
            System.err.println("  export DEEPSEEK_API_KEY=sk-xxx");
            System.err.println("  export ZHIPU_API_KEY=xxx");
            System.err.println("  export BAILIAN_API_KEY=sk-ws-xxx BAILIAN_BASE_URL=https://ws-xxx.cn-xxx.maas.aliyuncs.com/compatible-mode/v1");
            System.exit(1);
        }

        List<Message> messages = List.of(
                Message.system("你是一个简洁的技术助手，回答不超过三句话。"),
                Message.user(prompt));

        System.out.printf("%-9s %-16s %9s %9s %16s %7s%n",
                "provider", "model", "首token", "总耗时", "tokens(p/c/t)", "tok/s");
        System.out.println("-".repeat(72));

        for (Provider provider : providers) {
            if (!"all".equals(wanted) && !wanted.equals(provider.name())) continue;

            try {
                Reply reply = streaming
                        ? provider.stream(messages, opts, chunk -> {
                            if (!chunk.delta().isEmpty()) System.out.print(chunk.delta());
                        })
                        : provider.chat(messages, opts);

                if (streaming) System.out.println();

                System.out.printf("%-9s %-16s %9s %9s %16s %7.1f%n",
                        reply.provider(),
                        reply.model(),
                        reply.firstTokenMs() < 0 ? "-" : reply.firstTokenMs() + "ms",
                        reply.totalMs() + "ms",
                        reply.usage().isEmpty() ? "-" : reply.usage().shortText(),
                        reply.tokensPerSecond());
            } catch (RuntimeException e) {
                System.out.printf("%-9s %-16s  ❌ %s%n", provider.name(), "-", e.getMessage().split("\n")[0]);
            }
        }
    }

    // ==================================================================
    // 五、离线自检（不联网、不花钱）
    // ==================================================================

    /**
     * 解析器的自检。这几种帧是 Day 1 真实抓到的：
     * 首帧只有 role、推理模型的 reasoning_content、带 usage 的尾帧、unicode 转义。
     */
    static void selftest() {
        int passed = 0;

        String roleOnly = """
                {"id":"x","model":"deepseek-flash","choices":[{"index":0,\
                "delta":{"role":"assistant","content":""},"finish_reason":null}]}""";
        Map<String, Object> root1 = asMap(Json.parse(roleOnly));
        Map<String, Object> delta1 = asMap(firstChoice(root1).get("delta"));
        check("首帧 content 是空串", "".equals(str(delta1.get("content"), null)));
        check("首帧只有 role", "assistant".equals(str(delta1.get("role"), null)));
        check("空帧应被判定为空", new Chunk("", "", "").isEmpty());
        passed += 3;

        String reasoningFrame = """
                {"choices":[{"index":0,"delta":{"content":"","reasoning_content":"我先想一想"},\
                "finish_reason":null}]}""";
        Map<String, Object> delta2 = asMap(firstChoice(asMap(Json.parse(reasoningFrame))).get("delta"));
        check("推理帧的 reasoning_content 能取到",
                "我先想一想".equals(str(delta2.get("reasoning_content"), null)));
        check("推理帧的 content 是空串", "".equals(str(delta2.get("content"), null)));
        passed += 2;

        String usageFrame = """
                {"choices":[{"index":0,"delta":{"content":"完"},"finish_reason":"stop"}],\
                "usage":{"prompt_tokens":36,"completion_tokens":18,"total_tokens":54}}""";
        Map<String, Object> root3 = asMap(Json.parse(usageFrame));
        Usage usage = usageOf(root3.get("usage"));
        check("usage 解析正确", usage.promptTokens() == 36 && usage.completionTokens() == 18 && usage.totalTokens() == 54);
        check("finish_reason 解析正确", "stop".equals(str(firstChoice(root3).get("finish_reason"), null)));
        passed += 2;

        check("unicode 转义", "你好".equals(Json.parse("\"\\u4f60\\u597d\"")));
        check("顶层数组", Json.parse("[1,2,3]") instanceof List<?> l && l.size() == 3);
        check("小数", Json.parse("1.5") instanceof Double d && d == 1.5);
        check("null", Json.parse("null") == null);
        check("嵌套空对象", Json.parse("{}") instanceof Map<?, ?> m && m.isEmpty());
        check("字符串转义往返", "a\"b\nc".equals(Json.parse(Json.quote("a\"b\nc"))));
        passed += 6;

        check("非流式首 token 标记为 -1", new Reply("p", "m", "", "", "", Usage.EMPTY, -1, 100).firstTokenMs() < 0);
        passed += 1;

        System.out.println("selftest OK（" + passed + " 项断言通过）");
    }

    private static void check(String label, boolean ok) {
        if (!ok) throw new AssertionError("自检失败：" + label);
        System.out.println("  ✓ " + label);
    }

    // ==================================================================
    // 六、最小 JSON（够解析 OpenAI 兼容响应即可）
    // ==================================================================

    static final class Json {

        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            Json j = new Json(text);
            j.skip();
            Object v = j.value();
            j.skip();
            if (j.i < text.length()) throw new IllegalArgumentException("JSON 尾部有多余内容 @" + j.i);
            return v;
        }

        static String quote(String v) {
            StringBuilder b = new StringBuilder(v.length() + 16).append('"');
            for (int k = 0; k < v.length(); k++) {
                char c = v.charAt(k);
                switch (c) {
                    case '"'  -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> {
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                    }
                }
            }
            return b.append('"').toString();
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("JSON 意外结束");
            return s.charAt(i);
        }

        private void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("期望 " + c + " 实际 " + peek() + " @" + i);
            i++;
        }

        private Object value() {
            skip();
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String text, Object value) {
            if (!s.startsWith(text, i)) throw new IllegalArgumentException("非法字面量 @" + i);
            i += text.length();
            return value;
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skip();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skip();
                String key = string();
                skip();
                expect(':');
                map.put(key, value());
                skip();
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw new IllegalArgumentException("期望 , 或 } @" + (i - 1));
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skip();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(value());
                skip();
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw new IllegalArgumentException("期望 , 或 ] @" + (i - 1));
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case '"'  -> b.append('"');
                    case '\\' -> b.append('\\');
                    case '/'  -> b.append('/');
                    case 'b'  -> b.append('\b');
                    case 'f'  -> b.append('\f');
                    case 'n'  -> b.append('\n');
                    case 'r'  -> b.append('\r');
                    case 't'  -> b.append('\t');
                    case 'u'  -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("非法转义 \\" + e);
                }
            }
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String text = s.substring(start, i);
            if (text.isEmpty()) throw new IllegalArgumentException("非法数字 @" + start);
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return Double.parseDouble(text);
            }
            long v = Long.parseLong(text);
            return (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) ? (int) v : v;
        }
    }

    // ==================================================================
    // 七、小工具
    // ==================================================================

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    static Map<String, Object> firstChoice(Map<String, Object> root) {
        Object choices = root.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) return asMap(list.get(0));
        return Map.of();
    }

    static Usage usageOf(Object value) {
        Map<String, Object> map = asMap(value);
        if (map.isEmpty()) return Usage.EMPTY;
        return new Usage(num(map.get("prompt_tokens")), num(map.get("completion_tokens")), num(map.get("total_tokens")));
    }

    static int num(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    static String str(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待重试时被中断", e);
        }
    }

    static String env(String key) { return System.getenv(key); }

    static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return isBlank(v) ? fallback : v;
    }

    static String firstNonBlank(String... values) {
        for (String v : values) if (!isBlank(v)) return v;
        return null;
    }

    static boolean isBlank(String v) { return v == null || v.isBlank(); }

    static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (flag.equals(a)) return true;
        return false;
    }

    static String arg(String[] args, String name, String fallback) {
        for (int k = 0; k < args.length - 1; k++) {
            if (name.equals(args[k])) return args[k + 1];
        }
        return fallback;
    }

    private LLMClient() { }
}

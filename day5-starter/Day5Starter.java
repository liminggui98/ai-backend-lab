import java.util.Optional;

/**
 * 四段小练习（LLM 调用结果四态版）：
 * <ul>
 *   <li>record + 紧凑构造器校验：{@link Usage} / {@code Ok} / {@code RateLimited} / {@code TimedOut} / {@code Broken}</li>
 *   <li>sealed + 穷尽 switch：{@link LlmResult} 四个实现，{@code httpStatus} 不写 default</li>
 *   <li>模式匹配：type pattern / record pattern（含嵌套）/ when 守卫，见 {@code advice}</li>
 *   <li>文本块 prompt：{@code retryPrompt} 带 {@code %s} 占位与 {@code stripTrailing}</li>
 * </ul>
 *
 * <p>边界实验：给 {@code permits} 多加一个实现，{@code httpStatus} 与 {@code advice}
 * 会同时报「switch 表达式不包含所有可能的输入值」。
 */
public class Day5Starter {

    /**
     * 一次 LLM 调用的结果，四种状态互斥。
     *
     * <p>行为（{@link #retryable()}、{@link #content()}、{@link #isSuccess()}）走多态，
     * 调用方不需要 switch；只有「必须穷尽」的边界（HTTP 映射、给用户的建议）才做分派。
     */
    public sealed interface LlmResult permits Ok, RateLimited, TimedOut, Broken {

        /**
         * 是否值得重试。
         *
         * @return 成功态与 4xx 返回 false；可恢复的故障返回 true
         */
        boolean retryable();

        /**
         * 成功时的答案正文。
         *
         * @return 仅成功态返回非空 Optional，失败态统一为空
         */
        default Optional<String> content() {
            return Optional.empty();
        }

        /**
         * 统一成功判定，避免各出口各说各话。
         *
         * @return 是否拿到了非空答案
         */
        default boolean isSuccess() {
            return content().isPresent();
        }

        /**
         * 状态到 HTTP 状态码的穷尽映射。
         *
         * @param r 任意一种调用结果
         * @return 200 / 429 / 504 / 服务方原始状态码（400–599）
         */
        static int httpStatus(LlmResult r) {
            return switch (r) {
                case Ok ignored -> 200;
                case RateLimited ignored -> 429;
                case TimedOut ignored -> 504;                       // 超时是 504；499 是客户端主动断开，不是超时
                // 紧凑构造器已保证 status ∈ [400,599]，边界不必再兜底
                case Broken(var status, var code, var detail) -> status;
            };
        }

        /**
         * 给用户的处置建议：只在这一层做穷尽分派，业务侧仍然只看 {@link #retryable()}。
         *
         * @param r 任意一种调用结果
         * @return 一句中文建议
         */
        static String advice(LlmResult r) {
            return switch (r) {
                // 嵌套 record pattern：一次解到 Ok 与内层 Usage 的组件；when 只做判定，不参与穷尽性
                case Ok(var ignored, Usage(var promptTok, var completionTok), var ms) when ms > 3000 ->
                        "首 token " + ms + "ms 过慢（输入 " + promptTok + " / 输出 " + completionTok + " tok）";
                case Ok ignored -> "调用成功，无需建议";
                // 同一状态两档：带守卫的分支在前，无守卫的分支兜底
                case RateLimited(var after, var provider) when after > 10 ->
                        "限流太久（" + provider + " 要求等 " + after + "s），建议降级到备用模型";
                case RateLimited(var after, var provider) -> "等待 " + after + "s 后重试（" + provider + "）";
                case TimedOut(var waited, var attempts) when attempts >= 3 ->
                        "已超时 " + attempts + " 次，放弃重试（累计等待 " + waited + "s）";
                case TimedOut(var waited, var attempts) -> "超时 " + waited + "s，可再试一次";
                // type pattern + 方法守卫：5xx 走退避重试
                case Broken b when b.retryable() -> "服务方 " + b.status() + "，走退避重试";
                // 无守卫兜底：4xx 直接失败
                case Broken b -> "服务方 " + b.status() + "，不可重试：" + b.detail();
            };
        }

        /**
         * 失败后重新提问的 prompt。
         *
         * @param question 用户原始问题
         * @param failed   上一次失败的结果，其建议会作为失败原因写进 prompt
         * @return 去掉尾部空白的多行 prompt
         */
        static String retryPrompt(String question, LlmResult failed) {
            return """
                    你是客服助手。上一次调用失败了，原因：%s
                    请重新回答下面的问题，只输出答案，不要解释：
                    %s
                    """.formatted(advice(failed), question).stripTrailing();
        }
    }

    /**
     * token 用量。输入与输出单价不同，别只看总数。
     *
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     */
    record Usage(int promptTokens, int completionTokens) {
        Usage {
            if (promptTokens < 0 || completionTokens < 0) {
                throw new IllegalArgumentException("token 不能为负：" + promptTokens + "/" + completionTokens);
            }
        }

        /**
         * @return 输入 + 输出合计
         */
        public int total() {
            return promptTokens + completionTokens;
        }
    }

    /**
     * 调用成功。
     *
     * @param text         答案正文；null 或空白在构造期直接拒绝
     * @param usage        token 用量；null 归一为 0/0
     * @param firstTokenMs 首 token 耗时（毫秒）；&lt; 0 表示未测到（非流式）
     */
    record Ok(String text, Usage usage, long firstTokenMs) implements LlmResult {
        Ok {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text 不能为空");
            }
            if (usage == null) {
                // 归一：下游不用判空
                usage = new Usage(0, 0);
            }
            // 只能改参数，编译器负责写回字段
            text = text.trim();
        }

        @Override
        public boolean retryable() {
            return false;
        }

        @Override
        public Optional<String> content() {
            return Optional.of(text);
        }
    }

    /**
     * 被限流（429）。
     *
     * @param retryAfter 建议等待秒数，不能为负
     * @param provider   哪一家 provider，用于降级决策；构造时 trim 并转小写
     */
    record RateLimited(int retryAfter, String provider) implements LlmResult {
        RateLimited {
            if (retryAfter < 0) {
                throw new IllegalArgumentException("retryAfter 不能为负：" + retryAfter);
            }
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider 必填");
            }
            provider = provider.trim().toLowerCase();
        }

        @Override
        public boolean retryable() {
            // 限流是可恢复的，等一等就能过
            return true;
        }
    }

    /**
     * 超时（504）。
     *
     * @param waited   本次已等待秒数
     * @param attempts 已尝试次数，取值 0..3
     */
    record TimedOut(int waited, int attempts) implements LlmResult {
        TimedOut {
            if (waited < 0) {
                throw new IllegalArgumentException("waited 不能为负：" + waited);
            }
            if (attempts < 0 || attempts > 3) {
                throw new IllegalArgumentException("attempts 必须在 0..3：" + attempts);
            }
        }

        @Override
        public boolean retryable() {
            // 试满 3 次就放弃，别无限重试
            return attempts < 3;
        }
    }

    /**
     * 服务方返回错误（4xx / 5xx）。
     *
     * @param status HTTP 状态码，必须在 400..599
     * @param code   业务错误码
     * @param detail 错误详情，写日志用，不含密钥；null 归一为空串
     */
    record Broken(int status, int code, String detail) implements LlmResult {
        Broken {
            if (status < 400 || status > 599) {
                throw new IllegalArgumentException("status 必须在 400..599：" + status);
            }
            if (detail == null) {
                detail = "";
            }
        }

        @Override
        public boolean retryable() {
            return status >= 500;
        }
    }

    /**
     * 入口：四种状态各铺两条（覆盖守卫的两档），再验证不变量。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        var usage = new Usage(100, 20);

        LlmResult[] samples = {
                new Ok("你好啊", usage, 1000L),
                new Ok("你好啊", usage, 5000L),
                new RateLimited(30, " ZHIPU "),
                new RateLimited(3, "deepseek"),
                new TimedOut(20, 1),
                new TimedOut(20, 3),
                new Broken(503, 5001, "upstream timeout"),
                new Broken(401, 4001, "invalid key")
        };

        for (int i = 0; i < samples.length; i++) {
            LlmResult r = samples[i];
            System.out.printf("%d %-12s http=%-3d retry=%-5b success=%-5b %s%n",
                    i, r.getClass().getSimpleName(), LlmResult.httpStatus(r),
                    r.retryable(), r.isSuccess(), LlmResult.advice(r));
        }

        System.out.printf("%n成功态正文：%s%n%n", samples[0].content().orElse(""));

        System.out.println("---- 失败后重问的 prompt ----");
        System.out.println(LlmResult.retryPrompt("怎么退货", new RateLimited(30, "zhipu")));
        System.out.println();

        System.out.println("---- 不变量：非法构造必须被拒绝 ----");
        expectReject("Ok(\"   \")", () -> new Ok("   ", usage, 0));
        expectReject("Ok(null)", () -> new Ok(null, usage, 0));
        expectReject("TimedOut(attempts=99)", () -> new TimedOut(0, 99));
        expectReject("Broken(status=200)", () -> new Broken(200, 0, ""));
        expectReject("RateLimited(provider=\"\")", () -> new RateLimited(1, " "));
    }

    /**
     * 断言某次构造必须抛 {@link IllegalArgumentException}。
     *
     * @param label 用例名
     * @param ctor  构造动作
     */
    static void expectReject(String label, Runnable ctor) {
        try {
            ctor.run();
            System.out.println("✗ " + label + " 未被拦截");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ " + label + " 被拒绝：" + e.getMessage());
        }
    }
}

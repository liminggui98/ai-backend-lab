# Java 21：新语法与边界

> 本次产出：[`LlmResultExercises.java`](src/main/java/com/mg/LlmResultExercises.java)（287 行，零依赖，`java` 单文件直接跑）。
> 一句话： **record 把不变量收回构造入口，sealed + 无 `default` 的 switch 把穷尽性交给编译器**；新语法省的是维护成本，不是行数。

## 0. 先看这 6 行

| 结论                                                 | 本仓库证据                                                                     |
|------------------------------------------------------|--------------------------------------------------------------------------------|
| record 的价值是构造期校验，不是省 getter             | 5 条非法构造在 `new` 时被拒；null usage 归一为 `0/0`，text 自动 trim           |
| sealed + 不写 `default` = 编译期穷尽检查             | 加第 5 个实现 `Cached`，`httpStatus`、`advice` 两处 switch 同时报错            |
| 写 `default` 等于关掉穷尽性                          | 先写 `default -> "无建议"` 再加状态，编译器一声不吭                            |
| 模式匹配按「数据形状」分派，不按类型强转             | `case Ok(var ignored, Usage(var p, var c), var ms)` 一次解到嵌套              |
| `when` 只做筛选，不参与穷尽性                        | 同一状态两档：守卫分支在前、无守卫兜底在后；顺序反了 dominated 编译失败        |
| record 不是万能                                      | JPA 实体 / 数值语义 / 引用不可变，三个反例见 §6                                |

## 1. 名词表

| 术语                              | 一句话                                                                    | 为什么重要                                              |
|-----------------------------------|---------------------------------------------------------------------------|---------------------------------------------------------|
| LTS                               | 长期支持版本，JDK 21 是 LTS，17 也是                                      | 选版本先看 LTS，Boot 3 基线是 17，21 完全可用           |
| JEP                               | JDK Enhancement Proposal，每个新特性的提案编号                            | 面试说「虚拟线程是 JEP 444」比说「Java 有个新线程」可信 |
| record                            | 透明数据载体：字段、构造器、访问器、`equals`/`hashCode`/`toString` 全自动 | 取代大量 DTO / VO / 返回值样板代码                      |
| 紧凑构造器                        | record 里只写参数、不写赋值的构造器，用于校验与归一                       | 校验放在数据入口，比在 service 里到处判空好             |
| sealed                            | 封闭继承：`permits` 明确列出允许的子类型                                  | 让「只有这几种情况」变成编译期事实，配合 switch 穷尽性  |
| permits                           | sealed 类型后面声明允许的实现列表                                         | 同一文件内可省略，跨文件必须写                          |
| 模式匹配                          | 判断类型的同时完成绑定，不用强转                                          | 消灭 `instanceof` + 强转 + 临时变量三件套               |
| record pattern                    | 按组件把 record 解构出来                                                  | 取嵌套结构（如 `Chunk(delta, ...)`）不用层层调用访问器  |
| when 守卫                         | 模式后面附加布尔条件                                                      | 一个 case 里少写一层 if                                 |
| 穷尽性（exhaustiveness）          | 编译器检查 switch 是否覆盖所有可能                                        | sealed + switch 的核心收益：漏一个分支就编译不过        |
| switch 表达式                     | 有返回值的 switch，箭头不穿透，可 `yield`                                 | 替代「赋值 + break」的老写法                            |
| 文本块                            | 三引号多行字符串，自动去掉公共缩进                                        | 写 prompt、SQL、JSON 样例不用拼 `\n`                    |
| 附带空白（incidental whitespace） | 文本块自动剥离的公共前导空白                                              | 影响 prompt 的最终格式，调试时容易看错                  |
| var                               | 局部变量类型推断，编译期确定                                              | 少写类型，但滥用会降低可读性                            |
| preview                           | 预览特性，可能变、默认要加开关                                            | 生产代码不用；面试可提「知道但不上生产」                |

## 2. 概念精讲

### 1. record：值对象，不是万能 Bean

- 它是 `final` 类，隐式继承 `java.lang.Record`， **不能被继承**，也不能继承别的类（可以实现接口）。
- 组件自动生成：`private final` 字段、同名访问器（`delta()`，不是 `getDelta()`）、`equals`/`hashCode`/`toString`。
  `toString` 直接打出组件名，日志里可读：`OpenAIClientConfig[baseUrl=https://api.deepseek.com, apiKey=, model=deepseek-flash]`。
- 状态只能等于构造参数：不能声明额外的实例字段；可以有静态字段、静态方法、普通方法、实现接口。
- 紧凑构造器用来校验 / 归一（null 转空串、trim、补默认值）， **规范构造器只能有一个**；里面只能改参数，不能写
  `this.x =`，编译器负责写回字段。
- 不适合：JPA 实体（需要无参构造 + 可变的非 final 字段）、需要 setter 的旧框架、需要继承层次的模型。
- JSON 序列化：Jackson 2.12+ 支持 record；Boot 默认带 `-parameters`，一般不用额外注解；字段名对不上时才需要
  `@JsonProperty`。

对比「record vs Lombok `@Data`」：一个靠语言、一个靠注解处理器；record 天生不可变、无 setter、无 builder；`@Data`
可变、有 setter、能继承。

### 2. sealed：把「封闭」写进类型系统

- `sealed interface Shape permits Circle, Square`：子类型必须是 `final`、`sealed` 或 `non-sealed`，否则编译报错。
- 目的不是「禁止继承」这么简单，而是 **让调用方能穷尽所有情况**：`switch` 不写 `default` 也能编译，新增子类型时所有
  switch 立刻报错——这是重构时最值钱的地方。
- 与 `enum` 的区别：enum 的常量是单例，所有常量共享同一组字段；sealed 的每个子类型可以有 **不同的数据**
  （`Circle(double r)`、`Square(double side)`）。需要「有限种情况 + 各自不同的数据」时用 sealed。
- 与 `final` 的区别：`final` 只封一层，sealed 封整棵子树。
- 典型用法：provider 类型、流式事件类型、状态机节点、API 返回的「成功 / 限流 / 超时 / 服务方错误」四态。

本仓库的声明（练习版四态）：

```java
// 只摘分派部分：permits 省略，四个嵌套 record 也省略
public sealed interface LLMResult {
    boolean retryable();
    default Optional<String> content() { return Optional.empty(); }
    default boolean isSuccess() { return content().isPresent(); }

    static int httpStatus(LLMResult r) {
        return switch (r) {
            case Ok ignored -> 200;
            case RateLimited ignored -> 429;
            case TimedOut ignored -> 504;          // 499 是客户端断开，不是超时
            case Broken(var status, var code, var detail) -> status;
        };
    }
}
```

### 3. 模式匹配：type pattern / switch pattern / record pattern

- **type pattern**（Java 16 正式）：`if (o instanceof String s && !s.isEmpty())`，判断即绑定，作用域受流程控制。
- **switch pattern**（Java 21 正式，JEP 441）：case 可以写类型模式、`null` 标签、`when` 守卫； **顺序敏感**，更具体的放前面，
  被子类型覆盖的分支会编译报错（dominance）。
- **record pattern**（Java 21 正式，JEP 440）：`case Chunk(String d, String r, String f)` 直接解构，可嵌套。
- **null 语义**：switch 的选择器为 null 时默认抛 NPE，除非显式写 `case null`。
- **穷尽性**：sealed 类型 + 覆盖所有子类型 = 不用 `default`；非 sealed 类型必须给 `default` 或 `case null, default`。

`advice` 一处同时用了四种写法：

```java
static String advice(LLMResult r) {
    return switch (r) {
        // 嵌套 record pattern：一次解到 Ok 与内层 Usage；when 只判定，不参与穷尽性
        case Ok(var ignored, Usage(var p, var c), var ms) when ms > 3000 ->
                "首 token " + ms + "ms 过慢（输入 " + p + " / 输出 " + c + " tok）";
        case Ok ignored -> "调用成功，无需建议";
        // 同一状态两档：带守卫的在前，无守卫的兜底
        case RateLimited(var after, var provider) when after > 10 ->
                "限流太久（" + provider + " 要求等 " + after + "s），建议降级到备用模型";
        case RateLimited(var after, var provider) -> "等待 " + after + "s 后重试（" + provider + "）";
        case TimedOut(var waited, var attempts) when attempts >= 3 ->
                "已超时 " + attempts + " 次，放弃重试（累计等待 " + waited + "s）";
        case TimedOut(var waited, var attempts) -> "超时 " + waited + "s，可再试一次";
        // type pattern + 方法守卫：5xx 走退避重试
        case Broken b when b.retryable() -> "服务方 " + b.status() + "，走退避重试";
        case Broken b -> "服务方 " + b.status() + "，不可重试：" + b.detail();
    };
}
```

> 注意顺序：`case Ok ignored` 如果写在带 `when` 的 `Ok` 前面，后面那条就 dominated，编译直接失败。守卫分支必须排在
> 无守卫兜底之前。

### 4. switch 表达式 / 文本块 / var

- **switch 表达式**（Java 14 正式）：有返回值、必须穷尽、箭头分支不穿透；块里用 `yield` 返回。和「switch 语句」是两个东西，
  别混着讲。
- **文本块**（Java 15 正式）：开定界符后必须换行；自动去掉公共缩进（附带空白）；行尾 `\` 抑制换行；`\s` 保留行尾空格；
  `formatted` 做占位替换。写 prompt 时特别容易踩「缩进被吃掉」。
- **var**（Java 10）：只能用于局部变量、for、try-with-resources；不能用于字段、方法参数、返回类型；是编译期推断，
  **不是动态类型**。判断标准：变量名能不能替代类型名说清楚含义。

文本块写 prompt 的落点：

```java
static String retryPrompt(String question, LLMResult failed) {
    return """
            你是客服助手。上一次调用失败了，原因：%s
            请重新回答下面的问题，只输出答案，不要解释：
            %s
            """.formatted(advice(failed), question).stripTrailing();
}
```

`"""` 去公共缩进，`%s` 由 `formatted` 填，`stripTrailing()` 去掉最后一个换行，避免 prompt 末尾多一个空行。

### 5. JDK 21 里还有哪些值得知道

| 特性                            | 状态        | 学到什么程度                                    |
|---------------------------------|-------------|-------------------------------------------------|
| 虚拟线程（JEP 444）             | 正式        | 先知道即可                                      |
| 分代 ZGC（JEP 439）             | 正式        | 知道名字即可，后面再深入                        |
| SequencedCollection（JEP 431）  | 正式        | 知道 `getFirst`/`getLast`/`reversed` 即可       |
| 结构化并发（JEP 453）           | **preview** | 知道概念，别上生产                              |
| ScopedValue（JEP 446）          | **preview** | 和 ThreadLocal 对比着理解                       |
| String Templates（JEP 430）     | **preview** | 别用在生产；prompt 拼装先用文本块 + `formatted` |
| 未命名类 / 实例 main（JEP 445） | **preview** | 知道是「简化教学脚本」用的                      |

> 判断标准：`--release 21` 下能编译的是正式特性；要加 `--enable-preview` 的是预览。面试官问「Java 21
> 有什么新特性」，先答正式的那几个，preview 一句带过。

## 3. 本仓库对照：四段练习落在哪

| 练习                 | 落点                                                  | 关键写法                                                                                          | 验证                              |
|----------------------|-------------------------------------------------------|---------------------------------------------------------------------------------------------------|-----------------------------------|
| record + 紧凑构造器  | `Usage` / `Ok` / `RateLimited` / `TimedOut` / `Broken` | 各守一条不变量：text 非空白并 trim、`retryAfter ≥ 0`、`attempts ∈ [0,3]`、`status ∈ [400,599]`、token 非负 | 5 条非法构造被拒（§4.2）          |
| sealed + 穷尽 switch | `LLMResult.httpStatus`、`LLMResult.advice`            | 四个实现，两处都无 `default`                                                                | 加第 5 态两处同时编译失败（§5）   |
| 模式匹配             | `advice`                                              | 嵌套 record pattern + 4 个 `when` + `case Broken b` 类型模式                                      | 8 个样本覆盖守卫两档（§4.1）      |
| 文本块               | `retryPrompt`                                         | `"""` + `%s` + `formatted` + `stripTrailing()`                                                    | 输出见 §4.1 末尾                  |

跑法（项目根目录）：

```bash
java src/main/java/com/mg/LlmResultExercises.java
```

## 4. 实测

> 环境：`java 21.0.13 (Temurin)` / Maven 3.10.0；单文件源码启动，无第三方依赖。

### 4.1 八个样本

每种状态两条，故意覆盖守卫的两档（首 token 1000ms vs 5000ms、`retryAfter` 3s vs 30s、`attempts` 1 vs 3）：

```text
0 Ok           http=200 retry=false success=true  调用成功，无需建议
1 Ok           http=200 retry=false success=true  首 token 5000ms 过慢（输入 100 / 输出 20 tok）
2 RateLimited  http=429 retry=true  success=false 限流太久（zhipu 要求等 30s），建议降级到备用模型
3 RateLimited  http=429 retry=true  success=false 等待 3s 后重试（deepseek）
4 TimedOut     http=504 retry=true  success=false 超时 20s，可再试一次
5 TimedOut     http=504 retry=false success=false 已超时 3 次，放弃重试（累计等待 20s）
6 Broken       http=503 retry=true  success=false 服务方 503，走退避重试
7 Broken       http=401 retry=false success=false 服务方 401，不可重试：invalid key

成功态正文：你好啊

---- 失败后重问的 prompt ----
你是客服助手。上一次调用失败了，原因：限流太久（zhipu 要求等 30s），建议降级到备用模型
请重新回答下面的问题，只输出答案，不要解释：
怎么退货
```

两个细节：

1. `RateLimited(30, " ZHIPU ")` 打印出来是 `zhipu`：紧凑构造器做了 `trim().toLowerCase()`，下游不用再归一。
2. `retryPrompt` 的多行文本里，失败原因和问题各占一行，末尾没有多余空行。

### 4.2 五条不变量

```text
✓ Ok("   ") 被拒绝：text 不能为空
✓ Ok(null) 被拒绝：text 不能为空
✓ TimedOut(attempts=99) 被拒绝：attempts 必须在 0..3：99
✓ Broken(status=200) 被拒绝：status 必须在 400..599：200
✓ RateLimited(provider="") 被拒绝：provider 必填
```

`Ok` 里还有两个「归一」而不是「拒绝」：

```java
record Ok(String text, Usage usage, long firstTokenMs) implements LLMResult {
    Ok {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空");
        if (usage == null) usage = new Usage(0, 0);   // 下游不用判空
        text = text.trim();
    }
}
```

## 5. 边界：加第 5 态，两处 switch 同时报错

在临时副本里加第 5 个实现 `Cached`（与接口同文件，`permits` 可省略），`javac` 直接拒绝：

```text
LlmResultExercises.java:59: 错误: switch 表达式不包含所有可能的输入值
LlmResultExercises.java:76: 错误: switch 表达式不包含所有可能的输入值
2 个错误
```

（行号以当时的副本为准，仓库里的文件没动。）

这比「运行时漏一个分支返回 null」值钱得多：新增状态时，编译器把需要改的地方列给你。反过来，如果 `advice` 里写了
`default -> "无建议"`，加状态时它不会报错—— **`default` 是穷尽性的开关，不是兜底风格**。

## 6. 「record 不合适」的三个真实场景

1. **JPA 实体**：需要无参构造 + 可变的非 final 字段，record 天生给不了；Hibernate 还要可变代理。

2. **数值语义会丢**：之前 JSON 解析器里的 `Json.number()` 特意分了三档——带 `.`/`e` 走 `Double`，整数先试 `Integer`，超范围才 `Long`：

   ```java
   if (text.contains(".") || text.contains("e") || text.contains("E")) return Double.parseDouble(text);
   long v = Long.parseLong(text);
   return (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) ? (int) v : v;
   ```

   换成单组件 `record JsonNum(double)`，`1024` 会变成 `1024.0`，之前特意保留的 `Integer / Long / Double` 三档就丢了。

3. **不可变只到引用层**：`record JsonArr(List<JsonValue> items)` 挡不住外部改 list；必须 `List.copyOf(items)`
   才真不可变。record 只保证「引用不变」，不保证「内容不变」。

## 7. 自测题

| #  | 问题                                            | 回答要点                                                 |
|----|-------------------------------------------------|----------------------------------------------------------|
| 1  | record 和普通类相比，编译器替你生成了什么？     | 字段、访问器、构造器、equals/hashCode/toString；final 类 |
| 2  | record 为什么不能当 JPA 实体？                  | 无无参构造、字段 final、Hibernate 需要可变代理           |
| 3  | sealed 和 enum 都能表达「有限种情况」，怎么选？ | 各情况数据不同 → sealed；共享同一组字段的常量 → enum     |
| 4  | 为什么 sealed 能带来「穷尽性检查」？            | 编译器知道全部子类型，switch 少一个就报错                |
| 5  | switch 模式匹配里，case 的顺序为什么重要？      | 子类型分支会 dominate 父类型分支，顺序错编译失败         |
| 6  | switch 的选择器是 null 会怎样？                 | 默认 NPE；需要显式 `case null`                           |
| 7  | 文本块的缩进是怎么算的？                        | 去掉所有行（含结束定界符）的公共前导空白                 |
| 8  | var 是动态类型吗？                              | 不是，编译期推断，字节码里类型是确定的                   |
| 9  | 紧凑构造器里能不能给字段赋值？                  | 不能写 `this.x =`，参数重新赋值即可，编译器补赋值        |
| 10 | 哪些 21 的特性还是 preview？                    | String Templates、结构化并发、ScopedValue、未命名类等    |

## 8. 一句话记忆卡

1. record = `final` 数据载体；不变量写进紧凑构造器，非法值在 `new` 时就死。
2. sealed = 有限子类型；switch 不写 `default`，漏一个分支编译不过。
3. 写了 `default` 就没有穷尽性——它是开关，不是风格。
4. `when` 只筛选、不参与穷尽性；守卫分支必须排在无守卫兜底之前。
5. record pattern 可以嵌套，一次解到内层 `Usage`。
6. 文本块去公共缩进；prompt 末尾用 `stripTrailing()`，别留空行。
7. var 是编译期推断，不是动态类型；名字说不清含义就别用。
8. preview 特性知道即可，不上生产。
9. record 不适合 JPA、数值语义、深不可变三种场景。
10. 加子类型时编译器报错，是设计提示，不是麻烦。

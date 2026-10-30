# Day 5 · 11/02 周一 · Java 21：新语法与边界

> **状态：已完成**（实际执行 2026-10-30，比计划提前 3 天）　·　实现：[Day5Starter.java](Day5Starter.java)（284 行）　·　提交：`f572595`
>
> W1 第 1 天。今天不碰 AI，只把语言手感找回来。重点不是「会写」，而是**知道每个新语法解决什么问题、什么时候不该用**。
> 本周卡片：[W1.md](../W1.md)　·　下一日：[day6](../day6-starter/)
>
> 本目录**不给实现代码**：四段练习自己写，写完再对照下面的概念检查。

## 今天三段

| 时段 | 做什么 | 完成标志 |
| --- | --- | --- |
| 上午 09:00–12:00 | 读下面 5 组概念；JDK 21 环境与 Maven 指向同一个 JDK | 能解释 record / sealed / 模式匹配各自解决什么问题 |
| 下午 13:30–17:30 | 自己写四段小练习；用 Java 21 重写一段旧代码 | 重写版行为不变、行数下降、没有 Lombok |
| 晚上 19:00–21:30 | 读 Boot 3 参考指南「Auto-configuration」 | 能口述自动配置的加载链路（明天用） |

> 实际执行：13:31–17:07 只做了「四段小练习」这一段（3.5 h）。**「重写旧代码」没做**，挪到 Day 6 上午；「读 Boot 3 自动配置」放在今晚。

## 名词表

| 术语 | 一句话 | 为什么重要 |
| --- | --- | --- |
| LTS | 长期支持版本，JDK 21 是 LTS，17 也是 | 选版本先看 LTS，Boot 3 基线是 17，21 完全可用 |
| JEP | JDK Enhancement Proposal，每个新特性的提案编号 | 面试说「虚拟线程是 JEP 444」比说「Java 有个新线程」可信 |
| record | 透明数据载体：字段、构造器、访问器、`equals`/`hashCode`/`toString` 全自动 | 取代大量 DTO / VO / 返回值样板代码 |
| 紧凑构造器 | record 里只写参数、不写赋值的构造器，用于校验与归一 | 校验放在数据入口，比在 service 里到处判空好 |
| sealed | 封闭继承：`permits` 明确列出允许的子类型 | 让「只有这几种情况」变成编译期事实，配合 switch 穷尽性 |
| permits | sealed 类型后面声明允许的实现列表 | 同一文件内可省略，跨文件必须写 |
| 模式匹配 | 判断类型的同时完成绑定，不用强转 | 消灭 `instanceof` + 强转 + 临时变量三件套 |
| record pattern | 按组件把 record 解构出来 | 取嵌套结构（如 `Chunk(delta, ...)`）不用层层调用访问器 |
| when 守卫 | 模式后面附加布尔条件 | 一个 case 里少写一层 if |
| 穷尽性（exhaustiveness） | 编译器检查 switch 是否覆盖所有可能 | sealed + switch 的核心收益：漏一个分支就编译不过 |
| switch 表达式 | 有返回值的 switch，箭头不穿透，可 `yield` | 替代「赋值 + break」的老写法 |
| 文本块 | 三引号多行字符串，自动去掉公共缩进 | 写 prompt、SQL、JSON 样例不用拼 `\n` |
| 附带空白（incidental whitespace） | 文本块自动剥离的公共前导空白 | 影响 prompt 的最终格式，调试时容易看错 |
| var | 局部变量类型推断，编译期确定 | 少写类型，但滥用会降低可读性 |
| preview | 预览特性，可能变、默认要加开关 | 生产代码不用；面试可提「知道但不上生产」 |

## 概念精讲

### 1. record：值对象，不是万能 Bean

- 它是 `final` 类，隐式继承 `java.lang.Record`，**不能被继承**，也不能继承别的类。
- 组件自动生成：`private final` 字段、同名访问器（`delta()`，不是 `getDelta()`）、`equals`/`hashCode`/`toString`。
- 只能是「状态 = 构造参数」：不能声明额外的实例字段；可以有静态字段、静态方法、普通方法、实现接口。
- 紧凑构造器用来校验/归一（比如 null 转空串），**规范构造器只能有一个**。
- 不适合：JPA 实体（需要无参构造 + 可变的非 final 字段）、需要 setter 的旧框架、需要继承层次的模型。
- JSON 序列化：Jackson 2.12+ 支持 record；Boot 默认带 `-parameters`，一般不用额外注解；字段名对不上时才需要 `@JsonProperty`。

面试对比「record vs Lombok `@Data`」：一个靠语言、一个靠注解处理器；record 天生不可变、无 setter、无 builder；`@Data` 可变、有 setter、能继承。

### 2. sealed：把「封闭」写进类型系统

- `sealed interface Shape permits Circle, Square`：子类型必须是 `final`、`sealed` 或 `non-sealed`，否则编译报错。
- 目的不是「禁止继承」这么简单，而是**让调用方能穷尽所有情况**：`switch` 不写 `default` 也能编译，新增子类型时所有 switch 立刻报错——这是重构时最值钱的地方。
- 与 `enum` 的区别：enum 的常量是单例，所有常量共享同一组字段；sealed 的每个子类型可以有**不同的数据**（`Circle(double r)`、`Square(double side)`）。需要「有限种情况 + 各自不同的数据」时用 sealed。
- 与 `final` 的区别：`final` 只封一层，sealed 封整棵子树。
- 典型用法：provider 类型、流式事件类型、状态机节点、API 返回的「成功/失败/超时」三态。

### 3. 模式匹配：type pattern / switch pattern / record pattern

- **type pattern**（Java 16 正式）：`if (o instanceof String s && !s.isEmpty())`，判断即绑定，作用域受流程控制。
- **switch pattern**（Java 21 正式，JEP 441）：case 可以写类型模式、`null` 标签、`when` 守卫；**顺序敏感**，更具体的放前面，被子类型覆盖的分支会编译报错（dominance）。
- **record pattern**（Java 21 正式，JEP 440）：`case Chunk(String d, String r, String f)` 直接解构，可嵌套。
- **null 语义**：switch 的选择器为 null 时默认抛 NPE，除非显式写 `case null`。这一点和 `switch` 老语句一致，容易被忽略。
- **穷尽性**：sealed 类型 + 覆盖所有子类型 = 不用 `default`；非 sealed 类型必须给 `default` 或 `case null, default`。

### 4. switch 表达式 / 文本块 / var

- **switch 表达式**（Java 14 正式）：有返回值、必须穷尽、箭头分支不穿透；块里用 `yield` 返回。和「switch 语句」是两个东西，别混着讲。
- **文本块**（Java 15 正式）：开定界符后必须换行；自动去掉公共缩进（附带空白）；行尾 `\` 抑制换行；`\s` 保留行尾空格；`formatted` 做占位替换。写 prompt 时特别容易踩「缩进被吃掉」。
- **var**（Java 10）：只能用于局部变量、for、try-with-resources；不能用于字段、方法参数、返回类型；是编译期推断，**不是动态类型**。判断标准：变量名能不能替代类型名说清楚含义。

### 5. JDK 21 里还有哪些值得知道

| 特性 | 状态 | 要不要现在学 |
| --- | --- | --- |
| 虚拟线程（JEP 444） | 正式 | 明天专门学 |
| 分代 ZGC（JEP 439） | 正式 | 知道名字即可，W7 再深入 |
| SequencedCollection（JEP 431） | 正式 | 知道 `getFirst`/`getLast`/`reversed` 即可 |
| 结构化并发（JEP 453） | **preview** | 知道概念，别上生产 |
| ScopedValue（JEP 446） | **preview** | 和 ThreadLocal 对比着理解 |
| String Templates（JEP 430） | **preview** | 别用在生产；prompt 拼装先用文本块 + `formatted` |
| 未命名类 / 实例 main（JEP 445） | **preview** | 知道是「简化教学脚本」用的 |

> 判断标准：`--release 21` 下能编译的是正式特性；要加 `--enable-preview` 的是预览。面试官问「Java 21 有什么新特性」，先答正式的那几个，preview 一句带过。

## 自测题（先自己答，再回看）

| # | 问题 | 回答要点 |
| --- | --- | --- |
| 1 | record 和普通类相比，编译器替你生成了什么？ | 字段、访问器、构造器、equals/hashCode/toString；final 类 |
| 2 | record 为什么不能当 JPA 实体？ | 无无参构造、字段 final、Hibernate 需要可变代理 |
| 3 | sealed 和 enum 都能表达「有限种情况」，怎么选？ | 各情况数据不同 → sealed；共享同一组字段的常量 → enum |
| 4 | 为什么 sealed 能带来「穷尽性检查」？ | 编译器知道全部子类型，switch 少一个就报错 |
| 5 | switch 模式匹配里，case 的顺序为什么重要？ | 子类型分支会 dominate 父类型分支，顺序错编译失败 |
| 6 | switch 的选择器是 null 会怎样？ | 默认 NPE；需要显式 `case null` |
| 7 | 文本块的缩进是怎么算的？ | 去掉所有行（含结束定界符）的公共前导空白 |
| 8 | var 是动态类型吗？ | 不是，编译期推断，字节码里类型是确定的 |
| 9 | 紧凑构造器里能不能给字段赋值？ | 不能写 `this.x =`，参数重新赋值即可，编译器补赋值 |
| 10 | 哪些 21 的特性还是 preview？ | String Templates、结构化并发、ScopedValue、未命名类等 |

## 动手任务（自己写，不给代码）

1. **四段小练习**：record（带紧凑构造器校验）、sealed（三个实现 + 穷尽 switch）、模式匹配（type + record pattern + when）、文本块（一段带占位的 prompt）。
2. **重写一段旧代码**：从 `day3-starter/LLMClient.java` 的 `Chunk` / `Reply`，或旧项目里的 DTO / 枚举状态机里挑一个，复制一份改用 Java 21 写法，**别动原文件**。
3. **验证边界**：给 sealed 多加一个实现，确认所有 switch 立刻编译失败——这是今天最该亲手体验的一次。

验收：

- [x] 四段练习能跑，每段都能说出「不用新语法会多写什么」
- [ ] 重写版行为不变（原文件的自检仍通过），行数下降，没有 Lombok —— **未做**，挪到 Day 6 上午
- [ ] 能举一个「record 不合适」的真实场景，并说清原因 —— 素材已备好（见文末），还没落纸
- [x] JDK 与 Maven 指向同一个 21（`java -version` 与 `mvn -v` 一致）：都是 21.0.13

## 完成标志

- [x] 名词表里每个词都能用自己的话解释，不背原文（`record pattern` / `when` 已能配本项目例子）
- [x] 能画出「sealed + switch 穷尽性」在重构时如何帮你抓漏（加第 5 态实测，两处 switch 同时报错）
- [x] 当天代码已提交（commit message 写「做了什么」，不写 update）—— `f572595`

## 今天不做

- 不学虚拟线程（明天，JEP 444 留到明晚读）
- 不建 Spring Boot 工程（周三）
- 不看 Spring AI 文档（周四，API 变动快，早看早过期）
- 不追 preview 特性（21 的 String Templates 只是 preview，生产不用）

## 记录（当天填）

- 实际用时：上午 **0 h** / 下午 **3.5 h**（13:31 – 17:07，中间两次长时间停顿）/ 晚上 **0 h**
- 卡点（卡了多久、试了什么、怎么过的）：
  1. **把 sealed 当成「枚举的花哨写法」**（约 2 h 白跑）：`Day / Monday / Tuesday` 造了 8 个空 record，各情况没有数据差异 → record pattern 无处解构、`when` 无处挂、紧凑构造器只能写重言式 `"周一".equals(name)`。换掉判据才走通：**先问「各情况的数据是否不同」，不同才用 sealed；只差常量就该用 enum。**
  2. **`default -> "无建议"` 把穷尽性关掉了**（约 20 min 反复）：加第 5 态时只有 `httpStatus` 报错，`advice` 静默通过。删掉 `default` 后，编译器的报错反而成了设计提示——它逐条告诉你「哪些状态还没写分支」。
  3. **校验放晚了**（15 min）：没有紧凑构造器时 `new Ok(null, ...)` 能构造成功，直到 `content()` 里 `Optional.of(null)` 才 NPE，错误从构造期漂到调用期。补上「非空 + 非空白 + trim」后，5 条非法构造全部在入口被拒。
- 一句话结论：**新语法的价值不在「少写几行」，而在「让编译器替你看住不变量（record）和穷尽性（sealed + switch）」；record 只有存在不变量时才值得用。**

## 当天产出

| 产出 | 位置 | 状态 |
| --- | --- | --- |
| 四段练习（四态 `LlmResult`） | `Day5Starter.java`，284 行 | ✅ 可运行，`-Xlint:all` 零告警 |
| 边界实验（加第 5 态 → 编译失败） | 同上；另有 `日常/jdk21-sealed-switch/` 的 WeekDemo + 3 个 compile-fail | ✅ 两处 switch 同时报错 |
| 重写旧代码（Java 21 版） | — | ❌ 未做，挪到 Day 6 |

四段练习的落点：

| 练习 | 落在哪 | 关键写法 |
| --- | --- | --- |
| record + 紧凑构造器 | `Ok` / `RateLimited` / `TimedOut` / `Broken` / `Usage` | 各守一条不变量：文本非空白并 trim、`retryAfter ≥ 0`、`attempts ∈ [0,3]`、`status ∈ [400,599]`、token 非负 |
| sealed + 穷尽 switch | `httpStatus`、`advice` | 两处都无 `default`，`permits` 四态 |
| 模式匹配 | `advice` | 嵌套解构 `Ok(var ignored, Usage(var p, var c), var ms)` + 4 个 `when` 守卫 + `case Broken b` |
| 文本块 | `retryPrompt` | `"""` + `%s` + `formatted` + `stripTrailing()` |

## 实测数据

八个样本（每种状态两条，覆盖守卫两档）：

```text
0 Ok           http=200 retry=false success=true  调用成功，无需建议
1 Ok           http=200 retry=false success=true  首 token 5000ms 过慢（输入 100 / 输出 20 tok）
2 RateLimited  http=429 retry=true  success=false 限流太久（zhipu 要求等 30s），建议降级到备用模型
3 RateLimited  http=429 retry=true  success=false 等待 3s 后重试（deepseek）
4 TimedOut     http=504 retry=true  success=false 超时 20s，可再试一次
5 TimedOut     http=504 retry=false success=false 已超时 3 次，放弃重试（累计等待 20s）
6 Broken       http=503 retry=true  success=false 服务方 503，走退避重试
7 Broken       http=401 retry=false success=false 服务方 401，不可重试：invalid key
```

不变量（5 条非法构造全部被拒）：

```text
✓ Ok("   ") 被拒绝：text 不能为空
✓ Ok(null) 被拒绝：text 不能为空
✓ TimedOut(attempts=99) 被拒绝：attempts 必须在 0..3：99
✓ Broken(status=200) 被拒绝：status 必须在 400..599：200
✓ RateLimited(provider="") 被拒绝：provider 必填
```

边界实验：给 `permits` 加第 5 态 `Cached` 后

```text
Day5Starter.java:57: 错误: switch 表达式不包含所有可能的输入值
Day5Starter.java:73: 错误: switch 表达式不包含所有可能的输入值
```

环境：`java 21.0.13 (Temurin)`、`mvn 3.10.0`，`mvn -v` 报告的 Java 也是 21.0.13。

## 「record 不合适」的三条素材（还没落纸）

1. **JPA 实体**：需要无参构造 + 可变的非 final 字段，record 天生给不了。
2. **数值语义会丢**：`record JsonNum(double)` 把 `1024` 变成 `1024.0` —— day3 的 JSON 解析器特意分了 `Integer / Long / Double` 三档，换成单组件 record 就丢了。
3. **不可变只到引用层**：`record JsonArr(List<JsonValue> items)` 挡不住外部改 list，必须 `List.copyOf(items)` 才真不可变 —— record 只保证「引用不变」，不保证「内容不变」。

## 明天（Day 6）先做这三件

1. 补「重写旧代码」：把 `day3-starter/LLMClient.java` 里 `asMap / num / str` 那串 `instanceof` 改成 `sealed JsonValue` + 模式 switch，用 `java LLMClient.java --selftest` 验行为不变（约 30 min）。
2. 把 `日常/jdk21-sealed-switch/`（WeekDemo + 3 个 compile-fail）挪进仓库，别留在工作空间外面。
3. 上面三条「record 不合适」抄进笔记，W7 面试前要能直接讲。

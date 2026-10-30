# Day 5 · 11/02 周一 · Java 21：新语法与边界

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

- [ ] 四段练习能跑，每段都能说出「不用新语法会多写什么」
- [ ] 重写版行为不变（原文件的自检仍通过），行数下降，没有 Lombok
- [ ] 能举一个「record 不合适」的真实场景，并说清原因
- [ ] JDK 与 Maven 指向同一个 21（`java -version` 与 `mvn -v` 一致）

## 完成标志

- [ ] 名词表里每个词都能用自己的话解释，不背原文
- [ ] 能画出「sealed + switch 穷尽性」在重构时如何帮你抓漏
- [ ] 当天代码已提交（commit message 写「做了什么」，不写 update）

## 今天不做

- 不学虚拟线程（明天，JEP 444 留到明晚读）
- 不建 Spring Boot 工程（周三）
- 不看 Spring AI 文档（周四，API 变动快，早看早过期）
- 不追 preview 特性（21 的 String Templates 只是 preview，生产不用）

## 记录（当天填）

- 实际用时：上午 __ h / 下午 __ h / 晚上 __ h
- 卡点（卡了多久、试了什么、怎么过的）：
- 一句话结论：

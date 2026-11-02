# 学习内容

> 本次交付： **Java 21 四段练习（`LlmResultExercises.java`）+ `mg-llm-spring-boot-starter`（自动配置模块 + 薄壳）**。
>
专题笔记：[Java 21：新语法与边界.md](Java%2021：新语法与边界.md) · [Spring Boot 3：自动配置.md](Spring%20Boot%203：自动配置.md)
>
> 时间：Java 21 部分 2026-10-30；Starter 部分 2026-11-02。

## 0. 目标

> 用 record / sealed / 模式匹配 / 文本块，把 LLM 调用的返回值重写一遍；<br/>
> 把之前的多 provider 客户端重写成 Spring Boot 版，再包成标准 Starter：`.imports` 注册、条件装配、record
> 配置绑定、用户可覆盖；<br/>
> 不碰虚拟线程，不追 preview 特性。

## 1. 交付物

| 文件 / 目录                                                               | 内容                                                                                       |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| [`LlmResultExercises.java`](src/main/java/com/mg/LlmResultExercises.java) | 287 行零依赖练习：四态 `LLMResult` + 两处穷尽 switch + 嵌套 record pattern + 文本块 prompt |
| [`mg-llm-spring-boot-autoconfigure/`](mg-llm-spring-boot-autoconfigure/)  | 自动配置模块：`@AutoConfiguration`、record 配置绑定、自定义条件、`.imports`                |
| [`mg-llm-spring-boot-starter/`](mg-llm-spring-boot-starter/)              | 薄壳模块：只依赖 autoconfigure，使用方引一个坐标即可                                       |
| [`application.properties`](src/main/resources/application.properties)     | 三家 provider 的 `base-url / api-key / model`，`${ENV:default}` 兜底                       |
| [`.env.example`](.env.example)                                            | 环境变量清单（真实 `.env` 不入库）                                                         |
| [`Java 21：新语法与边界.md`](Java%2021：新语法与边界.md)                  | 语言特性、四段练习落点、实测与边界实验                                                     |
| [`Spring Boot 3：自动配置.md`](Spring%20Boot%203：自动配置.md)            | 自动配置链路、条件装配、本仓库对照、坑位                                                   |

## 2. 结构：三个 pom，不是 reactor

```text
项目根/
├── pom.xml                          # 应用模块，依赖 starter:1.0.0；没有 <modules>
├── src/main/java/com/mg/
│   ├── LlmResultExercises.java      # 四段练习，不依赖 Spring
│   └── SampleApplication.java       # 冒烟示例，@SpringBootApplication
├── src/main/resources/
│   ├── application.properties       # llm.* 配置，导入 classpath:.env
│   └── .env                         # 本机 key，gitignore
├── mg-llm-spring-boot-autoconfigure/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/mg/llm/...      # client / result / message / autoconfigure
│       └── resources/META-INF/spring/...AutoConfiguration.imports
└── mg-llm-spring-boot-starter/
    └── pom.xml                      # 只依赖 autoconfigure
```

> 根 `pom.xml` 只声明依赖，没有 `<modules>`，所以不能一条 `mvn install` 全装：
> autoconfigure → starter → 应用，按顺序装，应用才解析得到 `com.mg:*:1.0.0`。

## 3. 运行

### 3.1 四段练习（零依赖）

```bash
java src/main/java/com/mg/LlmResultExercises.java
```

在项目根目录执行，JDK 21 单文件源码启动，不用先编译、不用装依赖。输出见 [4.1](#41-四段练习)。

### 3.2 构建并安装 starter

```bash
mvn -DskipTests -f mg-llm-spring-boot-autoconfigure/pom.xml install
mvn -DskipTests -f mg-llm-spring-boot-starter/pom.xml install
mvn -DskipTests package
```

实测加 `-o` 走本地仓库，三条都 BUILD SUCCESS（Maven 3.10.0 / JDK 21.0.13）。

### 3.3 冒烟（每家一次非流式请求，会花钱）

在 IDE 里跑 `SampleApplication#main`，或：

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" com.mg.SampleApplication
```

`.env` 放 `src/main/resources/.env`（从 `.env.example` 复制后填 key），`application.properties` 用
`spring.config.import=optional:classpath:.env[.properties]` 导入。

> `.env` 会被 `mvn package` 打进应用 jar，本机跑没问题；发布或交出产物前移走，密钥只走环境变量。

> 只想验证装配、不想花钱：把三家 key 传空再启动，预期装配 0 个 client：
> `... --llm.deepseek.api-key= --llm.zhipu.api-key= --llm.bailian.api-key= --llm.bailian.base-url=`

## 4. 实测

### 4.1 四段练习

八个样本，每种状态两条（覆盖守卫的两档）：

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

不变量：5 条非法构造全部在 `new` 时被拒：

```text
✓ Ok("   ") 被拒绝：text 不能为空
✓ Ok(null) 被拒绝：text 不能为空
✓ TimedOut(attempts=99) 被拒绝：attempts 必须在 0..3：99
✓ Broken(status=200) 被拒绝：status 必须在 400..599：200
✓ RateLimited(provider="") 被拒绝：provider 必填
```

### 4.2 自动配置

用临时验证类（未入库）打印 `getBeansOfType(LLMOpenAIClient.class)`：

| 场景                | 启动参数                                    | client Bean | 说明                                                         |
|---------------------|---------------------------------------------|-------------|--------------------------------------------------------------|
| key 全空            | 三家 `--llm.*.api-key=`                     | 0           | 条件不命中；`Properties` 仍绑定，`base-url / model` 走默认值 |
| 三家给假 key        | `--llm.*.api-key=fake`，百炼另给 `base-url` | 3           | `deepseekClient / zhipuClient / bailianClient`               |
| 用户自定义同名 Bean | 同上 + 自定义 `deepseekClient`              | 3           | 自定义的 `provider=user-custom` 接管，另两家仍自动装配       |

空串误命中实验（同一个属性，两个条件）：

```text
flag=[]     OnProperty=true   OnNonBlank=false
flag=[abc]  OnProperty=true   OnNonBlank=true
```

> `@ConditionalOnProperty` 认「属性存在」不认「有值」，`${KEY:}` 的缺省空串照样命中；要非空白只能自己写
> `Condition`。细节与验证代码见 [Spring Boot 3：自动配置.md](Spring%20Boot%203：自动配置.md#4-实测)。

## 5. 边界实验

给 `LLMResult` 加第 5 个实现 `Cached`（在临时副本上做，不动仓库；行号以副本为准）：

```text
LlmResultExercises.java:59: 错误: switch 表达式不包含所有可能的输入值
LlmResultExercises.java:76: 错误: switch 表达式不包含所有可能的输入值
```

`httpStatus` 与 `advice` 两处 switch 同时报错，这就是 sealed 的收益。把 `default` 加回去编译立刻通过——穷尽性也没了，
所以 `advice` 里一个 `default` 都没写。

## 6. 卡在哪

1. **把 sealed 当 enum 用**（约 2 h）：造了 8 个没有数据差异的空 record，record pattern 无处解构、`when` 无处挂。
   判据换成「各情况的数据是否不同」，不同才用 sealed。
2. **`default` 关掉穷尽性**（约 20 min）：当时 `advice` 里有个 `default -> "无建议"`，加第 5 态时只有
   `httpStatus` 报错，`advice` 静默通过；删掉 `default` 后两处一起报错。
3. **校验放晚了**（15 min）：`new Ok(null, ...)` 能构造成功，直到 `content()` 才 NPE；补紧凑构造器后 5 条非法构造
   全部在入口被拒。
4. **`${KEY:}` 空串骗过 `@ConditionalOnProperty`**：环境变量没配也建了 client，请求时才 401。这次写了
   `@ConditionalOnNonBlankProperty`。
5. **`spring.config.import=file:.env` 换目录就失效**：改成 `optional:classpath:.env[.properties]`，打包后也能读到。

## 7. 已知边界

1. 客户端目前只产出 `Ok / Broken / ClientException`；`RateLimited`、`TimedOut` 有类型没接线——429 落到 `Broken`，
   超时落到 `ClientException`。
2. 练习版四态（带 `httpStatus / advice / retryable`）和客户端里的薄 `LLMResult` 还没合并，先保留两份。
3. 自动配置的条件还没有测试，目前只有临时验证类。

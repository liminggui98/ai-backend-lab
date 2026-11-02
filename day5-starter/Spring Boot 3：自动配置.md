# Spring Boot 3：自动配置

> 本次产出：[`mg-llm-spring-boot-autoconfigure/`](mg-llm-spring-boot-autoconfigure/) +
> [`mg-llm-spring-boot-starter/`](mg-llm-spring-boot-starter/)（Boot 3.5.16 / JDK 21）。
> 一句话：自动配置 = 一个 `ImportSelector` 读一行 `.imports`，再由条件决定「装不装、让不让位」；写 starter
> 的难点不在链路，在条件的边界（空串、顺序、同名）。

## 0. 先看这 6 行

| 结论                                                       | 本仓库证据                                                                                     |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Boot 3 只认 `.imports`                                     | `.imports` 文件里就一行全限定类名；`spring.factories` 的 `EnableAutoConfiguration` 键已移除 |
| `@AutoConfiguration` 类不能靠组件扫描                      | 放 `com.mg.llm.autoconfigure` 独立包，只由 `.imports` 注册                                     |
| 条件在 BeanDefinition 阶段判定，不成立就静默不注册         | key 全空 → 0 个 client；三家给假 key → 3 个                                                    |
| 「属性存在 ≠ 属性有值」                                    | 空串上 `@ConditionalOnProperty` 命中、`@ConditionalOnNonBlankProperty` 不命中                   |
| 用户 Bean 优先                                             | 自定义 `deepseekClient` 接管，另两家仍自动装配                                                 |
| 类级无条件的自动配置，在 `--debug` 里是 `Unconditional`    | 三个 `@Bean` 为什么没装，默认报告看不到——这是排错的盲区                                        |

## 1. 名词表

| 术语                                           | 一句话                                                                                                 | 为什么重要                                                                                   |
|------------------------------------------------|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| 自动配置（auto-configuration）                 | 根据 classpath 与配置，替你补上「没自己写」的 Bean                                                     | 说清「Boot 为什么能开箱即用」的起点                                                          |
| `@SpringBootApplication`                       | `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan` 三合一                      | 答案第一句：「它里面就有 `@EnableAutoConfiguration`」                                        |
| `@EnableAutoConfiguration`                     | 通过 `@Import` 引入 `AutoConfigurationImportSelector`                                                  | 自动配置不是魔法，是一个 ImportSelector                                                      |
| `AutoConfigurationImportSelector`              | 读 `.imports` 文件、过滤、排序，把类名交回容器                                                         | 面试问「怎么找到自动配置类」，就答这个类名                                                   |
| `.imports` 文件                                | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，一行一个全限定类名 | Boot 2.7 引入，3.0 起是唯一注册方式；`spring.factories` 里的 `EnableAutoConfiguration` 键已移除 |
| `@AutoConfiguration`                           | 自动配置类专用注解，等价于 `@Configuration(proxyBeanMethods = false)` + 顺序注解                       | 设计上只配合 `.imports` 用，不能被组件扫描扫到                                               |
| `@AutoConfigureBefore` / `@AutoConfigureAfter` | 声明自动配置类之间的先后                                                                               | 有依赖关系的两个 starter，靠它定顺序                                                         |
| `@Conditional`                                 | 条件装配的入口，值是 `Condition` 实现                                                                  | 所有 `@ConditionalOnXxx` 都是它的组合注解                                                    |
| `Condition`                                    | 只实现一个 `matches(ConditionContext, AnnotatedTypeMetadata)`                                          | 写自定义条件就实现它，这次写了「非空白」条件                                                 |
| `ConditionContext`                             | 给条件用的上下文：`Environment`、`BeanFactory`、`ClassLoader`、`ResourceLoader`                        | 条件里拿环境变量的正规入口                                                                   |
| `AnnotatedTypeMetadata`                        | 被注解元素上的注解属性（`getAnnotationAttributes`）                                                    | 自定义条件注解的参数从这儿读，别自己反射注解                                                 |
| `@ConditionalOnClass`                          | classpath 里有某个类才生效                                                                             | 引入 Spring AI 的 starter 时，缺依赖就不装配                                                 |
| `@ConditionalOnMissingBean`                    | 容器里还没有该 Bean 才生效                                                                             | starter「默认实现 + 用户可覆盖」的关键                                                       |
| `@ConditionalOnProperty`                       | 属性存在（且不等于 `false` / 等于 `havingValue`）才生效                                                | 最常用，也最容易在 `${KEY:}` 场景误命中                                                      |
| `@ConditionalOnBean`                           | 容器里已经有某 Bean 才生效                                                                             | 依赖顺序敏感，用错会静默不生效                                                               |
| `@ConfigurationProperties`                     | 把 `prefix` 下的属性绑成对象                                                                           | 配置从「散落的 `@Value`」变成类型安全的一份                                                  |
| 构造器绑定                                     | 用构造器参数接收配置，record 天然适配                                                                  | Boot 3 单构造器不用再写 `@ConstructorBinding`                                                |
| 松散绑定（relaxed binding）                    | `llm.deepseek.api-key` 能绑到 `apiKey`                                                                 | 属性名与字段名不必一模一样                                                                   |
| `@EnableConfigurationProperties`               | 注册并绑定指定的 properties 类                                                                         | 手写 starter 的标准做法，显式、可控                                                          |
| `@ConfigurationPropertiesScan`                 | 扫描包内所有 `@ConfigurationProperties`                                                                | 类多时省手写，但会扩大扫描面                                                                 |
| `spring-boot-configuration-processor`          | 编译期生成 `spring-configuration-metadata.json`                                                        | IDE 里能补全配置项；optional 依赖，不污染使用方                                              |
| `ConditionEvaluationReport`                    | 记录每个条件命不命中                                                                                   | `--debug` 或 `/actuator/conditions` 排错用                                                   |
| 排除自动配置                                   | `@SpringBootApplication(exclude = …)` 或 `spring.autoconfigure.exclude`                                | 业务要接管某个默认实现时的开关                                                               |

## 2. 概念精讲

### 1. 加载链路：一个 selector + 一个文件

1. `@SpringBootApplication` 里有 `@EnableAutoConfiguration`；
2. 它 `@Import(AutoConfigurationImportSelector.class)`；
3. selector 去 classpath 收集所有 `.imports` 文件（一个 jar 一份，可并存）；
4. 按 `@AutoConfigureBefore/After` 排序、去重、跑条件过滤；
5. 剩下的类名当配置类注册进容器。

`spring.factories` 是 2.7 之前的写法；3.0 移除了其中 `EnableAutoConfiguration` 键，自动配置只走 `.imports`。本仓库的 `.imports` 全文只有一行：

```text
com.mg.llm.autoconfigure.LLMOpenAIClientAutoConfiguration
```

### 2. `@AutoConfiguration`：专供 `.imports` 的配置类

- 它比 `@Configuration` 多两件事：`proxyBeanMethods = false`（不生成 CGLIB 代理），以及允许标注
  `@AutoConfigureBefore/After` 来定顺序。
- 官方明确要求：自动配置类不能被 `@ComponentScan` 扫到，必须放在独立包（如 `…autoconfigure`）里，只通过 `.imports`
  注册。被扫到会破坏「用户配置先处理」的顺序假设。
- starter 的目录结构因此是固定的：`src/main/java/<包>/autoconfigure/*AutoConfiguration.java` +
  `src/main/resources/META-INF/spring/*.imports`。

### 3. 条件装配：只在 BeanDefinition 阶段判断

- `Condition.matches` 在解析 BeanDefinition 时执行，拿到的是「已注册的定义」，不是 Bean 实例。条件里不要 `getBean`，只能看
  `Environment`、`BeanFactory` 的类型信息、classpath。
- 条件不成立时，对应的配置类或 Bean 定义不会被注册，也不报错——这正是自动配置「静默不生效」难查的原因。
- 写在同一个方法 / 类上的多个条件之间是「与」关系，全部成立才装配。本仓库百炼的 Bean 就要求 `base-url` 与 `api-key`
  同时非空白。

### 4. `@ConditionalOnProperty` 的空白坑

`@ConditionalOnProperty(name = "x")` 的命中规则是：属性存在且值不是 `"false"`（给了 `havingValue` 则要求相等）。

坑在「存在」：`application.properties` 里写了

```properties
llm.deepseek.api-key=${DEEPSEEK_API_KEY:}
```

即使环境变量没配，属性也是「存在且为空串」，条件照样命中，于是拿着空 key 建了 client，请求时才 401——这是改自定义条件之前的现象。

实测同一个空串属性，两个条件给出相反结果：

```java
@Bean @ConditionalOnProperty(name = "demo.flag") String byOnProperty() { return "hit"; }
@Bean @ConditionalOnNonBlankProperty("demo.flag") String byNonBlank() { return "hit"; }
```

```text
flag=[]     OnProperty=true   OnNonBlank=false
flag=[abc]  OnProperty=true   OnNonBlank=true
```

这解释了为什么要自定义 `@ConditionalOnNonBlankProperty`：

- 注解本身只声明要检查的属性名列表（`String[] value()`）；
- `Condition` 实现里用 `metadata.getAnnotationAttributes(...)` 读列表，再逐个 `Environment.getProperty` 判 `null` /
  `isBlank()`；
- 全部非空白才命中；百炼要求 `base-url` 与 `api-key` 同时非空，正好用上数组。

判断标准：能说清「属性存在 ≠ 属性有值」，就不会再用 `@ConditionalOnProperty` 兜空白。

### 5. 配置绑定：record + 构造器绑定

- `@ConfigurationProperties(prefix = "llm")` 加在 record 上，Boot 3 对「只有一个带参构造器」的类直接走构造器绑定，不需要再写
  `@ConstructorBinding`（该注解只在多构造器时用来指定）。
- 绑定是松散绑定：`llm.deepseek.api-key` → `apiKey`，`base-url` → `baseUrl`；kebab-case 是官方推荐写法。
- 注册方式二选一：`@EnableConfigurationProperties(X.class)`（显式，starter 首选）或 `@ConfigurationPropertiesScan`
  （扫描，应用侧省事）。
- `spring-boot-configuration-processor` 是编译期注解处理器，产出 metadata 给 IDE 补全；声明 `optional`，不要传给使用方。
- 绑定失败与条件求值是两回事：条件只看「属性非空白」，绑定通过也不代表值合法（类型转换失败、缺省值缺失会在启动期抛绑定异常）。

实测 key 全空时，`Properties` 仍然绑定成功，默认值都在：

```text
deepseek=OpenAIClientConfig[baseUrl=https://api.deepseek.com, apiKey=, model=deepseek-flash]
zhipu=OpenAIClientConfig[baseUrl=https://open.bigmodel.cn/api/paas/v4, apiKey=, model=glm-4-flash]
bailian=OpenAIClientConfig[baseUrl=, apiKey=, model=qwen-plus]
```

### 6. 顺序与让位：用户 Bean 为什么优先

- `@EnableAutoConfiguration` 是 deferred import：用户自己的配置类先解析完，自动配置类才处理。
- 于是 `@ConditionalOnMissingBean` 看到的是「用户已经注册的定义」，用户写了同名 Bean，starter 安静退出——这就是
  starter 的默认值机制。
- 反过来的代价：条件只看得见先注册的定义，两个自动配置类之间的 `@ConditionalOnBean` 因此对顺序敏感，必要时用
  `@AutoConfigureAfter` 固定。
- 这次给三个 provider 的 `@Bean` 都加了
  `@ConditionalOnMissingBean(name = "deepseekClient" | "zhipuClient" | "bailianClient")`：同类型 Bean
  有三个，只能按名字让位；使用者若起了同名但不同类型的 Bean，按类型注入会失败——按类型让位更稳，但和「多 provider 同类型」冲突，这是取舍。

实测用户覆盖：

```text
clientBeans=3 names=[deepseekClient, zhipuClient, bailianClient]
deepseekClient -> provider=user-custom
zhipuClient -> provider=zhipu
bailianClient -> provider=bailian
```

## 3. 本仓库对照

三个 pom 不是一个 reactor，安装顺序：autoconfigure → starter → 应用。

| 模块               | 坐标                                             | 职责                                                    |
|--------------------|--------------------------------------------------|---------------------------------------------------------|
| autoconfigure      | `com.mg:mg-llm-spring-boot-autoconfigure:1.0.0`  | 自动配置、属性、条件、OpenAI 兼容客户端                 |
| starter            | `com.mg:mg-llm-spring-boot-starter:1.0.0`        | 薄壳，只依赖 autoconfigure                              |
| 应用（本目录）     | —                                                | 冒烟示例 + 四段练习，不发布                             |

机制落点：

| 机制       | 落点                                                                                                  | 说明                                                                                       |
|------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| 注册入口   | `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 只有一行，写全限定类名                                                                     |
| 自动配置类 | `com.mg.llm.autoconfigure.LLMOpenAIClientAutoConfiguration`                                           | `@AutoConfiguration` + `@EnableConfigurationProperties`，三个 provider 各一个 `@Bean`      |
| 自定义条件 | `ConditionalOnNonBlankProperty` + `OnNonBlankPropertyCondition`                                       | 注解只声明属性名，判定逻辑在 `Condition` 里                                                |
| 配置绑定   | `LLMOpenAIClientProperties`（record，嵌套 `OpenAIClientConfig` record）                               | `prefix = "llm"`，构造器绑定，`pom.xml` 带 `configuration-processor`                       |
| 用户覆盖   | 每个 `@Bean` 上的 `@ConditionalOnMissingBean(name = …)`                                               | 使用者定义同名 Bean 即接管                                                                 |
| 外部配置   | `application.properties` 的 `${ENV:default}` + `spring.config.import=optional:classpath:.env[.properties]` | `.env` 在 `src/main/resources`，按 properties 格式导入、缺失时跳过；`classpath:` 打包后也能读 |
| 冒烟示例   | `com.mg.SampleApplication`（应用模块）                                                                | 不属于 starter 本体；javadoc 已注明正式发布应移到 samples 模块                              |

## 4. 实测

验证类没入库，核心就这几行：

```java
try (var ctx = new SpringApplicationBuilder(SampleApplication.class)
        .web(WebApplicationType.NONE).run(args)) {
    var clients = ctx.getBeansOfType(LLMOpenAIClient.class);
    System.out.println("clientBeans=" + clients.size() + " names=" + clients.keySet());
}
```

三个场景（不调用 `chatCompletion`，不发请求）：

| 场景                | 启动参数                                    | client Bean | 结果                                                         |
|---------------------|---------------------------------------------|-------------|--------------------------------------------------------------|
| key 全空            | 三家 `--llm.*.api-key=`                     | 0           | 三个 `@Bean` 条件全不命中；`Properties` 照常绑定              |
| 三家给假 key        | `--llm.*.api-key=fake`，百炼另给 `base-url` | 3           | `[deepseekClient, zhipuClient, bailianClient]`                |
| 用户自定义同名 Bean | 同上 + 自定义 `deepseekClient`              | 3           | 自定义的 `provider=user-custom`，另两家仍自动装配            |

```text
clientBeans=0 names=[]                                            # 场景 1
clientBeans=3 names=[deepseekClient, zhipuClient, bailianClient]   # 场景 2
```

## 5. 排错：`--debug` 的盲区

启动加 `--debug`，报告里本仓库的自动配置长这样：

```text
Exclusions:
-----------
    None

Unconditional classes:
----------------------
    com.mg.llm.autoconfigure.LLMOpenAIClientAutoConfiguration
```

它在 `Unconditional classes`，不是 Positive matches——因为 **类级没有条件**，条件都挂在三个 `@Bean` 方法上。默认报告按「配置类」
聚合，方法级条件不打印。所以 key 没配时，报告不会告诉你「deepseekClient 为什么没装」，只会让你看到类被导入了。

排查顺序改成：

1. 先确认类有没有被导入（`--debug` 的 Positive matches / Unconditional classes，或 `/actuator/conditions`）；
2. Bean 级条件用 `ApplicationContextRunner`（加 `spring-boot-starter-test`）直接断言 `hasSingleBean` / `doesNotHaveBean`；
3. 临时在 `Condition.matches` 里打一行日志，看实际拿到的属性值；
4. 排除法：`--spring.autoconfigure.exclude=…` 临时关掉某个自动配置，确认问题归属。

`GET /actuator/beans` 看最终注册结果；`/actuator/conditions` 与 `--debug` 是同一份报告。

## 6. 坑位清单

1. 属性存在 ≠ 有值：`${KEY:}` 会给空串，`@ConditionalOnProperty` 照样命中；要「非空白」就自定义条件。
2. 自动配置类进错包：被 `@ComponentScan` 扫到会打乱顺序；放独立包，只留 `.imports` 注册。
3. `src/main` 里放示例：示例类会跟着应用 / starter 的产物发布；正式 starter 把示例移 samples 模块。
4. `@ConditionalOnBean` 对顺序敏感：跨自动配置类依赖时配 `@AutoConfigureAfter`，否则静默不装配。
5. 按名字让位：`@ConditionalOnMissingBean(name=…)` 挡不住「同名不同类型」，按类型注入会失败；多 provider 同类型时这是必要取舍。
6. 条件不校验值合法性：非空白通过后，绑定 / 构造仍可能抛绑定异常或 NPE，校验要另做（如 `@Validated` + JSR-303）。
7. `.env` 会被打进产物：`src/main/resources/.env` 经 `mvn package` 进应用 jar（`target/*.jar`）根目录，本机 demo
   无妨，发布或交出产物前必须移走；密钥只该走环境变量 / 配置中心。
8. `file:.env` 换目录就失效：`spring.config.import=file:.env` 只认工作目录；改成
   `optional:classpath:.env[.properties]` 才能从 classpath 根读到。
9. 三个 pom 不是 reactor：根 `pom.xml` 没有 `<modules>`，`mvn install` 只装应用模块；autoconfigure、starter 要各自先
   `install`，否则应用解析不到 `com.mg:*:1.0.0`。

## 7. 自测题

| #  | 问题                                                                      | 回答要点                                                                         |
|----|---------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| 1  | `@EnableAutoConfiguration` 靠什么找到自动配置类？                         | `AutoConfigurationImportSelector` 读所有 `.imports` 文件                         |
| 2  | Boot 3 里还能用 `spring.factories` 注册自动配置吗？                       | 不能；2.7 引入 `.imports`，3.0 移除旧键，3.x 只认 `.imports`                    |
| 3  | `@AutoConfiguration` 和 `@Configuration` 差在哪？                         | `proxyBeanMethods = false` + 专供 `.imports`，不被组件扫描                       |
| 4  | 为什么自动配置类不能被 `@ComponentScan` 扫到？                            | 破坏「用户配置先注册、自动配置后处理」的顺序，`@ConditionalOnMissingBean` 就失效 |
| 5  | `${KEY:}` 空串为什么会让 `@ConditionalOnProperty` 误命中？                | 条件判的是「属性存在且非 false」，空串也算存在                                   |
| 6  | 自定义条件的参数从哪读？                                                  | `AnnotatedTypeMetadata.getAnnotationAttributes(注解类名)`，取值用 `Environment`  |
| 7  | record 做 `@ConfigurationProperties` 要写 `@ConstructorBinding` 吗？      | 单构造器不用；多构造器时用它指定                                                 |
| 8  | `@EnableConfigurationProperties` 和 `@ConfigurationPropertiesScan` 区别？ | 前者显式注册指定类，后者扫描包内全部；starter 用前者                             |
| 9  | `@ConditionalOnMissingBean` 为什么能实现「用户优先」？                    | 自动配置是 deferred import，用户定义已先注册                                     |
| 10 | 某个自动配置没生效怎么查？                                                | 先看 `--debug` 确认类导入；Bean 级条件用 `ApplicationContextRunner` 断言         |

## 8. 一句话

- 原理：`@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` 读 `.imports` →
  条件过滤 → 注册 BeanDefinition；用户配置先处理，所以 `@ConditionalOnMissingBean` 能让位。
- 写一个 starter：`autoconfigure` 包 + `@AutoConfiguration` + `@EnableConfigurationProperties` + 条件注解 + 一行
  `.imports` + `configuration-processor`。
- 两个真实边界：`${KEY:}` 空串仍算「存在」；类级无条件的自动配置在 `--debug` 里是 Unconditional，Bean 级条件看不到。

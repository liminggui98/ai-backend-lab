# Day 1 学习笔记

> 计划 10/29，实际 10/28 提前做完。今天只做一件事：不开框架，用 JDK 自带的 `HttpClient` 把一次流式调用拆开看清楚。
> 进度：环境 ✅　·　三家 provider ✅　·　10 个问题已列，猜测 0/10

## 一、跑通了什么

| 项 | 结果 | 怎么验证的 |
| --- | --- | --- |
| Docker + pgvector | ✅ pg16，vector 0.8.7 | `select * from healthcheck;` 有 1 行 |
| DeepSeek 流式 | ✅ | `java HelloLLM.java` |
| 智谱流式 | ✅ glm-4-flash | 换 `LLM_BASE_URL` / `LLM_MODEL` |
| 百炼流式 | ✅ qwen3.7-plus（专属空间域名） | 空间级 key 必须配空间级域名 |
| 仓库提交 | ✅ ai-backend-lab | 4 个 commit |

代码在 [HelloLLM.java](HelloLLM.java)：零依赖，只用 JDK 21 的 `HttpClient` + `BodyHandlers.ofLines()`。

## 二、实测数据（2026-10-28，JDK 21，本机）

| provider | 模型 | 首 token | 总耗时 |
| --- | --- | --- | --- |
| DeepSeek | deepseek-chat | 0.36–0.98 s | 1.0–1.3 s |
| 智谱 | glm-4-flash | 1.4–1.6 s | 2.3–3.1 s |
| 百炼 | qwen3.7-plus | 0.6–10.7 s | 3.8–11 s |

两次测量差异就不小，**单次数字不可信，要取 3 次中位数**。这组数就是 W1 参数实验的基线。

两个必须记住的差异：

- `deepseek-chat` 是别名，响应里回来的 `model` 是 `deepseek-flash`。模型名会漂，指标表要记「实际返回的 model」。
- 百炼是推理模型：先流 `reasoning_content`、思考结束才流 `content`。它的「首 token」取决于思考长度，和另外两家不可比。

## 三、今天亲眼确认的 4 个事实

1. 流式每一帧都是 `chat.completion.chunk`，增量在 `choices[0].delta`；非流式的答案在 `choices[0].message.content`。两者结构不同，不能拿同一套解析。
2. 第一帧的 `delta` 只带 `role`，`content` 是空字符串。用它算首 token，延迟会被低估。
3. 同一份请求，换 `LLM_BASE_URL` + `LLM_MODEL` + key 就能切 provider——OpenAI 兼容协议确实能抹平三家差异。
4. 推理模型的 `delta` 里，`reasoning_content` 和 `content` 可以同时出现，只是其中一个是空串。

## 四、十个问题（先写下来，不查；W1 逐个解决）

每一条后面补一句「我猜是……」，猜错没关系——猜过再看答案，记得更牢。

1. 一次请求最少要传哪两个字段？
   - 我猜是：

2. `messages` 里 `system` / `user` / `assistant` 三种角色，分别是谁在说话？
   - 我猜是：

3. 非流式响应里，答案藏在哪个路径？
   - 我猜是：

4. `finish_reason` 有哪几种值？`length` 代表什么？
   - 我猜是：

5. `usage` 的三个数字，哪个决定你付多少钱？
   - 我猜是：

6. 流式响应每行都以 `data: ` 开头，这个格式是谁定的？为什么不是直接返回 JSON 数组？
   - 我猜是：

7. 最后那行 `data: [DONE]` 是模型发出的，还是网关发出的？
   - 我猜是：

8. `temperature` 和 `top_p` 为什么通常只调一个？
   - 我猜是：

9. 同一句话，中文为什么比英文更费 token？
   - 我猜是：

10. 上下文窗口超了会发生什么，报错还是截断？
    - 我猜是：

## 五、读文档的顺序（DeepSeek 接口文档）

<https://api-docs.deepseek.com/zh-cn/api/create-chat-completion>

| 部分     | 看什么                                                                               | 时间   |
| ------ | --------------------------------------------------------------------------------- | ---- |
| 请求体字段表 | `model` / `messages` / `stream` / `temperature` / `top_p` / `max_tokens` / `stop` | 5 分钟 |
| 响应对象   | `choices[].message.content`、`finish_reason`、`usage`                               | 3 分钟 |
| 流式说明   | 为什么每行是 `data: {...}`、最后为什么有 `[DONE]`                                              | 4 分钟 |
| 错误码    | 429 / 401 / 400 分别代表什么                                                            | 3 分钟 |

## 六、今天踩过的坑

- **`.env` 三段 `LLM_*` 全开**：`source .env` 之后最后一段（百炼）生效，以为在测 DeepSeek 其实打的是百炼。要么注释掉没用的两段，要么命令行显式覆盖。
- **body 流没有超时**：`HttpRequest.timeout` 只覆盖到响应头，流式读到一半没数据会一直挂着。百炼那次就卡了两分钟，只能 Ctrl+C。
- **空 `content` 帧**：第一帧 `content` 是 `""`，原来的代码把它算成首 token，延迟偏低（已修）。

## 七、Day 2 衔接

- 上午：读 30 份北京 AI 应用后端 JD，填台账的「JD词频」sheet
- 下午：过一遍 60 家公司池，填「优先级」，选出最想去的 10 家
- 晚上：简历 v1（如实写，先不改）

## 八、今天的一句话

（自己写一句，能讲给别人听的那种）

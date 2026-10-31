# Day 1 学习笔记

> 计划 10/29，实际 10/28 提前做完。今天只做一件事：不开框架，用 JDK 自带的 `HttpClient` 把一次流式调用拆开看清楚。
> 进度：环境 ✅　·　三家 provider ✅　·　10 个问题已列并猜完（10/10），答案见第九节

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
   - 我猜是：用户输入信息、选用模型

2. `messages` 里 `system` / `user` / `assistant` 三种角色，分别是谁在说话？
   - 我猜是：system 设置系统角色，user 是进行输入的用户，assistant 是协助模型进行处理的工具

3. 非流式响应里，答案藏在哪个路径？
   - 我猜是：content

4. `finish_reason` 有哪几种值？`length` 代表什么？
   - 我猜是：finish_reason 具体不知道，length 表示返回的文本长度

5. `usage` 的三个数字，哪个决定你付多少钱？
   - 我猜是：usage 三个数字不清楚，付多少钱主要看消耗了多少 token

6. 流式响应每行都以 `data: ` 开头，这个格式是谁定的？为什么不是直接返回 JSON 数组？
   - 我猜是：请求里字段定的，返回 json 数组消耗 token 过多

7. 最后那行 `data: [DONE]` 是模型发出的，还是网关发出的？
   - 我猜是：网关

8. `temperature` 和 `top_p` 为什么通常只调一个？
   - 我猜是：只会有一个生效

9. 同一句话，中文为什么比英文更费 token？
   - 我猜是：存储方式不同，中文占更多空间

10. 上下文窗口超了会发生什么，报错还是截断？
    - 我猜是：截断

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

- 上午：读 31 份北京 AI 应用后端 JD，填台账的「JD词频」sheet
- 下午：过一遍 60 家公司池，填「优先级」，选出最想去的 10 家
- 晚上：简历 v1（如实写，先不改）

## 八、今天的一句话

今日完成了环境安装及 OpenAI 协议基础阅读，还把 pgvector 和三家模型的流式调用跑通了；

## 九、答案与纠正（2026-10-29）

> 10 条全部猜完：**2 条全对、4 条半对、4 条要改**。错的集中在「协议 / 采样 / 上下文」三类，W1 会逐个撞上。

| # | 判定 | 答案 / 纠正 |
| --- | --- | --- |
| 1 | ✅ | `model` + `messages` 必填；`stream`、`temperature` 等可选 |
| 2 | 🟡 | `system` = 开发者指令 / 人设，`user` = 用户输入，`assistant` = **模型自己的回复**（可含 `tool_calls`）；工具结果走 `tool` 角色 |
| 3 | 🟡 | 非流式完整路径：`choices[0].message.content`（不只是 `content`） |
| 4 | ❌ | 常见值：`stop` / `length` / `content_filter` / `tool_calls`；`length` = **达到 `max_tokens` 被截断**，不是文本长度 |
| 5 | 🟡 | `usage` = `prompt_tokens` + `completion_tokens` + `total_tokens`；输入 / 输出**分别计价**（输出更贵），`total` 只是合计；DeepSeek 输入还分缓存命中 / 未命中 |
| 6 | ❌ | `data: ...` 是 **SSE**（Server-Sent Events，`Content-Type: text/event-stream`）协议，不是请求字段决定的；不用 JSON 数组是为了增量到达、边生成边解析，不是省 token |
| 7 | ✅ | `[DONE]` 是服务端 / 兼容层发出的哨兵，模型不发（严格说不是「网关」这个组件，而是 API 服务端的流式实现） |
| 8 | ❌ | 不是「只有一个生效」：`temperature` 缩放 logits、`top_p` 做核采样截断，**两者都会作用**；叠加后难归因，所以建议只调一个 |
| 9 | 🟡 | 是 BPE tokenizer 对中文切分更碎（1 个汉字常 1–2 token），不是「存储方式」；英文常见词多是一个 token |
| 10 | 🟡 | 两家行为不同：DeepSeek 文档口径是 400 报错；实测智谱 glm-4-flash 返回 200 但 prompt_tokens 恒为 131071（静默截断）。→ 不能靠 provider 报错，必须本地算 token |

### 2026-10-30 用 HelloLLM 非流式验证（#3 / #4 / #5）

- **#3 路径**：非流式答案只在 `choices[0].message.content`，没有 `delta`；`finish_reason=stop`。
- **#4 截断**：`max_tokens=5` → HTTP 200 + `finish_reason=length` + `content="RAG（检索增强"`（半句），`completion_tokens=5`。
  与「上下文超限」的区别：后者是 **400 报错**，前者是**正常返回被截断**。
- **#5 计费**：`prompt=22 / completion=40 / total=62`，`prompt_cache_hit_tokens=0`。
  高峰 `(22×2 + 40×8)/1e6 = ¥0.000364`，千次 ¥0.364；空闲 ¥0.000182。
  输出占这次费用的 **88%**（320/364）。

**两个新发现**：
1. 同一 prompt 连跑两次 `cached_tokens` 都是 0 —— 缓存按 64 token 一块对齐，22 token 未达粒度。
   → 修正 Day 3 结论：前缀要**够长（≥64 token）**才进缓存，不是「固定前缀就一定命中」。
2. 用正则 `[^}]*` 抠 usage 会在 `prompt_tokens_details` 的内层 `}` 处截断 ——
   改用括号配平的 `jsonObjectAfter()`。这是 Day 3 `LLMClient` 要写真正解析器的原因。

### 一句话记住

`finish_reason=length` 是「被 max_tokens 截断」，上下文超限是「400 报错」，SSE 的 `[DONE]` 是服务端哨兵——三个都跟「结束」有关，但不是一个东西。

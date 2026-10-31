# 学习笔记

## 一、跑通了什么

| 项                | 结果                            | 怎么验证的                                                                                    |
|-------------------|---------------------------------|-----------------------------------------------------------------------------------------------|
| Docker + pgvector | ✅ pg16，vector 0.8.7           | `select * from healthcheck;` 有 1 行                                                          |
| DeepSeek 流式     | ✅ deepseek-flash               | 注入环境变量 `DEEPSEEK_API_KEY`，<br/>运行 [HelloLLM.java](HelloLLM.java)                     |
| 智谱流式          | ✅ glm-4-flash                  | 注入 `ZHIPU_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`，<br/>运行 [HelloLLM.java](HelloLLM.java)   |
| 百炼流式          | ✅ qwen3.7-plus（专属空间域名） | 注入 `BAILIAN_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`，<br/>运行 [HelloLLM.java](HelloLLM.java) |
| 仓库提交          | ✅ ai-backend-lab               | github `ai-backend-lab` 有代码，可访问                                                        |

代码在 [HelloLLM.java](HelloLLM.java)：零依赖，只用 JDK 21 的 `HttpClient` + `BodyHandlers.ofLines()`。

百炼只支持空间域名访问。

## 二、实测数据

环境参考 [环境清单.md](环境清单.md)

| provider | 模型           | 首 token    | 总耗时    |
|----------|----------------|-------------|-----------|
| DeepSeek | deepseek-flash | 0.36–0.98 s | 1.0–1.3 s |
| 智谱     | glm-4-flash    | 1.4–1.6 s   | 2.3–3.1 s |
| 百炼     | qwen3.7-plus   | 0.6–10.7 s  | 3.8–11 s  |

差异：

- 百炼是推理模型：先流 `reasoning_content`、思考结束才流 `content`。它的「首 token」取决于思考长度，和另外两家不可比。

## 三、今天亲眼确认的 3 个事实

1. 流式每一帧都是 `chat.completion.chunk`，增量在 `choices[0].delta`；非流式的答案在 `choices[0].message.content`
   。两者结构不同，不能拿同一套解析。
2. 第一帧的 `delta` 只带 `role`，`content` 是空字符串。用它算首 token，延迟会被低估。
3. 推理模型的 `delta` 里，`reasoning_content` 和 `content` 可以同时出现，只是其中一个是空串。

## 四、OpenAI 兼容协议

[OpenAI兼容协议.md](OpenAI兼容协议.md)

## 五、十个问题

### 1. 一次请求最少要传哪两个字段？

字段名是 `model` 与 `messages`。除此之外的字段全都有默认值，可以一个都不传。

最小可用请求体：

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ]
}
```

三个容易踩的边界：

| 约束                         | 说明                                                        |
|------------------------------|-------------------------------------------------------------|
| `messages` 不能为空数组      | 至少 1 条；空数组通常返回 400                               |
| 每条消息必须有 `role`        | `content` 视角色而定，`assistant` 在纯工具调用时可为 `null` |
| `model` 是"模型 ID / 部署名" | Azure 走 `deployments/{deployment}`，`model` 字段可被忽略   |

> 常见误区：以为 `temperature`、`max_tokens`、`stream` 是必填。它们都有默认值（`1`、`null`、`false`），只是生产环境通常显式指定以便复现。

---

### 2. `system` / `user` / `assistant` 分别是谁在说话？

**先记住一个前提**：`messages` 里的每一条 **都是客户端发上去的**。服务端不保存会话，role
描述的是"这段话的署名与责任方"，不是运行时的系统组件。

| 角色        | 谁写的                           | 作用                                    | 常见误解                              |
|-------------|----------------------------------|-----------------------------------------|---------------------------------------|
| `system`    | 应用开发者                       | 设定人设、规则、边界；优先级高于 `user` | 不是"操作系统角色"，也不是运行时开关  |
| `user`      | 终端用户                         | 本轮输入，可带图片/音频/文件            | —                                     |
| `assistant` | **模型自己**（由客户端回填历史） | 表示"这是模型此前说过的话"              | ❌ 不是工具、不是中间件、不"协助"模型 |

---

### 3. 非流式响应里，答案藏在哪个路径？

```text
choices[1,...,n-1].message.content
```

- `n > 1` 时按 `choices[i].index` 取，不能默认只有一条；
- `content` 可能为 `null`（纯工具调用），此时答案在 `message.tool_calls`；
- 模型拒答时看 `message.refusal`；
- 流式响应里对应的是 `choices[0].delta.content`，不是 `message`；
- 旧版 `/v1/completions` 才是 `choices[0].text`，别和 Chat Completions 混用。

```python
resp = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "你好"}],
)
print(resp.choices[0].message.content)  # 原始 JSON 即 choices[0].message.content
```

> 网关差异：部分兼容网关会把整个响应再包一层（如 `{"data": {...}}`），或直接返回 SDK 对象。定位时先打印顶层键，再按
> `choices → 0 → message → content` 下钻。

---

### 4. `finish_reason` 有哪几种值？`length` 代表什么？

**标准取值（OpenAI Chat Completions）**：

| 值               | 含义                                                                                       | 客户端该做什么                                    |
|------------------|--------------------------------------------------------------------------------------------|---------------------------------------------------|
| `stop`           | 模型自然结束，或命中 `stop` 序列                                                           | 正常消费                                          |
| `length`         | **token 预算耗尽**：达到 `max_tokens` / `max_completion_tokens`，或输入+输出触顶上下文窗口 | 内容不完整；别直接 `JSON.parse`，可提高上限或续写 |
| `tool_calls`     | 模型发起工具调用                                                                           | 执行工具并回填 `tool` 消息                        |
| `content_filter` | 被内容安全策略拦截，内容可能为空                                                           | 提示用户，不要原样重试                            |
| `function_call`  | 旧版函数调用，**已废弃**                                                                   | 迁移到 `tool_calls`                               |

**`length` 不是"文本长度"**，而是"**token 预算用尽**"：模型不是算到某个字数停的，是生成预算或上下文额度到顶被硬停。典型表现：句子说一半、JSON
不闭合、代码截断。

```json
{
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "综上所述，闭包是指函数与其词法环境的组合，因"
      },
      "finish_reason": "length"
    }
  ]
}
```

**厂商扩展值**（兼容层可能透出，务必加兜底分支）：

| 实现                    | 额外取值                                                                            |
|-------------------------|-------------------------------------------------------------------------------------|
| DeepSeek                | `insufficient_system_resource`（系统资源不足）                                      |
| vLLM                    | `abort`（请求被中止）                                                               |
| Anthropic（若走兼容层） | `end_turn` / `max_tokens` / `stop_sequence` / `tool_use` / `pause_turn` / `refusal` |
| Gemini                  | `STOP` / `MAX_TOKENS` / `SAFETY` / `RECITATION` / `OTHER` 等                        |

> 判定规则：`finish_reason` 非 `null` 只代表 **某个 choice** 结束；整个流是否完整仍以 `data: [DONE]` 为准。

---

### 5. `usage` 的三个数字，哪个决定你付多少钱？

**三个数字**：

| 字段                | 含义                                           |
|---------------------|------------------------------------------------|
| `prompt_tokens`     | 输入 token 数（系统提示 + 历史 + 本轮输入）    |
| `completion_tokens` | 输出 token 数（含推理模型的 reasoning tokens） |
| `total_tokens`      | 前两者之和                                     |

**计费公式**（单价因模型而异）：

```text
费用 = prompt_tokens     / 1,000,000 × 输入单价
     + completion_tokens / 1,000,000 × 输出单价
```

**`total_tokens` 只是和，不直接参与计费。** 输入和输出单价不同，输出通常贵数倍（常见 2–4 倍），所以"输入 10 万 token"和"输出
10 万 token"价钱差很多。

举例（假设输入 $0.15/M、输出 $0.60/M）：

```text
prompt_tokens = 1200  → 1200 / 1e6 × 0.15 = $0.00018
completion_tokens = 800 → 800 / 1e6 × 0.60 = $0.00048
合计 ≈ $0.00066
```

再叠加四个变量：

| 变量       | 影响                                                                |
|------------|---------------------------------------------------------------------|
| 缓存命中   | `prompt_tokens_details.cached_tokens` 通常按输入价的 10%–50% 计费   |
| 推理 token | `completion_tokens_details.reasoning_tokens` 计入输出，按输出价计费 |
| `n > 1`    | 每个候选都产生 completion tokens，全部计费                          |
| 流式       | 必须 `stream_options.include_usage: true` 才拿得到 `usage`          |

> 结论： **输入和输出各算各的**；`total_tokens` 只用于展示和限额，不要拿它乘单价。

---

### 6. 流式响应每行 `data: ` 是谁定的？为什么不是直接返回 JSON 数组？

**格式是谁定的**：SSE（Server-Sent Events），由 WHATWG 的 HTML Living Standard 定义，媒体类型 `text/event-stream`。
`stream: true` 只是让服务端 **切换到流式模式**，帧格式由标准和实现决定，不由请求字段定义。

SSE 的帧规则：

```text
data: {"choices":[{"delta":{"content":"你"}}]}

data: {"choices":[{"delta":{"content":"好"}}]}

data: [DONE]
```

| 规则 | 说明                                                            |
|------|-----------------------------------------------------------------|
| 分帧 | 空行（`\n\n`）结束一个事件，不是单个换行                        |
| 字段 | `data:` / `event:` / `id:` / `retry:`；`data:` 后只去掉一个空格 |
| 注释 | 以 `:` 开头（如 `: ping`），是心跳，忽略                        |
| 结束 | `data: [DONE]` 不是 JSON，收到即停                              |

**为什么不是 JSON 数组**：

1. **JSON 数组无法边收边解析**。标准 JSON 解析器要等 `]` 闭合才能确认结构合法，等于必须等全部生成完——流式就失去意义了（除非自己写流式
   JSON 解析器）。
2. **SSE 每帧是独立完整负载**。按空行分帧，单帧解析失败不影响后续帧；JSON 数组一处损坏整包报废。
3. **标准与生态**。浏览器 `EventSource` 原生支持 SSE；Nginx、CDN、API 网关都能按行 flush。JSON 数组没有"行"语义，中间层只能整包缓冲。
4. **可插入控制帧**。心跳、`event:` 类型、`id:`、`retry:` 都能表达；纯 JSON 数组里塞这些会污染数据。
5. **错误可单独成帧**。生成到一半出错，可以补一帧 `data: {"error": ...}`，数组格式做不到。

**关于 token**：`data: ` 是传输层字节， **不计入 `usage`，也不计费**；每帧多 6 个字节，和 JSON 数组的逗号、括号是同一量级。用"省
token"解释不成立。

> 备选方案确实存在：NDJSON / JSON Lines 也能流式（每行一个 JSON）。SSE 胜在浏览器原生 API 与标准帧语义，所以成了 OpenAI
> 兼容协议的事实标准。

---

### 7. 最后那行 `data: [DONE]` 是模型发出的，还是网关发出的？

由 **服务端 API 层**在模型生成结束后追加，不是模型 token，也不是 SSE 标准的一部分。

| 判断依据          | 说明                                        |
|-------------------|---------------------------------------------|
| 不是 JSON         | 模型只产 token，产不出协议控制帧            |
| 不在 `choices` 里 | 它不属于任何一次补全结果                    |
| 各实现都可能发    | OpenAI 平台发；vLLM 等自建推理服务也发      |
| 可能被改写或省略  | 反向代理/网关可能补发、吞掉，或换成直接断流 |

所以"网关"方向是对的，但更准确地说： **是厂商的 API 服务层（可能由网关实现）加的哨兵值**。客户端要两手准备：

```python
if payload == "[DONE]":
    break
# 没收到 [DONE] 但流结束了，也应正常收尾（容错），只是标记为"可能不完整"
```

---

### 8. `temperature` 和 `top_p` 为什么通常只调一个？

**两个都生效，而且是串联叠加的**：

| 参数          | 作用位置                              | 效果                                   |
|---------------|---------------------------------------|----------------------------------------|
| `temperature` | softmax 之前缩放 logits               | 越低分布越尖（确定），越高越平（随机） |
| `top_p`       | 对概率排序后截取累计前 `p` 的 nucleus | 砍掉长尾，只在高概率 token 里采样      |

**只调一个的真实原因**：

1. **效果耦合，无法归因**。同时改两个，输出变差时你不知道是谁的锅。
2. **容易过度约束**。`temperature=0.1` + `top_p=0.1` 会把候选压得极少，表现为复读、死板、答非所问。
3. **官方建议**。OpenAI 文档明确建议二者改其一，另一个保持默认 `1`。

**极端情况**：

- `temperature → 0` 等价贪心解码，`top_p` 基本无意义；
- `top_p → 0` 只留最高概率 token，`temperature` 的影响也被压缩。

**实践口径**：

| 目标             | 建议                                        |
|------------------|---------------------------------------------|
| 创意/发散        | 调 `temperature`（0.7–1.0），`top_p=1`      |
| 控长尾、保持稳定 | 调 `top_p`（0.9–0.95），`temperature=1`     |
| 事实性/抽取      | `temperature=0–0.3`                         |
| 推理模型         | 可能固定 `temperature=1` 或不支持，先查文档 |

---

### 9. 同一句话，中文为什么比英文更费 token？

**分词器（BPE）的词表覆盖，不是存储。**

- 词表里收录了大量英文高频词与子词，常见英文单词往往 **1 个 token**；
- 中文常用字多数也能占 1 个 token，但生僻字、专有名词、跨字组合常被拆成多个字节级 token；
- 中文没有空格，分词器依赖字间合并规则，覆盖率天然不如英文；
- 结果是： **同样信息量，中文的 token 数通常更高**。

**量化参考**（DeepSeek 官方口径，[Token 用量计算](https://api-docs.deepseek.com/zh-cn/quick_start/token_usage/)）：1
个英文字符 ≈ 0.3 token，1 个中文字符 ≈ 0.6 token。同字数下中文约 2 倍；中文句子字数通常更少，所以整体差距会缩小，但多数场景仍是中文更费。

**实际影响与对策**：

- 成本：中文提示词更贵，长文档尤甚；
- 上下文：同样的窗口能放的中文信息更少；
- 延迟：输入 token 多，首 token 更慢；
- 对策：精简系统提示、复用缓存、按 token 预算裁剪（不要按字符数估）、少用生僻表达；
- 新分词器（如 o200k）对中文有改善，但"中文更费"的方向没变。

---

### 10. 上下文窗口超了会发生什么，报错还是截断？

**托管 API 的标准行为**：HTTP 400，`type: invalid_request_error`，`code: context_length_exceeded`。

```json
{
  "error": {
    "message": "This model's maximum context length is 128000 tokens. However, your messages resulted in 130512 tokens.",
    "type": "invalid_request_error",
    "param": "messages",
    "code": "context_length_exceeded"
  }
}
```

**两种"到顶"要分清**：

| 场景                       | 发生时机 | 表现                                                   | 处理                                        |
|----------------------------|----------|--------------------------------------------------------|---------------------------------------------|
| 输入 + `max_tokens` > 窗口 | 请求阶段 | 400 `context_length_exceeded`，**一个 token 都不生成** | 裁剪历史 / 降 `max_tokens` / 换长上下文模型 |
| 生成中触顶                 | 生成阶段 | HTTP 200，`finish_reason: "length"`，内容不完整        | 续写、提高上限、缩短输入                    |

**兼容实现的差异**（这也是"截断"这个印象的来源）：

| 实现类型                        | 典型行为                                                           |
|---------------------------------|--------------------------------------------------------------------|
| OpenAI / Azure / 多数托管 API   | 报 400，不截断                                                     |
| vLLM                            | 报 400（超长直接拒绝）                                             |
| Ollama / llama.cpp 等本地运行时 | 按配置的 `num_ctx` / `--ctx-size` 截断或丢弃超出部分，**可能静默** |
| 部分兼容网关                    | 自动裁掉最早的消息（滑动窗口），调用方无感知                       |

**为什么"截断"更危险**：静默丢历史 → 模型答非所问、幻觉、不可复现；而且你从响应里看不出被丢了什么。
**不要把截断当默认行为来设计。**

**工程做法**：

1. 请求前算 token 预算：`窗口 - 预留输出 - 安全边际`；
2. 超了先裁剪：滑动窗口、摘要压缩、RAG 只召回相关片段；
3. 捕获 400 `context_length_exceeded`，降级重试（缩短输入或换模型）；
4. 对 `finish_reason == "length"` 单独分支，不要当正常结束；
5. 明确告知用户"历史已被压缩"，避免静默改变语义。

---

### 附：一句话记忆卡

1. 最小请求 = `model` + `messages`。
2. `assistant` 是模型说的话；工具结果走 `role: "tool"`。
3. 非流式答案：`choices[0].message.content`；流式：`choices[0].delta.content`。
4. `finish_reason`：`stop` / `length` / `tool_calls` / `content_filter` / `function_call`；`length` = token 预算耗尽。
5. 计费 = 输入 token × 输入价 + 输出 token × 输出价；`total_tokens` 不参与。
6. `data: ` 是 SSE 标准帧；不用 JSON 数组是因为数组无法增量解析。
7. `[DONE]` 是服务端 API 层加的哨兵，不是模型输出。
8. `temperature` 与 `top_p` 都生效，所以只调一个。
9. 中文费 token 是分词器词表问题，不是存储问题。
10. 上下文超限默认 400 报错；截断只出现在部分自建实现。
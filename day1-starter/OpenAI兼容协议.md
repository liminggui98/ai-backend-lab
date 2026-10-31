# OpenAI 兼容协议：请求体、响应体与错误码

> **接口**：`POST {base_url}/chat/completions`  
> **格式**：请求 `application/json`；响应 `application/json`（非流式）或 `text/event-stream`（流式）
>
> 本文描述 **请求体**、 **响应体**、 **流式输出（SSE） **与**错误响应**
> 的结构与字段，不涉及具体厂商的域名、鉴权与限流策略。字段的「必填 / 可空 / 默认值 / 取值」以 OpenAI Chat Completions 规范为准。

---

## OpenAI 兼容协议简介

### 什么是 OpenAI 兼容协议

「OpenAI 兼容协议」并不是一个正式标准，而是业界对 **与 OpenAI Chat Completions API 保持同构**的一类接口的统称：服务方按
OpenAI 的路径、JSON 字段、SSE 流式格式与错误结构实现接口，客户端只需替换 `base_url` 与 `api_key`，业务代码基本无需改动，即可在
OpenAI 与其它模型服务之间切换。

常见客户端包括 OpenAI 官方 SDK（Python / Node / Go / Java）、LangChain、LlamaIndex、Dify，以及 One API、LiteLLM 等兼容网关。

### 兼容的四个层面

| 层面   | 兼容内容                                    | 对客户端的意义         |
|--------|---------------------------------------------|------------------------|
| 传输层 | HTTPS + JSON；流式为 SSE                    | 复用同一套 HTTP 客户端 |
| 路径层 | 以 `/chat/completions` 结尾，前缀可变       | 只改 `base_url`        |
| 数据层 | 请求 / 响应字段同名、同义、同结构           | 序列化代码无需重写     |
| 行为层 | 增量语义、`[DONE]` 结束、统一错误体、鉴权头 | 解析与重试逻辑可复用   |

### 路径与 base_url

不同厂商的路径前缀不一致，本文统一写作 `POST {base_url}/chat/completions`：

| 形态       | 示例                                                                                                   |
|------------|--------------------------------------------------------------------------------------------------------|
| 带 `/v1`   | `https://api.openai.com/v1/chat/completions`                                                           |
| 不带 `/v1` | `https://api.example.com/chat/completions`                                                             |
| 自定义前缀 | `https://api.example.com/api/v1/chat/completions`                                                      |
| 部署式     | `https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions?api-version=...` |

> 配置 `base_url` 时包含厂商要求的完整前缀，SDK 会自动拼接 `/chat/completions`。

### 兼容性边界

「兼容」不等于「完全一致」。同一份客户端代码在不同厂商上仍可能遇到以下差异：

| 差异点   | 典型表现                                      | 客户端应对                   |
|----------|-----------------------------------------------|------------------------------|
| 扩展字段 | 厂商增加私有字段（如思考开关、缓存统计）      | 忽略未知字段，不因多字段报错 |
| 字段子集 | 部分标准参数未实现，传入被忽略或报错          | 做能力探测与降级             |
| 枚举扩展 | `finish_reason` 等取值多于标准集合            | 增加兜底分支                 |
| 错误命名 | `type` / `code` 文案不同                      | 以 HTTP 状态码为主判断       |
| 鉴权方式 | `Authorization: Bearer`、`api-key` 或自定义头 | 按厂商要求配置               |
| 模型能力 | 多模态、工具调用、JSON 输出支持不一           | 按模型能力路由               |

> 实践建议：对未知字段保持宽容、对缺失字段提供默认值、对未知枚举值走兜底逻辑，这样同一套代码才能稳定运行在多个兼容实现之上。

---

## 阅读约定

| 记号             | 含义                              |
|------------------|-----------------------------------|
| ✅               | 必填                              |
| ❌               | 可选                              |
| 可空 ✅          | 允许显式传 `null`，通常等同于省略 |
| `string \| null` | 联合类型，`null` 为合法值         |

---

## 目录

- [OpenAI 兼容协议简介](#openai-兼容协议简介)
    - [什么是 OpenAI 兼容协议](#什么是-openai-兼容协议)
    - [兼容的四个层面](#兼容的四个层面)
    - [路径与 base_url](#路径与-base_url)
    - [兼容性边界](#兼容性边界)
- [一、请求体](#一请求体)
    - [1.1 整体结构](#11-整体结构)
    - [1.2 字段总览](#12-字段总览)
    - [1.3 messages](#13-messages)
    - [1.4 采样与生成控制](#14-采样与生成控制)
    - [1.5 工具调用](#15-工具调用)
    - [1.6 其他参数](#16-其他参数)
    - [1.7 请求示例](#17-请求示例)
- [二、响应体](#二响应体)
    - [2.1 非流式响应](#21-非流式响应)
    - [2.2 流式响应（SSE）](#22-流式响应sse)
    - [2.3 finish_reason](#23-finish_reason)
    - [2.4 usage](#24-usage)
- [三、错误响应](#三错误响应)
    - [3.1 错误对象](#31-错误对象)
    - [3.2 HTTP 状态码](#32-http-状态码)
    - [3.3 错误类型与错误码](#33-错误类型与错误码)
    - [3.4 流式请求中的错误](#34-流式请求中的错误)
    - [3.5 重试策略](#35-重试策略)

---

## 一、请求体

### 1.1 整体结构

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "你是一个简洁的助手。" },
    { "role": "user", "content": "你好" }
  ],
  "temperature": 0.7,
  "stream": false
}
```

| 层级             | 说明                                   |
|------------------|----------------------------------------|
| 顶层             | 模型、消息、采样参数、工具、流式开关等 |
| `messages[]`     | 对话消息，按时间顺序排列，至少 1 条    |
| `tools[]`        | 可调用工具（函数）定义                 |
| `stream_options` | 流式输出选项                           |

---

### 1.2 字段总览

| 字段                    | 类型                      | 必填 | 可空 | 默认值                       | 说明                                               |
|-------------------------|---------------------------|------|------|------------------------------|----------------------------------------------------|
| `model`                 | `string`                  | ✅   | ❌   | —                            | 模型 ID                                            |
| `messages`              | `array<object>`           | ✅   | ❌   | —                            | 对话消息列表，最少 1 条                            |
| `temperature`           | `number`                  | ❌   | ✅   | `1`                          | 采样温度，`0 ~ 2`                                  |
| `top_p`                 | `number`                  | ❌   | ✅   | `1`                          | 核采样，`0 ~ 1`                                    |
| `n`                     | `integer`                 | ❌   | ✅   | `1`                          | 每个输入生成几条候选回复                           |
| `stream`                | `boolean`                 | ❌   | ✅   | `false`                      | 是否以 SSE 流式返回                                |
| `stream_options`        | `object`                  | ❌   | ✅   | `null`                       | 流式选项，需配合 `stream: true`                    |
| `stop`                  | `string \| array<string>` | ❌   | ✅   | `null`                       | 停止序列，数组最多 4 个                            |
| `max_tokens`            | `integer`                 | ❌   | ✅   | `null`                       | 输出上限，**已弃用**                               |
| `max_completion_tokens` | `integer`                 | ❌   | ✅   | `null`                       | 输出上限，推荐使用                                 |
| `presence_penalty`      | `number`                  | ❌   | ✅   | `0`                          | 话题新颖度惩罚，`-2 ~ 2`                           |
| `frequency_penalty`     | `number`                  | ❌   | ✅   | `0`                          | 词频惩罚，`-2 ~ 2`                                 |
| `logit_bias`            | `object`                  | ❌   | ✅   | `null`                       | 指定 token 的概率偏置，`-100 ~ 100`                |
| `logprobs`              | `boolean`                 | ❌   | ✅   | `false`                      | 是否返回输出 token 的对数概率                      |
| `top_logprobs`          | `integer`                 | ❌   | ✅   | `null`                       | 返回 top N 概率 token，`0 ~ 20`                    |
| `response_format`       | `object`                  | ❌   | ✅   | `{"type":"text"}`            | 输出格式：文本 / JSON / JSON Schema                |
| `seed`                  | `integer`                 | ❌   | ✅   | `null`                       | 随机种子，尽力保证可复现                           |
| `tools`                 | `array<object>`           | ❌   | ✅   | —                            | 可调用的工具列表                                   |
| `tool_choice`           | `string \| object`        | ❌   | ✅   | 无工具 `none`；有工具 `auto` | 工具调用策略                                       |
| `parallel_tool_calls`   | `boolean`                 | ❌   | ❌   | `true`                       | 是否允许并行工具调用                               |
| `user`                  | `string`                  | ❌   | ❌   | —                            | 终端用户标识                                       |
| `store`                 | `boolean`                 | ❌   | ✅   | `false`                      | 是否存储本次请求供后续使用                         |
| `metadata`              | `object`                  | ❌   | ✅   | `null`                       | 附加元数据，最多 16 对键值                         |
| `service_tier`          | `string`                  | ❌   | ✅   | `auto`                       | 服务等级：`auto` / `default` / `flex` / `priority` |
| `reasoning_effort`      | `string`                  | ❌   | ✅   | `null`                       | 推理强度：`low` / `medium` / `high`                |

---

### 1.3 messages

#### 1.3.1 约束

| 项目       | 值                 |
|------------|--------------------|
| 类型       | `array<object>`    |
| 必填       | ✅                 |
| 最少元素数 | `1`                |
| 顺序       | 数组顺序即对话顺序 |

#### 1.3.2 角色一览

| `role`      | 用途                          | `content` 类型                             | 必填字段                          |
|-------------|-------------------------------|--------------------------------------------|-----------------------------------|
| `developer` | 开发者指令，优先级高于 `user` | `string \| array<text>`                    | `role`、`content`                 |
| `system`    | 系统指令                      | `string \| array<text>`                    | `role`、`content`                 |
| `user`      | 用户输入，支持多模态          | `string \| array<content_part>`            | `role`、`content`                 |
| `assistant` | 模型历史回复                  | `string \| array<text \| refusal> \| null` | `role`；`content` 条件必填        |
| `tool`      | 工具执行结果                  | `string \| array<text>`                    | `role`、`content`、`tool_call_id` |
| `function`  | 旧版函数结果（已废弃）        | `string \| null`                           | `role`、`content`、`name`         |

> `developer` 与 `system` 语义相同，`developer` 用于取代 `system`（o1 及更新模型）。两者都支持时，`developer` 优先生效。

#### 1.3.3 各角色字段

**developer / system**

| 字段      | 类型                        | 必填 | 可空 | 说明       |
|-----------|-----------------------------|------|------|------------|
| `role`    | `"developer"` \| `"system"` | ✅   | ❌   | 角色       |
| `content` | `string \| array<text>`     | ✅   | ❌   | 指令内容   |
| `name`    | `string`                    | ❌   | ❌   | 参与者名称 |

```json
{
  "role": "system",
  "content": "你是一个严谨的代码审查员。"
}
```

**user**

| 字段      | 类型                            | 必填 | 可空 | 说明                                    |
|-----------|---------------------------------|------|------|-----------------------------------------|
| `role`    | `"user"`                        | ✅   | ❌   | 角色                                    |
| `content` | `string \| array<content_part>` | ✅   | ❌   | 文本，或内容块数组（数组至少 1 个元素） |
| `name`    | `string`                        | ❌   | ❌   | 参与者名称                              |

`content` 数组支持的 part：

| `type`        | 作用   | 关键字段                                 |
|---------------|--------|------------------------------------------|
| `text`        | 文本块 | `text`                                   |
| `image_url`   | 图片   | `image_url.url`、`image_url.detail`      |
| `input_audio` | 音频   | `input_audio.data`、`input_audio.format` |
| `file`        | 文件   | `file.file_data` 或 `file.file_id`       |

**assistant**

| 字段            | 类型                                       | 必填     | 可空 | 默认值 | 说明                                            |
|-----------------|--------------------------------------------|----------|------|--------|-------------------------------------------------|
| `role`          | `"assistant"`                              | ✅       | ❌   | —      | 角色                                            |
| `content`       | `string \| array<text \| refusal> \| null` | 条件必填 | ✅   | —      | 无 `tool_calls` / `function_call` 时必须提供    |
| `name`          | `string`                                   | ❌       | ❌   | —      | 参与者名称                                      |
| `refusal`       | `string`                                   | ❌       | ✅   | `null` | 拒答文本（顶层字段）                            |
| `audio`         | `object`                                   | ❌       | ✅   | —      | 音频输出引用：`{ "id": "audio_xxx" }`           |
| `tool_calls`    | `array<object>`                            | ❌       | ❌   | —      | 模型发起的工具调用                              |
| `function_call` | `object`                                   | ❌       | ❌   | —      | 已废弃：`{ "name": "...", "arguments": "..." }` |

```json
{
  "role": "assistant",
  "content": null,
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": { "name": "get_weather", "arguments": "{\"city\":\"北京\"}" }
    }
  ]
}
```

**tool**

| 字段           | 类型                    | 必填 | 可空 | 说明                               |
|----------------|-------------------------|------|------|------------------------------------|
| `role`         | `"tool"`                | ✅   | ❌   | 角色                               |
| `content`      | `string \| array<text>` | ✅   | ❌   | 工具返回值，通常是 JSON 字符串     |
| `tool_call_id` | `string`                | ✅   | ❌   | 对应的 `assistant.tool_calls[].id` |

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "{\"temp\":26}"
}
```

**function（已废弃）**

| 字段      | 类型             | 必填 | 可空 | 说明       |
|-----------|------------------|------|------|------------|
| `role`    | `"function"`     | ✅   | ❌   | 角色       |
| `content` | `string \| null` | ✅   | ✅   | 函数返回值 |
| `name`    | `string`         | ✅   | ❌   | 函数名     |

#### 1.3.4 content part 结构

**text**

| 字段   | 类型     | 必填 | 说明     |
|--------|----------|------|----------|
| `type` | `"text"` | ✅   | 固定值   |
| `text` | `string` | ✅   | 文本内容 |

**image_url**

| 字段               | 类型          | 必填 | 取值                    | 说明                                    |
|--------------------|---------------|------|-------------------------|-----------------------------------------|
| `type`             | `"image_url"` | ✅   | `image_url`             | 固定值                                  |
| `image_url.url`    | `string`      | ✅   | —                       | 公网 URL 或 `data:image/...;base64,...` |
| `image_url.detail` | `string`      | ❌   | `auto` / `low` / `high` | 视觉精度，默认 `auto`                   |

```json
{
  "type": "image_url",
  "image_url": { "url": "https://example.com/cat.png", "detail": "high" }
}
```

**input_audio**

| 字段                 | 类型            | 必填 | 取值          | 说明                                 |
|----------------------|-----------------|------|---------------|--------------------------------------|
| `type`               | `"input_audio"` | ✅   | `input_audio` | 固定值                               |
| `input_audio.data`   | `string`        | ✅   | —             | Base64 音频数据（不含 `data:` 前缀） |
| `input_audio.format` | `string`        | ✅   | `wav` / `mp3` | 音频格式                             |

**file**

| 字段             | 类型     | 必填   | 说明                             |
|------------------|----------|--------|----------------------------------|
| `type`           | `"file"` | ✅     | 固定值                           |
| `file.file_data` | `string` | 二选一 | `data:` 开头的 Base64 data URL   |
| `file.file_id`   | `string` | 二选一 | 通过 Files API 上传得到的文件 ID |
| `file.filename`  | `string` | ❌     | 文件名，便于模型理解             |

**refusal（仅 assistant）**

| 字段      | 类型        | 必填 | 说明     |
|-----------|-------------|------|----------|
| `type`    | `"refusal"` | ✅   | 固定值   |
| `refusal` | `string`    | ✅   | 拒答内容 |

---

### 1.4 采样与生成控制

#### 1.4.1 `temperature` 与 `top_p`

| 字段          | 类型     | 可空 | 默认值 | 范围    | 说明                                   |
|---------------|----------|------|--------|---------|----------------------------------------|
| `temperature` | `number` | ✅   | `1`    | `0 ~ 2` | 越大越随机，越小越确定                 |
| `top_p`       | `number` | ✅   | `1`    | `0 ~ 1` | 只从累计概率前 `top_p` 的 token 中采样 |

建议只调整其中一个，不要同时修改。

#### 1.4.2 `n`

| 字段 | 类型      | 可空 | 默认值 | 说明                                                   |
|------|-----------|------|--------|--------------------------------------------------------|
| `n`  | `integer` | ✅   | `1`    | 为每条输入生成 n 条候选回复；响应中 `choices` 长度为 n |

> `n > 1` 时按所有候选的生成总量计费。

#### 1.4.3 `stop`

| 约束     | 值                                         |
|----------|--------------------------------------------|
| 类型     | `string \| array<string>`，可空            |
| 默认值   | `null`                                     |
| 数组上限 | `4` 个                                     |
| 行为     | 命中任一序列即停止，停止序列不出现在输出中 |

```json
{
  "stop": [
    "\n\n",
    "###"
  ]
}
```

#### 1.4.4 `max_tokens` 与 `max_completion_tokens`

| 字段                    | 类型      | 可空 | 默认值 | 状态       | 说明                            |
|-------------------------|-----------|------|--------|------------|---------------------------------|
| `max_tokens`            | `integer` | ✅   | `null` | **已弃用** | 输出 token 上限，兼容保留       |
| `max_completion_tokens` | `integer` | ✅   | `null` | 推荐       | 输出 token 上限，包含推理 token |

- 两者不应同时使用，推荐只传 `max_completion_tokens`。
- 上限还受模型上下文窗口约束：输入 + 输出不得超过上下文长度。
- 部分推理模型不支持 `max_tokens`，只接受 `max_completion_tokens`。

#### 1.4.5 惩罚项

| 字段                | 类型     | 可空 | 默认值 | 范围     | 说明               |
|---------------------|----------|------|--------|----------|--------------------|
| `presence_penalty`  | `number` | ✅   | `0`    | `-2 ~ 2` | 正值鼓励引入新话题 |
| `frequency_penalty` | `number` | ✅   | `0`    | `-2 ~ 2` | 正值抑制重复用词   |

#### 1.4.6 `logit_bias`

| 约束   | 值                                            |
|--------|-----------------------------------------------|
| 类型   | `object`（`map<token_id, -100 ~ 100>`），可空 |
| 默认值 | `null`                                        |
| 语义   | `-100` 禁止该 token，`100` 强制该 token       |

```json
{
  "logit_bias": {
    "50256": -100
  }
}
```

#### 1.4.7 `logprobs` 与 `top_logprobs`

| 字段           | 类型      | 可空 | 默认值  | 范围     | 说明                                |
|----------------|-----------|------|---------|----------|-------------------------------------|
| `logprobs`     | `boolean` | ✅   | `false` | —        | 是否返回每个输出 token 的对数概率   |
| `top_logprobs` | `integer` | ✅   | `null`  | `0 ~ 20` | 每个位置返回 top N 候选及其对数概率 |

- 指定 `top_logprobs` 时，`logprobs` 必须为 `true`。
- 结果位于响应的 `choices[].logprobs.content`。

#### 1.4.8 `response_format`

| `type`        | 说明                                                 |
|---------------|------------------------------------------------------|
| `text`        | 普通文本，默认值                                     |
| `json_object` | 保证输出是合法 JSON，需在提示词中要求 JSON           |
| `json_schema` | 按给定 JSON Schema 输出，需提供 `json_schema.schema` |

`json_schema` 结构：

| 字段          | 类型      | 必填 | 说明                              |
|---------------|-----------|------|-----------------------------------|
| `name`        | `string`  | ✅   | Schema 名称                       |
| `description` | `string`  | ❌   | Schema 描述                       |
| `schema`      | `object`  | ✅   | JSON Schema 定义                  |
| `strict`      | `boolean` | ❌   | 是否严格遵循 Schema，默认 `false` |

```json
{
  "response_format": {
    "type": "json_schema",
    "json_schema": {
      "name": "person",
      "strict": true,
      "schema": {
        "type": "object",
        "properties": { "name": { "type": "string" } },
        "required": ["name"],
        "additionalProperties": false
      }
    }
  }
}
```

#### 1.4.9 `seed`

| 约束   | 值                                                        |
|--------|-----------------------------------------------------------|
| 类型   | `integer`，可空                                           |
| 默认值 | `null`                                                    |
| 说明   | 尽力保证相同种子 + 相同输入得到相同输出，不保证完全可复现 |

#### 1.4.10 `stream` 与 `stream_options`

| 字段                           | 类型      | 必填 | 可空 | 默认值  | 说明                                                                                                           |
|--------------------------------|-----------|------|------|---------|----------------------------------------------------------------------------------------------------------------|
| `stream`                       | `boolean` | ❌   | ✅   | `false` | `true` 时以 SSE 增量返回                                                                                       |
| `stream_options.include_usage` | `boolean` | ❌   | ❌   | `false` | 为 `true` 时，所有 chunk 都带 `usage` 字段（其余为 `null`），并在 `[DONE]` 前额外返回一个只含 `usage` 的 chunk |

```json
{
  "stream": true,
  "stream_options": { "include_usage": true }
}
```

---

### 1.5 工具调用

#### 1.5.1 `tools`

| 约束     | 值                                        |
|----------|-------------------------------------------|
| 类型     | `array<object>`，可空                     |
| 元素类型 | `function`（主流）或 `custom`（自由格式） |
| 名称约束 | 唯一，`[a-zA-Z0-9_-]`，≤ 64 字符          |

**function 工具**

| 字段                   | 类型         | 必填 | 默认值  | 说明                                 |
|------------------------|--------------|------|---------|--------------------------------------|
| `type`                 | `"function"` | ✅   | —       | 工具类型                             |
| `function.name`        | `string`     | ✅   | —       | 函数名                               |
| `function.description` | `string`     | ❌   | —       | 功能描述                             |
| `function.parameters`  | `object`     | ❌   | —       | JSON Schema 参数定义，省略表示无参数 |
| `function.strict`      | `boolean`    | ❌   | `false` | 严格模式，保证输出符合 Schema        |

```json
{
  "type": "function",
  "function": {
    "name": "get_weather",
    "description": "查询指定城市的实时天气",
    "parameters": {
      "type": "object",
      "properties": { "city": { "type": "string" } },
      "required": ["city"],
      "additionalProperties": false
    },
    "strict": true
  }
}
```

> `strict: true` 时，object 的所有属性需列入 `required`，且 `additionalProperties` 必须为 `false`（依模型支持情况而定）。

**custom 工具（自由格式）**

```json
{
  "type": "custom",
  "custom": {
    "name": "run_sql",
    "description": "执行一段 SQL"
  }
}
```

#### 1.5.2 `tool_choice`

| 取值                                            | 含义                 |
|-------------------------------------------------|----------------------|
| `none`                                          | 不调用任何工具       |
| `auto`                                          | 模型自行决定是否调用 |
| `required`                                      | 必须调用至少一个工具 |
| `{"type":"function","function":{"name":"xxx"}}` | 强制调用指定函数     |

| 条件       | 默认值 |
|------------|--------|
| 无 `tools` | `none` |
| 有 `tools` | `auto` |

#### 1.5.3 `parallel_tool_calls`

| 字段                  | 类型      | 必填 | 默认值 | 说明                           |
|-----------------------|-----------|------|--------|--------------------------------|
| `parallel_tool_calls` | `boolean` | ❌   | `true` | `false` 时最多返回一个工具调用 |

#### 1.5.4 工具调用闭环

1. 请求携带 `tools`，模型返回 `finish_reason = "tool_calls"`，`message.tool_calls` 给出调用列表。
2. 客户端执行函数。
3. 把 `assistant` 消息（含 `tool_calls`）与 `role: "tool"` 的结果追加到 `messages`，再次请求。
4. `tool_call_id` 必须与对应调用的 `id` 一致。

---

### 1.6 其他参数

| 字段               | 类型      | 可空 | 默认值  | 说明                                                |
|--------------------|-----------|------|---------|-----------------------------------------------------|
| `user`             | `string`  | ❌   | —       | 终端用户唯一标识，用于滥用监测；不要包含隐私信息    |
| `store`            | `boolean` | ✅   | `false` | 是否存储请求与响应，供后续评估 / 蒸馏使用           |
| `metadata`         | `object`  | ✅   | `null`  | 附加键值对，最多 16 对；键 ≤ 64 字符，值 ≤ 512 字符 |
| `service_tier`     | `string`  | ✅   | `auto`  | `auto` / `default` / `flex` / `priority`            |
| `reasoning_effort` | `string`  | ✅   | `null`  | `low` / `medium` / `high`（部分模型支持 `minimal`） |

---

### 1.7 请求示例

**基础对话**

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "你是一个简洁的助手。" },
    { "role": "user", "content": "用一句话解释什么是闭包。" }
  ],
  "temperature": 0.7,
  "max_completion_tokens": 256
}
```

**多模态输入**

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "描述这张图片。" },
        {
          "type": "image_url",
          "image_url": { "url": "https://example.com/a.jpg", "detail": "low" }
        }
      ]
    }
  ]
}
```

**工具调用**

```json
{
  "model": "gpt-4o-mini",
  "messages": [{ "role": "user", "content": "北京现在多少度？" }],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "查询指定城市的实时天气",
        "parameters": {
          "type": "object",
          "properties": { "city": { "type": "string" } },
          "required": ["city"]
        }
      }
    }
  ],
  "tool_choice": "auto",
  "parallel_tool_calls": true
}
```

---

## 二、响应体

### 2.1 非流式响应

#### 2.1.1 顶层结构

| 字段                 | 类型            | 必填 | 可空 | 取值                            | 说明                       |
|----------------------|-----------------|------|------|---------------------------------|----------------------------|
| `id`                 | `string`        | ✅   | ❌   | —                               | 本次补全的唯一标识         |
| `object`             | `string`        | ✅   | ❌   | `chat.completion`               | 对象类型                   |
| `created`            | `integer`       | ✅   | ❌   | —                               | Unix 时间戳（秒）          |
| `model`              | `string`        | ✅   | ❌   | —                               | 实际使用的模型             |
| `service_tier`       | `string`        | ❌   | ✅   | `default` / `flex` / `priority` | 实际服务等级               |
| `system_fingerprint` | `string`        | ❌   | ❌   | —                               | 后端配置指纹               |
| `choices`            | `array<object>` | ✅   | ❌   | —                               | 候选回复列表，长度等于 `n` |
| `usage`              | `object`        | ❌   | ✅   | —                               | token 用量                 |

#### 2.1.2 `choices[]`

| 字段            | 类型      | 必填 | 可空 | 说明                                  |
|-----------------|-----------|------|------|---------------------------------------|
| `index`         | `integer` | ✅   | ❌   | 候选索引，从 0 开始                   |
| `message`       | `object`  | ✅   | ❌   | 模型生成的消息                        |
| `finish_reason` | `string`  | ✅   | ❌   | 停止原因，见 [2.3](#23-finish_reason) |
| `logprobs`      | `object`  | ✅   | ✅   | 对数概率信息；未开启时为 `null`       |

`logprobs` 结构：

| 字段      | 类型            | 必填 | 可空 | 说明                  |
|-----------|-----------------|------|------|-----------------------|
| `content` | `array<object>` | ✅   | ✅   | 输出 token 的概率列表 |
| `refusal` | `array<object>` | ❌   | ✅   | 拒答 token 的概率列表 |

数组元素：

| 字段           | 类型             | 必填 | 可空 | 说明                                                     |
|----------------|------------------|------|------|----------------------------------------------------------|
| `token`        | `string`         | ✅   | ❌   | 输出 token                                               |
| `logprob`      | `number`         | ✅   | ❌   | 对数概率；`-9999.0` 表示概率极小                         |
| `bytes`        | `array<integer>` | ✅   | ✅   | token 的 UTF-8 字节表示                                  |
| `top_logprobs` | `array<object>`  | ✅   | ❌   | 该位置的 top N 候选，元素结构同上（不含 `top_logprobs`） |

#### 2.1.3 `message`

| 字段            | 类型            | 必填 | 可空 | 取值        | 说明                                             |
|-----------------|-----------------|------|------|-------------|--------------------------------------------------|
| `role`          | `string`        | ✅   | ❌   | `assistant` | 消息角色                                         |
| `content`       | `string`        | ✅   | ✅   | —           | 回复正文；工具调用时可能为 `null`                |
| `refusal`       | `string`        | ❌   | ✅   | —           | 拒答文本；未拒答时为 `null`                      |
| `annotations`   | `array<object>` | ❌   | ❌   | —           | 引用标注（如网页搜索的 `url_citation`）          |
| `audio`         | `object`        | ❌   | ✅   | —           | 音频输出：`{ id, data, transcript, expires_at }` |
| `tool_calls`    | `array<object>` | ❌   | ❌   | —           | 模型发起的工具调用                               |
| `function_call` | `object`        | ❌   | ❌   | —           | 已废弃：`{ name, arguments }`                    |

`tool_calls[]` 元素：

| 字段                 | 类型     | 必填 | 取值        | 说明                            |
|----------------------|----------|------|-------------|---------------------------------|
| `id`                 | `string` | ✅   | —           | 调用 ID，回填 `tool` 消息时使用 |
| `type`               | `string` | ✅   | `function`  | 工具类型                        |
| `function.name`      | `string` | ✅   | —           | 函数名                          |
| `function.arguments` | `string` | ✅   | JSON 字符串 | 函数参数，需自行解析与校验      |

> `arguments` 是字符串且不保证是合法 JSON，调用前需 `JSON.parse` 并校验参数。

#### 2.1.4 响应示例

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1730000000,
  "model": "gpt-4o-mini-2024-07-18",
  "system_fingerprint": "fp_abc123",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！有什么可以帮你的吗？",
        "refusal": null
      },
      "logprobs": null,
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 19,
    "completion_tokens": 10,
    "total_tokens": 29,
    "prompt_tokens_details": { "cached_tokens": 0 },
    "completion_tokens_details": { "reasoning_tokens": 0 }
  }
}
```

---

### 2.2 流式响应（SSE）

流式模式通过 Server-Sent Events（SSE）逐步返回增量，适合打字机效果、长回答与工具调用参数的分段传输。

#### 2.2.1 建立流式连接

请求体设置 `stream: true`：

```json
{
  "model": "gpt-4o-mini",
  "messages": [{ "role": "user", "content": "写一段 200 字的介绍" }],
  "stream": true,
  "stream_options": { "include_usage": true }
}
```

| 项目                | 值                                 |
|---------------------|------------------------------------|
| 建议请求头          | `Accept: text/event-stream`        |
| 响应 `Content-Type` | `text/event-stream; charset=utf-8` |
| 传输方式            | 通常为 chunked                     |
| 结束标记            | `data: [DONE]`                     |

常见响应头：

| 响应头          | 示例值                             | 说明           |
|-----------------|------------------------------------|----------------|
| `Content-Type`  | `text/event-stream; charset=utf-8` | 标识 SSE       |
| `Cache-Control` | `no-cache`                         | 避免中间层缓存 |
| `Connection`    | `keep-alive`                       | 保持长连接     |
| `X-Request-Id`  | `req_abc123`                       | 请求追踪 ID    |

> **必须关闭中间层缓冲**：Nginx 需 `proxy_buffering off`，CDN / API 网关需支持流式透传；否则 chunk 会被攒批后一次性下发，表现为「一次性返回全文」。

#### 2.2.2 事件格式

SSE 以行为单位， **空行**表示一个事件结束：

```text
data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk",...}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk",...}

data: [DONE]
```

| 规则     | 说明                                                   |
|----------|--------------------------------------------------------|
| 事件分隔 | 空行（`\n\n` 或 `\r\n\r\n`），不是单个换行             |
| 数据行   | 以 `data:` 开头，冒号后有一个可选空格                  |
| 多行数据 | 一个事件内可有多个 `data:` 行，客户端以 `\n` 拼接      |
| 注释行   | 以 `:` 开头（如 `: ping`），属于心跳，应忽略           |
| 其他字段 | `event:` / `id:` / `retry:`，OpenAI 兼容实现通常不使用 |
| 结束哨兵 | `data: [DONE]`，**不是 JSON**，收到后停止解析          |

> 解析时按行读取、遇空行分派事件；注意去掉行尾 `\r`，并只去掉 `data:` 后的 **一个**空格。

#### 2.2.3 Chunk 结构

| 字段                 | 类型            | 必填 | 可空 | 取值                    | 说明                                     |
|----------------------|-----------------|------|------|-------------------------|------------------------------------------|
| `id`                 | `string`        | ✅   | ❌   | —                       | 整个流共用同一 ID                        |
| `object`             | `string`        | ✅   | ❌   | `chat.completion.chunk` | 对象类型                                 |
| `created`            | `integer`       | ✅   | ❌   | —                       | 创建时间戳（秒）                         |
| `model`              | `string`        | ✅   | ❌   | —                       | 模型名                                   |
| `service_tier`       | `string`        | ❌   | ✅   | —                       | 服务等级                                 |
| `system_fingerprint` | `string`        | ❌   | ❌   | —                       | 后端配置指纹                             |
| `choices`            | `array<object>` | ✅   | ❌   | —                       | 增量列表；仅含 usage 的 chunk 中为空数组 |
| `usage`              | `object`        | ❌   | ✅   | —                       | 仅 `include_usage` 的最后一个 chunk 有值 |

`choices[]`：

| 字段            | 类型      | 必填 | 可空 | 说明                            |
|-----------------|-----------|------|------|---------------------------------|
| `index`         | `integer` | ✅   | ❌   | 候选索引                        |
| `delta`         | `object`  | ✅   | ❌   | 本次增量                        |
| `finish_reason` | `string`  | ✅   | ✅   | 生成中为 `null`，结束时为具体值 |
| `logprobs`      | `object`  | ❌   | ✅   | 概率信息                        |

`delta`：

| 字段            | 类型            | 必填 | 可空 | 取值        | 说明            |
|-----------------|-----------------|------|------|-------------|-----------------|
| `role`          | `string`        | ❌   | ❌   | `assistant` | 首个 chunk 携带 |
| `content`       | `string`        | ❌   | ✅   | —           | 正文增量        |
| `refusal`       | `string`        | ❌   | ✅   | —           | 拒答增量        |
| `tool_calls`    | `array<object>` | ❌   | ❌   | —           | 工具调用增量    |
| `function_call` | `object`        | ❌   | ❌   | —           | 已废弃          |

`delta.tool_calls[]`：

| 字段                 | 类型      | 必填 | 说明                                |
|----------------------|-----------|------|-------------------------------------|
| `index`              | `integer` | ✅   | 第几个工具调用，用于分片归并        |
| `id`                 | `string`  | ❌   | 仅首个分片携带                      |
| `type`               | `string`  | ❌   | 仅首个分片携带，取值 `function`     |
| `function.name`      | `string`  | ❌   | 仅首个分片携带                      |
| `function.arguments` | `string`  | ❌   | 参数分片，需按 `index` 拼接后再解析 |

#### 2.2.4 增量合并规则

| 字段               | 合并方式                                                               |
|--------------------|------------------------------------------------------------------------|
| `delta.role`       | 取首个非空值，通常只在首块出现                                         |
| `delta.content`    | 按顺序做字符串拼接                                                     |
| `delta.refusal`    | 按顺序做字符串拼接                                                     |
| `delta.tool_calls` | 按 `index` 分组；`id` / `type` / `name` 取首个非空值，`arguments` 拼接 |
| `finish_reason`    | 非 `null` 表示该 choice 生成结束                                       |
| `usage`            | 仅在 `include_usage` 时出现，取最后一个非空值                          |

注意事项：

1. `n > 1` 时，按 `choice.index` 分别累积，不要混用。
2. `content` 可能是空字符串或缺失，拼接时用 `or ""` 跳过。
3. `tool_calls[].function.arguments` 可能被拆到任意多个 chunk， **必须**在 `finish_reason` 之后再 `JSON.parse`。
4. 不要假设每个 chunk 只包含一个 choice，也不要假设 `delta` 非空。

#### 2.2.5 完整示例

```jsonl
data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1730000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":""},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1730000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":"你"},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1730000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":"好"},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1730000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{},"logprobs":null,"finish_reason":"stop"}]}

data: [DONE]
```

工具调用的分片示例：

```jsonl
data: {"id":"chatcmpl-def456","object":"chat.completion.chunk","created":1730000001,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_abc123","type":"function","function":{"name":"get_weather","arguments":""}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-def456","object":"chat.completion.chunk","created":1730000001,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"city\":"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-def456","object":"chat.completion.chunk","created":1730000001,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"北京\"}"}}]},"finish_reason":"tool_calls"}]}

data: [DONE]
```

#### 2.2.6 `include_usage` 与结束

| `include_usage` | 各 chunk 的 `usage`                            | 额外 chunk                                             |
|-----------------|------------------------------------------------|--------------------------------------------------------|
| `false` / 省略  | 不含 `usage` 字段                              | 无                                                     |
| `true`          | 所有 chunk 都带 `usage`，除最后一个外为 `null` | `[DONE]` 前多一个 `choices: []`、只含 `usage` 的 chunk |

```jsonl
data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1730000000,"model":"gpt-4o-mini","choices":[],"usage":{"prompt_tokens":19,"completion_tokens":10,"total_tokens":29}}

data: [DONE]
```

结束判定：`finish_reason` 非 `null` 只表示某个 choice 结束；整个流以 `data: [DONE]` 为准。

#### 2.2.7 客户端解析伪代码

```python
import json

content = ""
tool_calls = {}
finish_reason = None
usage = None
done = False

for raw in response.iter_lines(decode_unicode=True):
    line = raw.rstrip("\r")
    if not line or line.startswith(":"):   # 空行 / 心跳注释
        continue
    if not line.startswith("data:"):
        continue                            # event: / id: / retry: 等字段
    payload = line[len("data:"):].lstrip(" ")
    if payload == "[DONE]":
        done = True
        break
    chunk = json.loads(payload)
    if chunk.get("error"):
        raise RuntimeError(chunk["error"])
    if chunk.get("usage"):
        usage = chunk["usage"]
    for choice in chunk.get("choices", []):
        delta = choice.get("delta") or {}
        content += delta.get("content") or ""
        for tc in delta.get("tool_calls") or []:
            slot = tool_calls.setdefault(tc["index"], {"id": "", "name": "", "arguments": ""})
            slot["id"] = tc.get("id") or slot["id"]
            fn = tc.get("function") or {}
            slot["name"] += fn.get("name") or ""
            slot["arguments"] += fn.get("arguments") or ""
        if choice.get("finish_reason"):
            finish_reason = choice["finish_reason"]

if not done:
    raise RuntimeError("stream ended without [DONE]")

for slot in tool_calls.values():
    slot["args"] = json.loads(slot["arguments"])   # 需处理解析失败
```

#### 2.2.8 异常、超时与重连

| 场景          | 表现                                              | 处理                                            |
|---------------|---------------------------------------------------|-------------------------------------------------|
| 建连前失败    | 返回普通 HTTP 错误体                              | 按 [三、错误响应](#三错误响应) 处理             |
| 建连后失败    | 可能收到 `data: {"error": {...}}`，也可能直接断流 | 检测 `error` 字段；未收到 `[DONE]` 一律视为失败 |
| 网络中断      | 连接被重置、读取超时                              | 丢弃半截结果，重新发起请求                      |
| 首 token 延迟 | 长时间无任何事件                                  | 放宽读超时；推理模型可能数十秒才返回首块        |
| 中间层缓冲    | 长时间无输出后一次性收到全部内容                  | 关闭代理缓冲，检查 `Content-Encoding`           |

要点：

1. **不要等待响应体结束再处理**，应边读边渲染。
2. SSE 不支持断点续传（无事件回放），断线后只能重发请求。
3. 重发可能重复计费，且结果不保证一致；建议记录 `X-Request-Id`。
4. 读超时应按「相邻 chunk 的最大间隔」设置，而不是整个请求的总时长。

---

### 2.3 finish_reason

| 值               | 含义                                                     |
|------------------|----------------------------------------------------------|
| `stop`           | 模型自然结束，或命中 `stop` 序列                         |
| `length`         | 达到 `max_tokens` / `max_completion_tokens` 或上下文上限 |
| `tool_calls`     | 模型发起工具调用                                         |
| `content_filter` | 内容被安全策略过滤                                       |
| `function_call`  | 旧版函数调用（已废弃）                                   |

---

### 2.4 usage

| 字段                                         | 类型      | 必填 | 说明                               |
|----------------------------------------------|-----------|------|------------------------------------|
| `prompt_tokens`                              | `integer` | ✅   | 输入 token 数                      |
| `completion_tokens`                          | `integer` | ✅   | 输出 token 数                      |
| `total_tokens`                               | `integer` | ✅   | 总 token 数（prompt + completion） |
| `prompt_tokens_details`                      | `object`  | ❌   | 输入明细                           |
| `prompt_tokens_details.cached_tokens`        | `integer` | ❌   | 命中缓存的输入 token 数            |
| `prompt_tokens_details.audio_tokens`         | `integer` | ❌   | 音频输入 token 数                  |
| `completion_tokens_details`                  | `object`  | ❌   | 输出明细                           |
| `completion_tokens_details.reasoning_tokens` | `integer` | ❌   | 推理 token 数                      |
| `completion_tokens_details.audio_tokens`     | `integer` | ❌   | 音频输出 token 数                  |

> `total_tokens = prompt_tokens + completion_tokens`；流式响应中 usage 仅在开启 `include_usage` 时返回。

---

## 三、错误响应

### 3.1 错误对象

非 2xx 响应返回统一的错误结构：

```json
{
  "error": {
    "message": "Invalid value for 'temperature': expected a number between 0 and 2.",
    "type": "invalid_request_error",
    "param": "temperature",
    "code": "invalid_value"
  }
}
```

| 字段            | 类型     | 必填 | 可空 | 说明                                     |
|-----------------|----------|------|------|------------------------------------------|
| `error.message` | `string` | ✅   | ❌   | 人类可读的错误描述                       |
| `error.type`    | `string` | ✅   | ❌   | 错误大类，见 [3.3](#33-错误类型与错误码) |
| `error.param`   | `string` | ❌   | ✅   | 出错的参数名；与参数无关时为 `null`      |
| `error.code`    | `string` | ❌   | ✅   | 具体错误码；无细分码时为 `null`          |

> 兼容实现可能只返回 `message` 与 `type`，或额外附带 `request_id`；解析时应容错。

### 3.2 HTTP 状态码

| 状态码 | 含义                         | 常见 `type`                               | 是否重试   |
|--------|------------------------------|-------------------------------------------|------------|
| `400`  | 请求格式或参数非法           | `invalid_request_error`                   | 否         |
| `401`  | 鉴权失败（Key 无效 / 缺失）  | `authentication_error`                    | 否         |
| `403`  | 权限不足或地区受限           | `permission_error`                        | 否         |
| `404`  | 资源不存在（模型、文件等）   | `not_found_error`                         | 否         |
| `409`  | 资源状态冲突                 | —                                         | 视业务而定 |
| `422`  | 参数语义校验失败（部分实现） | `invalid_request_error`                   | 否         |
| `429`  | 触发限流或额度耗尽           | `rate_limit_error` / `insufficient_quota` | 是（退避） |
| `500`  | 服务内部错误                 | `server_error`                            | 是         |
| `503`  | 服务过载或维护               | `service_unavailable`                     | 是         |

### 3.3 错误类型与错误码

| `type`                  | 典型 `code`                  | 触发场景                             | 处理建议                               |
|-------------------------|------------------------------|--------------------------------------|----------------------------------------|
| `invalid_request_error` | `invalid_value`              | 参数取值非法（如 `temperature = 3`） | 修正参数                               |
| `invalid_request_error` | `missing_required_parameter` | 缺少必填字段（如 `messages`）        | 补齐字段                               |
| `invalid_request_error` | `context_length_exceeded`    | 输入 + 输出超过上下文窗口            | 截断历史、压缩提示词或换更长上下文模型 |
| `invalid_request_error` | `content_policy_violation`   | 输入或输出触发内容安全策略           | 修改输入，勿原样重试                   |
| `authentication_error`  | `invalid_api_key`            | API Key 无效或已撤销                 | 检查 Key 与请求头                      |
| `not_found_error`       | `model_not_found`            | 模型不存在或当前账号无权限           | 核对模型名与权限                       |
| `rate_limit_error`      | `rate_limit_exceeded`        | 单位时间请求过多                     | 指数退避后重试                         |
| `insufficient_quota`    | `insufficient_quota`         | 账户额度耗尽                         | 充值；**不要重试**                     |
| `server_error`          | —                            | 服务端异常                           | 退避重试                               |

> `type` 与 `code` 由服务端实现定义，OpenAI 兼容层可能使用不同命名。稳定做法是： **优先看 HTTP 状态码，其次看 `code`，最后才匹配
`message` 文本**。

### 3.4 流式请求中的错误

流式请求存在两个阶段：

| 阶段            | 错误形式                                          |
|-----------------|---------------------------------------------------|
| 建立连接前      | 普通 HTTP 错误响应 + [3.1](#31-错误对象) 的错误体 |
| 已返回 `200` 后 | SSE 事件中携带错误，或直接断开连接                |

已开始流式输出后，错误可能以如下事件下发：

```jsonl
data: {"error":{"message":"The server had an error while processing your request.","type":"server_error","param":null,"code":null}}
```

判定与处理：

1. 只有收到 `data: [DONE]` 才表示本次生成完整结束。
2. 收到 `error` 事件、或流在 `[DONE]` 前结束，都应视为失败。
3. 部分实现把错误放在普通 chunk 的 `error` 字段中（`choices` 为空），解析时需兼容。
4. 流式失败时已产生的 token 通常仍会计费。

### 3.5 重试策略

| 状态                                  | 策略                                                            |
|---------------------------------------|-----------------------------------------------------------------|
| `429` / `500` / `503`                 | 指数退避 + 随机抖动；若响应含 `Retry-After`，按其指定的秒数等待 |
| `400` / `401` / `403` / `404` / `422` | 不重试，修正请求或凭证                                          |
| 网络超时 / 连接重置                   | 可重试，但需控制次数并记录请求 ID                               |
| `insufficient_quota`                  | 不重试，需充值或更换 Key                                        |

参考退避序列（秒）：`1 → 2 → 4 → 8 → 16`，最多 5 次；加入 ±20% 抖动避免惊群。

> Chat Completions 没有幂等键，重试可能产生重复计费与不一致结果；对写操作类工具调用尤其要谨慎。

---

*本文覆盖请求体、响应体、流式输出与错误响应；完整协议行为以 OpenAI 官方 API 参考为准。*

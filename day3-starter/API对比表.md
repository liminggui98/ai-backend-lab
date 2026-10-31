# 三家模型 API 对比

## 目录

| 段                           | 内容           | 
|------------------------------|----------------|
| [0](#0-已经确认的事实)       | 已确认的事实   |
| [A](#a-计价：价目与成本结论) | 计价：三层记账 | 
| [B](#b-能力矩阵)             | 能力矩阵       | 
| [C](#c-实测)                 | 实测数据与发现 | 
| [D](#d-错误码与限流)         | 错误码与限流   | 
| [E](#e-结论)                 | 结论           | 

跟读顺序建议： **E（结论）→ A（为什么）→ C（实测依据）**。

---

## 0. 已经确认的事实

| 事实                                                                          | 证据                                                |
|-------------------------------------------------------------------------------|-----------------------------------------------------|
| 三家都提供 **OpenAI 兼容端点**，同一份代码换 `baseUrl + model + key` 即可切换 | `day1-starter/HelloLLM.java` 跑通三家               |
| 百炼是**推理模型**：先流 `reasoning_content`，思考结束才流 `content`          | 它的「首 token」取决于思考长度，和另外两家不可比    |
| 百炼空间级 key **必须配空间级域名**，混用返回 `403 AccessDenied.Unpurchased`  | `day1-starter/HelloLLM.java` 配置                   |
| 首帧 `delta` 只带 `role`，`content` 是空串                                    | 用它算首 token 会把延迟算低，`LLMClient` 已跳过空帧 |
---

## A.计价：价目与成本结论

> 三家报价单位都是「元 / 百万 token」， **不需要换算**。要做的只有一件事：
> 按项目的真实输入输出结构，折算成「 **元 / 千次问答**」——单价不可比，千次成本才可比。
>
> 折算公式：`单次成本 = 输入 token ÷ 100 万 × 输入价 + 输出 token ÷ 100 万 × 输出价`，再 ×1000 得千次成本。

价目（2026-10-29 抄自官方定价页，单位：元/百万 token）

| 服务商   | 模型           | 时段   | 输入档    | 输入 | 缓存命中 | 输出 | 来源                                                                                                                                         |
|----------|----------------|--------|-----------|------|----------|------|----------------------------------------------------------------------------------------------------------------------------------------------|
| DeepSeek | deepseek-flash | 高峰   | 无上限    | 2    | 0.04     | 8    | https://api-docs.deepseek.com/zh-cn/quick_start/pricing                                                                                      |
| DeepSeek | deepseek-flash | 空闲   | 无上限    | 1    | 0.02     | 4    | https://api-docs.deepseek.com/zh-cn/quick_start/pricing                                                                                      |
| 智谱     | glm-4-flash    | 全时段 | 无上限    | 0    | —        | 0    | https://bigmodel.cn/console/modelcenter/square                                                                                               |
| 百炼     | qwen3.7-plus   | 全时段 | < 256K    | 2    | 0.4      | 8    | https://bailian.console.aliyun.com/cn-beijing/model/market/detail/qwen3.7-plus?serviceSite=asia-pacific-china&ref=search_result&kw=qwen3.7-p |
| 百炼     | qwen3.7-plus   | 全时段 | 256K ~ 1M | 6    | 1.2      | 24   | https://bailian.console.aliyun.com/cn-beijing/model/market/detail/qwen3.7-plus?serviceSite=asia-pacific-china&ref=search_result&kw=qwen3.7-p |

**备注**

- **免费不等于零成本**
- 智谱 `glm-4-flash` <a href="https://bigmodel.cn/usercenter/proj-mgmt/rate-limits">速率限制</a>

---

## B. 能力矩阵

> **查官方文档或代码确认**。
> ✅ 支持　❌ 不支持　⚠️ 有条件 / 文档未明确

| 能力                                       | DeepSeek | 智谱 | 百炼 | 关键限制 |
|--------------------------------------------|----------|------|------|----------|
| 流式（SSE）                                |          |      |      |          |
| 推理过程单独返回（`reasoning_content`）    |          |      |      |          |
| `response_format: json_object`             |          |      |      |          |
| `response_format: json_schema`（严格模式） |          |      |      |          |
| 工具调用（`tools`）                        |          |      |      |          |
| 并行工具调用                               |          |      |      |          |
| 多轮 `tool` 角色回传                       |          |      |      |          |
| 图片输入（多模态）                         |          |      |      |          |
| 自带联网 / 检索                            |          |      |      |          |
| 上下文缓存                                 |          |      |      |          |
| `tool_choice` 可选值                       |          |      |      |          |

## C. 实测

> 跑法：每组连跑 3 次取中位数。
>
> ```bash
> java LLMClient.java --provider all
> ```
>
> 统一条件：同一 prompt（「用一句话解释什么是 RAG」+ system「你是一个简洁的技术助手，回答不超过三句话」）、`temperature=0.3`、
> `max_tokens=1024`、流式、JDK 21、本机。
> 百炼本轮统一用 `qwen3.7-plus`（与 A1 报价同一个模型）。

**逐次原始数据**

| 轮次 | 时刻 | provider | 模型（实际返回） | 首 token | 总耗时 | tokens(p/c/t) | tok/s |
|------|------|----------|------------------|----------|--------|---------------|-------|
| 1    |      | DeepSeek |                  |          |        |               |       |
| 1    |      | 智谱     |                  |          |        |               |       |
| 1    |      | 百炼     |                  |          |        |               |       |
| 2    |      | DeepSeek |                  |          |        |               |       |
| 2    |      | 智谱     |                  |          |        |               |       |
| 2    |      | 百炼     |                  |          |        |               |       |
| 3    |      | DeepSeek |                  |          |        |               |       |
| 3    |      | 智谱     |                  |          |        |               |       |
| 3    |      | 百炼     |                  |          |        |               |       |

**中位数**

| provider | 模型             | 首 token | 总耗时 | tokens(p/c/t) | tok/s | 抖动 |
|----------|------------------|----------|--------|---------------|-------|------|
| DeepSeek | `deepseek-flash` |          |        |               |       |      |
| 智谱     | `glm-4-flash`    |          |        |               |       |      |
| 百炼     | `qwen3.7-plus`   |          |        |               |       |      |

> `tok/s` 按「输出 token ÷（总耗时 − 首 token）」由中位数现算，与「逐次 tok/s 取中位数」会差 1–10%，属正常。
---

## D. 错误码与限流

> 用错 key / 超额 / 超长输入各打一次，把 **真实返回体** 记下来。

| provider | 场景           | HTTP | 错误码 / `type` | 可重试？ |
|----------|----------------|------|-----------------|----------|
| DeepSeek | key 错         |      |                 |          |
| DeepSeek | 余额不足       |      |                 |          |
| DeepSeek | 限流           |      |                 |          |
| 智谱     | key 错         |      |                 |          |
| 百炼     | 空间域名不匹配 |      |                 |          |
| 任意     | 上下文超限     |      |                 |          |

---

## E. 结论

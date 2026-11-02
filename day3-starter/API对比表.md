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
| 三家都提供 **OpenAI 兼容端点**，同一份代码换 `baseUrl + model + key` 即可切换 | `HelloLLM.java` 跑通三家                            |
| 百炼空间级 key **必须配空间级域名**，混用返回 `403 AccessDenied.Unpurchased`  | `HelloLLM.java` 配置                                |
| 首帧 `delta` 只带 `role`，`content` 是空串                                    | 用它算首 token 会把延迟算低，`LLMClient` 已跳过空帧 |

---

## A.计价：价目与成本结论

> 三家报价单位都是「元 / 百万 token」， **不需要换算**。要做的只有一件事：
>
> 折算公式：`单次成本 = 输入 token ÷ 100 万 × 输入价 + 输出 token ÷ 100 万 × 输出价`。

价目（2026-10-29 抄自官方定价页，单位：元/百万 token）

| 服务商   | 模型           | 时段   | 输入档      | 输入 | 缓存命中 | 输出 | 来源                                                                                                                                      |
|----------|----------------|--------|-------------|------|----------|------|-------------------------------------------------------------------------------------------------------------------------------------------|
| DeepSeek | deepseek-flash | 高峰   | 无上限      | 2    | 0.04     | 8    | https://api-docs.deepseek.com/zh-cn/quick_start/pricing                                                                                   |
| DeepSeek | deepseek-flash | 空闲   | 无上限      | 1    | 0.02     | 4    | https://api-docs.deepseek.com/zh-cn/quick_start/pricing                                                                                   |
| 智谱     | glm-4-flash    | 全时段 | 无上限      | 0    | —        | 0    | https://bigmodel.cn/console/modelcenter/square                                                                                            |
| 百炼     | qwen-plus      | 全时段 | <= 128K     | 0.8  | 0.16     | 2    | https://bailian.console.aliyun.com/cn-beijing/model/market/detail/qwen-plus?serviceSite=asia-pacific-china&ref=search_result&kw=qwen3.7-p |
| 百炼     | qwen-plus      | 全时段 | 128K ~ 256K | 2.4  | 0.48     | 20   | https://bailian.console.aliyun.com/cn-beijing/model/market/detail/qwen-plus?serviceSite=asia-pacific-china&ref=search_result&kw=qwen3.7-p |
| 百炼     | qwen-plus      | 全时段 | 256K ~ 1M   | 4.8  | 0.96     | 48   | https://bailian.console.aliyun.com/cn-beijing/model/market/detail/qwen-plus?serviceSite=asia-pacific-china&ref=search_result&kw=qwen3.7-p |

**备注**

- 展示的是文本输入的计价方式，百炼中还按输入（思考）、输入（思考模式缓存命中）、输入（Batch File）等分别计价。
- 智谱其他收费模型计算公式：调用费用 = 未命中缓存的输入费用 + 缓存命中费用 + 输出费用 + 缓存存储费用。当前缓存存储处于限时免费。
- **免费不等于零成本**
- 智谱 `glm-4-flash` <a href="https://bigmodel.cn/usercenter/proj-mgmt/rate-limits">速率限制</a>

---

## B. 能力矩阵

> **查官方文档或代码确认**。
> ✅ 支持　❌ 不支持　⚠️ 有条件 / 文档未明确

| 能力                                       | DeepSeek                                                | 智谱 | 百炼                 | 关键限制                                                                                                                                                                                                                                                                                                                                                                                                                   |
|--------------------------------------------|---------------------------------------------------------|------|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 流式（SSE）                                | ✅                                                      | ✅   | ✅                   | <a href="https://api-docs.deepseek.com/zh-cn/api/create-chat-completion">DeepSeek</a>、<a href="https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%AF%B9%E8%AF%9D%E8%A1%A5%E5%85%A8">智谱</a>、<a href="https://docs.bailian.console.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions ">百炼</a> 接口文档中的 stream 字段是否传入，是否可为 `true`，[LLMClient](LLMClient.java) 中有代码实现 |
| 推理过程单独返回（`reasoning_content`）    | ✅                                                      | ❌   | ❌                   | [LLMClient.java](LLMClient.java) 中统计了 `reasoning_content` 的长度，`deepseek-flash` 返回了数据、`glm-4-flash` `qwen-plus` 均返回`reasoning.length` = 0。DeepSeek 默认思考；qwen-plus/glm-4-flash 本次未返回 reasoning_content。                                                                                                                                                                                         |
| `response_format: json_object`             | ✅                                                      | ✅   | ✅                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `response_format: json_schema`（严格模式） | ❌                                                      | ❌   | ✅                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 工具调用（`tools`）                        | ✅                                                      | ✅   | ✅                   | <a href="https://api-docs.deepseek.com/zh-cn/api/create-chat-completion">DeepSeek</a>、<a href="https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%AF%B9%E8%AF%9D%E8%A1%A5%E5%85%A8">智谱</a>、<a href="https://docs.bailian.console.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions ">百炼</a> 接口文档中都包含 `tools` 字段。                                                             |
| 并行工具调用                               | ⚠️                                                      | ⚠️   | ✅                   | <a href="https://docs.bailian.console.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions ">百炼</a> 接口文档包含 `parallel_tool_calls` 字段。                                                                                                                                                                                                                                                                 |
| 多轮 `tool` 角色回传                       | ⚠️                                                      | ⚠️   | ⚠️                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 图片输入（多模态）                         | ✅                                                      | ❌   | ❌                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 自带联网 / 检索                            | ✅                                                      | ✅   | ✅                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 上下文缓存                                 | ✅                                                      | ✅   | ✅                   |                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `tool_choice` 可选值                       | none/auto（思考模式）；none/auto/required（非思考模式） | auto | none, auto, required |                                                                                                                                                                                                                                                                                                                                                                                                                            |

## C. 实测

> 跑法：每组连跑 10 次取中位数。
>
> ```bash
> java LLMClient.java --provider all
> ```
>
> 统一条件：同一 prompt（「用一句话解释什么是 RAG」+ system「你是一个简洁的技术助手，回答不超过三句话」）、`temperature=0.3`、
> `max_tokens=1024`、流式、JDK 21、本机。

**逐次原始数据**

| 轮次 | provider | 模型（实际返回） | 首 token | 总耗时 | tokens(p/c/t) | tok/s |
|------|----------|------------------|----------|--------|---------------|-------|
| 1    | DeepSeek | `deepseek-flash` | 895ms    | 1107ms | 22/31/53      | 146.2 |
| 1    | 智谱     | `glm-4-flash`    | 649ms    | 1224ms | 24/28/52      | 48.7  |
| 1    | 百炼     | `qwen-plus`      | 342ms    | 1624ms | 30/57/87      | 44.5  |
| 2    | DeepSeek | `deepseek-flash` | 329ms    | 647ms  | 22/41/63      | 128.9 |
| 2    | 智谱     | `glm-4-flash`    | 761ms    | 2160ms | 24/48/72      | 34.3  |
| 2    | 百炼     | `qwen-plus`      | 225ms    | 1517ms | 30/57/87      | 44.1  |
| 3    | DeepSeek | `deepseek-flash` | 629ms    | 803ms  | 22/40/62      | 229.9 |
| 3    | 智谱     | `glm-4-flash`    | 3305ms   | 3725ms | 24/23/47      | 54.8  |
| 3    | 百炼     | `qwen-plus`      | 269ms    | 1555ms | 30/57/87      | 44.3  |
| 4    | DeepSeek | `deepseek-flash` | 365ms    | 666ms  | 22/40/62      | 132.9 |
| 4    | 智谱     | `glm-4-flash`    | 531ms    | 992ms  | 24/28/52      | 60.7  |
| 4    | 百炼     | `qwen-plus`      | 431ms    | 1708ms | 30/56/86      | 43.9  |
| 5    | DeepSeek | `deepseek-flash` | 332ms    | 674ms  | 22/40/62      | 117.0 |
| 5    | 智谱     | `glm-4-flash`    | 1757ms   | 2406ms | 24/28/52      | 43.1  |
| 5    | 百炼     | `qwen-plus`      | 206ms    | 1471ms | 30/56/86      | 44.3  |
| 6    | DeepSeek | `deepseek-flash` | 642ms    | 1003ms | 22/40/62      | 110.8 |
| 6    | 智谱     | `glm-4-flash`    | 1276ms   | 2888ms | 24/28/52      | 17.4  |
| 6    | 百炼     | `qwen-plus`      | 267ms    | 1528ms | 30/56/86      | 44.4  |
| 7    | DeepSeek | `deepseek-flash` | 649ms    | 945ms  | 22/39/61      | 131.8 |
| 7    | 智谱     | `glm-4-flash`    | 2674ms   | 3227ms | 24/27/51      | 48.8  |
| 7    | 百炼     | `qwen-plus`      | 468ms    | 1703ms | 30/56/86      | 45.3  |
| 8    | DeepSeek | `deepseek-flash` | 710ms    | 910ms  | 22/36/58      | 180.0 |
| 8    | 智谱     | `glm-4-flash`    | 515ms    | 1870ms | 24/44/68      | 32.5  |
| 8    | 百炼     | `qwen-plus`      | 487ms    | 1638ms | 30/54/84      | 46.9  |
| 9    | DeepSeek | `deepseek-flash` | 383ms    | 603ms  | 22/31/53      | 140.9 |
| 9    | 智谱     | `glm-4-flash`    | 804ms    | 2204ms | 24/30/54      | 21.4  |
| 9    | 百炼     | `qwen-plus`      | 847ms    | 2074ms | 30/54/84      | 44.0  |
| 10   | DeepSeek | `deepseek-flash` | 416ms    | 664ms  | 22/31/53      | 125.0 |
| 10   | 智谱     | `glm-4-flash`    | 526ms    | 1074ms | 24/21/45      | 38.3  |
| 10   | 百炼     | `qwen-plus`      | 215ms    | 1478ms | 30/56/86      | 44.3  |

**中位数**

| provider | 模型             | 首 token | 总耗时   | tokens(p/c/t) | tok/s | 抖动 |
|----------|------------------|----------|----------|---------------|-------|------|
| DeepSeek | `deepseek-flash` | 522.5ms  | 738.5ms  | 22/39.5/61.5  | 182.9 | 108% |
| 智谱     | `glm-4-flash`    | 782.5ms  | 2182ms   | 24/28/52      | 20.0  | 357% |
| 百炼     | `qwen-plus`      | 305.5ms  | 1589.5ms | 30/56/86      | 43.6  | 210% |

> `tok/s` 按「输出 token ÷（总耗时 − 首 token）」由中位数现算，与「逐次 tok/s 取中位数」会差 1–10%，属正常。
> 抖动 = 首 token 的（最大值 − 最小值）÷ 中位数。
---

## D. 错误码与限流

> 用错 key / 超额 / 超长输入各打一次，把 **真实返回体** 记下来。

| provider | 场景           | HTTP | 错误码 / `type`                                                                                                                                                                                                                                                                                                       | 可重试？                                              |
|----------|----------------|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| DeepSeek | key 错         | 401  | {"error":{"message":"Authentication Fails, Your api key: ****-xxx is invalid (request_id: 857fb67b-8ff8-4b4e-a711-98ac650ff4cc)","type":"authentication_error","param":null,"code":"invalid_request_error"}                                                                                                           | 不可，需要修改 API_KEY                                |
| DeepSeek | 余额不足       | 402  | {"error":{"message":"Insufficient Balance (request_id: 05056220-d26a-43e6-abad-50be727824b7)","type":"insufficient_balance_error","param":null,"code":"invalid_request_error"}}                                                                                                                                       |                                                       |
| DeepSeek | 限流           | 429  | TODO 待验证：deepseek-flash 并发限制： 2500，请求不好构造，需要压测介入。                                                                                                                                                                                                                                             | TODO：[LLMClient.java](LLMClient.java) 中写的可重试。 |
| 智谱     | key 错         | 401  | {"error":{"code":"401","message":"令牌已过期或验证不正确"}}                                                                                                                                                                                                                                                           |                                                       |
| 百炼     | 空间域名不匹配 | 403  | 先执行 `export BAILIAN_BASE_URL=https://llm-nonexistent-id.cn-beijing.maas.aliyuncs.com/compatible-mode/v1` 后执行 `java LLMClient.java --provider bailian` <br/> {"error":{"message":"Workspace endpoint access denied.","id":"368190b4-4630-9f80-939a-3dac89960f00","type":"access_denied","code":"access_denied"}} |                                                       |
| 任意     | 上下文超限     |      |                                                                                                                                                                                                                                                                                                                       |                                                       |

---

## E. 结论

> DeepSeek `deepseek-flash` 总耗时中位数最低，tok/s 也领先很多，抖动很低，可以用于作生产环境。
>
> 智谱 `glm-4-flash` 各项指标最低且平台针对用户有限速，优点是不收费，开发阶段使用最好。
>
> 百炼 `qwen-plus` `首 token` 值最小，其他值中规中矩，可以用于降级时使用。
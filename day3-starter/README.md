# 学习内容

> 当天交付： **三家 API 对比表 + `LLMClient.java`（多 provider 统一封装）+ GitHub 主页**。

## 交付物

| 文件                               | 内容                                                    | 状态                                 |
|------------------------------------|---------------------------------------------------------|--------------------------------------|
| [`API对比表.md`](API对比表.md)     | 三家 API 对比                                           | ✅ A/B/C/D/E 已填，C 表为 3 次中位数 |
| [`LLMClient.java`](LLMClient.java) | 零依赖统一客户端：多 provider、流式、读超时、usage 记账 | ✅ 可运行                            |
| `.env.example`                     | provider 分开命名的 key                                 | ✅                                   |
| [`github/`](github/)               | 4 份 README 成品：主页 + 3 个项目仓库                   | ✅ 可直接粘贴                        |

## GitHub

[`github/`](github/) 里的文件是 **成品**，复制过去就是对应仓库的 `README.md`：

| 文件                                                           | 复制到哪                                     | 说明                          |
|----------------------------------------------------------------|----------------------------------------------|-------------------------------|
| [`github/profile-README.md`](github/profile-README.md)         | `liminggui98/liminggui98` 仓库的 `README.md` | 主页，含 8 周进度表与项目索引 |
| [`github/rag-knowledge-base.md`](github/rag-knowledge-base.md) | 新建仓库 `rag-knowledge-base`                | 项目一（W3–W4）               |
| [`github/agent-workflow.md`](github/agent-workflow.md)         | 新建仓库 `agent-workflow`                    | 项目二（W5）                  |
| [`github/llm-gateway.md`](github/llm-gateway.md)               | 新建仓库 `llm-gateway`                       | 项目三（W6）                  |

仓库本体的 `README.md` 已经更新（进度表、目录、运行方式）。

## 为什么要有 LLMClient

Day 1 的 `HelloLLM.java` 只证明「能调通一次」。这个类解决的是「三家能当成一家用」：

1. **provider 适配**：一份 `OpenAiCompatible` 实现，靠 `baseUrl + model + key` 区分三家，调用方拿到的永远是 `Reply`；
2. **流式解析统一**：三家的 chunk 归一成 `Chunk{delta, reasoning, finishReason}`，推理模型的思考内容和答案分开；
3. **超时归一**：`HttpRequest.timeout` 只管到响应头， **body 流另设读空闲看门狗**（Day 1 在百炼上卡过两分钟）；429 / 5xx
   退避重试；
4. **usage 记账**：每次带回 prompt / completion / total，W6 的成本模型直接用这份数据。


## 运行

```bash
cd day3-starter

# 1) 离线自检：不联网、不花钱，验证 JSON 解析和空帧处理
java LLMClient.java --selftest

# 2) 配 key（三家分开命名，不要再用 LLM_* 混着放）
export DEEPSEEK_API_KEY=sk-xxx
export ZHIPU_API_KEY=xxx
export BAILIAN_API_KEY=sk-ws-xxx
export BAILIAN_BASE_URL=https://ws-xxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1

# 3) 跑实测（对比表 C 的数据来源）
java LLMClient.java                      # 所有已配置的 provider，流式
java LLMClient.java --provider deepseek  # 只跑一家
java LLMClient.java --provider all --chat  # 非流式，usage 更准
```

输出长这样：

```
provider  model               首token       总耗时    tokens(p/c/t)   tok/s
------------------------------------------------------------------------
RAG（检索增强生成）是一种让大语言模型在生成回答前先从外部知识库检索相关信息、再结合检索内容生成答案的技术，可提升回答的准确性和时效性。
deepseek  deepseek-flash       879ms    1169ms         22/40/62   137.9
zhipu     glm-4-flash          672ms    2269ms         24/46/70    28.8
```

可选参数：`--prompt "..."`、`--idle-ms 20000`、`--max-tokens 1024`、`--chat`。

## 已知限制

- 流式模式下 `usage` 不一定有（看 provider 是否在尾帧返回），拿不到就标 `-`，成本核算用 `--chat` 的结果；
- JSON 解析器只覆盖 OpenAI 兼容响应用到的子集，不追求通用性；
- `--provider all` 是串行跑的，别用它做并发压测。

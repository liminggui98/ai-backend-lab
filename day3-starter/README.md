# Day 3 Starter（10/31）

> 当天交付：**三家 API 对比表 + `LLMClient.java`（多 provider 统一封装）+ GitHub 主页**。
> 计划里这一天是三个时段，实际可以拆开做：对比表 → 客户端 → 主页。

## 交付物

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| [`API对比表.md`](API对比表.md) | 计价三层记账 + 实测 + 结论 | ✅ A/B/C/D/E 已填，C 表为 3 次中位数 |
| [`LLMClient.java`](LLMClient.java) | 零依赖统一客户端：多 provider、流式、读超时、usage 记账 | ✅ 可运行 |
| `.env.example` | provider 分开命名的 key，避免 Day 1 的覆盖坑 | ✅ |
| [`github/`](github/) | 4 份 README 成品：主页 + 3 个项目仓库 | ✅ 可直接粘贴 |

## 晚上：GitHub（模板已备好）

[`github/`](github/) 里的文件是**成品**，复制过去就是对应仓库的 `README.md`：

| 文件 | 复制到哪 | 说明 |
| --- | --- | --- |
| [`github/profile-README.md`](github/profile-README.md) | `liminggui98/liminggui98` 仓库的 `README.md` | 主页，含 8 周进度表与项目索引 |
| [`github/rag-knowledge-base.md`](github/rag-knowledge-base.md) | 新建仓库 `rag-knowledge-base` | 项目一（W3–W4） |
| [`github/agent-workflow.md`](github/agent-workflow.md) | 新建仓库 `agent-workflow` | 项目二（W5） |
| [`github/llm-gateway.md`](github/llm-gateway.md) | 新建仓库 `llm-gateway` | 项目三（W6） |

仓库本体的 `README.md` 已经更新（进度表、目录、运行方式）。

建仓库（网页操作，或用 `gh`）：

```bash
gh repo create liminggui98/rag-knowledge-base --public --description "企业知识库 RAG：混合检索 + Rerank + 评测"
# 然后
cp day3-starter/github/rag-knowledge-base.md ../rag-knowledge-base/README.md
```

> 三份项目 README 都标了「**状态：骨架**」和计划上线时间，指标栏一律 `【】`。
> 这是刻意的：骨架阶段不写没跑过的数字，做完再改状态。

## 为什么要有 LLMClient

Day 1 的 `HelloLLM.java` 只证明「能调通一次」。这个类解决的是「三家能当成一家用」：

1. **provider 适配**：一份 `OpenAiCompatible` 实现，靠 `baseUrl + model + key` 区分三家，调用方拿到的永远是 `Reply`；
2. **流式解析统一**：三家的 chunk 归一成 `Chunk{delta, reasoning, finishReason}`，推理模型的思考内容和答案分开；
3. **超时归一**：`HttpRequest.timeout` 只管到响应头，**body 流另设读空闲看门狗**（Day 1 在百炼上卡过两分钟）；429 / 5xx 退避重试；
4. **usage 记账**：每次带回 prompt / completion / total，W6 的成本模型直接用这份数据。

> 这一层是**临时的**——W1 周一上 Spring AI 后会换掉实现，但 `Chunk` / `Reply` 这层抽象要保留。
> 所以今天别把业务逻辑写进 provider 里。

## 运行

```bash
cd day3-starter

# 1) 离线自检：不联网、不花钱，验证 JSON 解析和空帧处理
java LLMClient.java --selftest

# 2) 配 key（三家分开命名，不要再用 LLM_* 混着放）
export DEEPSEEK_API_KEY=sk-xxx
export ZHIPU_API_KEY=xxx
export BAILIAN_API_KEY=sk-ws-xxx
export BAILIAN_BASE_URL=https://ws-xxxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1

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

## 今日状态（2026-10-29）

| 时段 | 计划 | 状态 | 说明 |
| --- | --- | --- | --- |
| 上午 | 三家 API 对比 | ✅ | 价目抄全并折算成「元/千次」（DeepSeek 峰/谷 × 缓存、百炼 256K/1M 两档、智谱免费）；C 表 **3 次实测取中位数** |
| 下午 | `LLMClient` | ✅ | 编译通过、14 项自检通过；**真接口跑过 3 轮 × 三家**（见 C 表） |
| 晚上 | GitHub 主页 + 3 仓库 | ✅ | 4 份 README 已上线：主页 + 3 个项目仓库（各只含 README，符合「骨架」定义） |

**结论一句话**：生产主线 DeepSeek-flash，批处理走空闲时段（省一半），降级用百炼（低延迟降到 `qwen-plus`，要推理才用 `qwen3.7-plus`），智谱只做开发档。
成本口径：RAG 典型场景（3.5K 输入）**高峰 10.2 元 / 千次**，约 1 分钱一次；空闲时段 5.1 元。

**还差 1 件事**：

1. B 表 2 项实测（百炼 `json_schema` 是否真严格、DeepSeek 并行工具调用返回几个 `tool_calls`）——W2 前做即可。

> 另外两项已记进 C3、不阻塞 Day 3：长回答（500+ token）对比、推理 token / 答案 token 分开计时。

## 当天怎么安排

| 时段 | 做什么 | 完成标志 |
| --- | --- | --- |
| 上午 | 查三家定价页，填 `API对比表.md` 的 A1 价目 + B / D 两张表 | A2 的场景成本按公式算出来，写进表里 |
| 下午 | 读 `LLMClient.java`，跑通三家；把 C 表实测数据填进去 | 三家都能出数，百炼的推理 token 单独记 |
| 晚上 | GitHub 主页 + 3 个项目仓库骨架 | 主页 README 有 8 周进度，3 个 repo 有说明 |

> 建议先跑 `--selftest` 再动网络。解析器如果有问题，先修解析，别去怀疑 key。

## 验收（沿用 W0 的标准）

- [ ] 能用 Java 调通 **3 家**模型的 chat completion 并流式打印
- [ ] 换 provider **只改环境变量**，不改一行代码
- [ ] 流式连接卡住时，能在 `idle-ms` 内主动失败，而不是永久挂起
- [ ] 对比表 E 节能一句话说清「主线用谁、降级用谁」

## 今天不做的事

- 不建 Spring Boot 工程（W1 周一再做）
- 不做 Prompt 模板 / 工具调用（W2）
- 不写结构化输出的完整实现（今天只验证「支持不支持」，实现留到 W2）

## 已知限制

- 流式模式下 `usage` 不一定有（看 provider 是否在尾帧返回），拿不到就标 `-`，成本核算用 `--chat` 的结果；
- JSON 解析器只覆盖 OpenAI 兼容响应用到的子集，不追求通用性；
- `--provider all` 是串行跑的，别用它做并发压测。

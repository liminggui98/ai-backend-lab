# 学习内容

> 当天交付： **三家 API 对比表 + `LLMClient.java`（多 provider 统一封装）+ GitHub 主页**。

## 交付物

| 文件                               | 内容                                                    | 
|------------------------------------|---------------------------------------------------------|
| [`API对比表.md`](API对比表.md)     | 三家 API 对比                                           |
| [`LLMClient.java`](LLMClient.java) | 零依赖统一客户端：多 provider、流式、读超时、usage 记账 | 
| `.env.example`                     | provider 分开命名的 key                                 | 
| [`github/`](github/)               | 4 份 README 成品：主页 + 3 个项目仓库                   | 

## GitHub

| 操作                                                     |
|----------------------------------------------------------|
| 新建仓库，`liminggui98/liminggui98`，写 `README.md` 文档 |
| 新建仓库 `rag-knowledge-base`，写`README.md` 文档        |
| 新建仓库 `agent-workflow`，写`README.md` 文档            |
| 新建仓库  `llm-gateway`，写`README.md` 文档              |

仓库本体的 `README.md` 已经更新（进度表、目录、运行方式）。

## 为什么要有 LLMClient

`HelloLLM.java` 只证明「能调通一次」。这个类解决的是「三家能当成一家用」：

1. **provider 适配**：一份 `OpenAiCompatible` 实现，靠 `baseUrl + model + key` 区分三家，调用方拿到的永远是 `Reply`；
2. **流式解析统一**：三家的 chunk 归一成 `Chunk{delta, reasoning, finishReason}`，推理模型的思考内容和答案分开；
3. **超时归一**：`HttpRequest.timeout` 只管到响应头， **body 流另设读空闲看门狗**（Day 1 在百炼上卡过两分钟）；429 / 5xx
   退避重试；
4. **usage 记账**：每次带回 prompt / completion / total，成本模型直接用这份数据。

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
provider  model               首token       总耗时    tokens(p/c/t)   tok/s content.length reasoning.length
---------------------------------------------------------------------------------------------------
deepseek  deepseek-flash       512ms     820ms         22/36/58 116.9        62         0
zhipu     glm-4-flash          606ms    1436ms         24/47/71 56.6        91         0
bailian   qwen-plus            329ms    1614ms         30/57/87 44.4       112         0
```

可选参数：`--prompt "..."`、`--idle-ms 20000`、`--max-tokens 1024`、`--chat`。

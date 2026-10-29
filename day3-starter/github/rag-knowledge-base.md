# rag-knowledge-base

> 把企业制度 / 合同这类长文档，做成**能引用出处、能评测召回**的问答系统。
>
> **状态：骨架**　创建于 2026-10-29　·　计划 W3–W4 实现，目标 11 月中上线

## 为什么做这个

企业知识库问答的难点不在「接一个大模型」，而在三件事：

1. **召不准**：文档切碎了语义就断了，切大了噪声就多了；
2. **答了不该答的**：模型会编，需要引用溯源和置信度拒答；
3. **改不动**：换了切分策略 / Prompt，没有基线就不知道是变好还是变坏。

所以这个项目的重点不是「能答」，而是**每次改动都可量化对比**。

## 架构

```
离线 ingest
  PDF/Word/Excel/HTML → Apache Tika 解析 → 父子块切分 → bge-m3 向量化 → pgvector

在线问答
  用户问题 → 向量检索 ┐
            BM25 检索 ┘→ RRF 融合 → Rerank → LLM 生成 → 引用溯源 + 拒答阈值
```

## 核心设计（每条都要有对比实验）

| 决策 | 备选方案 | 怎么验证 |
| --- | --- | --- |
| 切分策略 | 固定长度 / 按标题 / 父子块 | 同一批问题看 Top-5 召回 |
| chunk_size / overlap | 多组取值 | 召回率 × 上下文成本 的折中 |
| 检索方式 | 纯向量 / 纯 BM25 / 混合 + RRF | 三组对照 |
| 是否加 Rerank | Top-20 → Rerank → Top-5 | 看 Top-5 提升幅度与延迟代价 |
| 拒答策略 | 相似度阈值 / 模型自评 | 幻觉率 vs 拒答率 |

## 指标（**跑完再填，不许预估**）

| 指标 | 目标 | 实测 |
| --- | --- | --- |
| Top-5 召回率 | ≥ 80% | 【】 |
| 引用准确率 | ≥ 90% | 【】 |
| 首 token | < 1.5s | 【】 |
| P95 端到端 | < 3s | 【】 |
| 千次问答成本 | 【】 | 【】 |
| 人工抽检幻觉率 | < 10% | 【】 |

## 评测集

- **golden set**：50 条「问题 + 标准答案 + 应命中的文档段」，人工标；
- **自动化**：RAGAS（faithfulness / answer relevancy / context precision）；
- **回归**：每次改切分或 Prompt，跑一遍全量，记录指标变化。

## 快速开始

> W3 完成后补充。目前仓库只有本文件。

```bash
# 计划中的启动方式
docker compose up -d          # pgvector
./mvnw spring-boot:run        # 应用
```

## Roadmap

- [ ] W3 · Tika 解析管道（4 种格式 + 失败兜底）
- [ ] W3 · 切分策略对比实验（固定 / 按标题 / 父子块）
- [ ] W3 · Embedding 批量向量化 + 入库
- [ ] W3 · `/search` 接口 + metadata 过滤，10 万级压测
- [ ] W4 · BM25 + 向量 + RRF 混合检索
- [ ] W4 · Rerank + 引用溯源 + 拒答阈值
- [ ] W4 · 50 条 golden set + RAGAS 评测基线
- [ ] W4 · Docker Compose 部署 + 3 分钟 demo

## 技术栈

Java 21 · Spring Boot 3 · Spring AI · PgVectorStore · Apache Tika · PostgreSQL(tsvector + zhparser) · Redis · Docker

## 说明

本仓库的指标与结论**全部来自实测**。未跑过的项写 `【】`，不写估计值。

# 李明桂 · Li Minggui

> Java 后端 9 年 → **AI 应用后端（LLM Application Backend）**
> 北京 · 正在做 8 周转型冲刺（2026.10.29 – 12.27）

## 现在在做什么

用 Java 把 LLM 能力做成**能上线、能计费、可观测**的系统，而不是 demo。
8 周里每周产出一样能跑起来的东西，进度实时更新在这个主页和 [ai-backend-lab](https://github.com/liminggui98/ai-backend-lab)。

| 周 | 主题 | 产出 | 状态 |
| --- | --- | --- | --- |
| W0 | 环境 + 市场调研 | pgvector 环境、三家 provider 流式调用、31 份 JD 词频分析 | ✅ 完成 |
| W1 | Java 21 + Spring Boot 3 + Spring AI | 可切换 3 家 provider 的流式服务 | 🚧 进行中 |
| W2 | Prompt / Tool Calling / Memory | 项目 0：带工具与记忆的 AI 助手 | ⬜ |
| W3–W4 | RAG 全链路 | 项目一：企业知识库问答（混合检索 + 重排 + 评测） | ⬜ 计划 11 月中 |
| W5 | Agent 编排 | 项目二：履约异常处理 Agent（状态机 + 审批 + 注入防护） | ⬜ 计划 11 月底 |
| W6 | 网关与工程化 | 项目三：LLM 统一网关（路由 / 限流 / 降级 / 计费） | ⬜ 计划 12 月初 |
| W7–W8 | 面试冲刺 | 62 题答案库 + 3 套系统设计 + 模拟面试 | ⬜ |

## 项目

| 项目 | 一句话 | 技术栈 | 状态 |
| --- | --- | --- | --- |
| [ai-backend-lab](https://github.com/liminggui98/ai-backend-lab) | 8 周冲刺实战仓库，每个目录是一次可运行的交付 | Java 21 · HttpClient · Spring AI（W1 起） | 🚧 |
| [rag-knowledge-base](https://github.com/liminggui98/rag-knowledge-base) | 企业知识库问答：父子块切分 + 混合检索 + Rerank + RAGAS 评测 | Spring AI · PgVector · Apache Tika | 骨架 |
| [agent-workflow](https://github.com/liminggui98/agent-workflow) | 物流履约异常处理 Agent：状态机 + 工具幂等 + 人工审批 | LangChain4j · 自研状态机 · MCP | 骨架 |
| [llm-gateway](https://github.com/liminggui98/llm-gateway) | 多 provider 统一网关：兼容层 + 按成本路由 + 限流熔断 | Spring Boot 3 · Redis · Micrometer | 骨架 |

> 骨架阶段的项目会在 README 里写明「计划上线时间」，做完才改成 ✅。**不写没跑过的数字。**

## 技术栈

**语言**：Java 21（主力）· Python（可读改）
**AI 应用**：Spring AI · LangChain4j · RAG 全链路 · Function Calling · MCP · Prompt 工程
**数据**：PostgreSQL / pgvector · Redis · MySQL · Kafka · RocketMQ
**工程**：Docker · Nginx · CI/CD · Micrometer / Prometheus / Grafana · JVM 调优

## 之前做过

华为 / 荣耀商城的交易与站点建设 · 招商银行核心系统 · 京东物流履约与站内系统。
擅长高并发、缓存一致性、集群会话、JVM 调优 —— 这些是 AI 应用的底座，不是卖点。

## 联系

- 邮箱：1406345964@qq.com
- 博客：[www.minggui98.vip](https://www.minggui98.vip)

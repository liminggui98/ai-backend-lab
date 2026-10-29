# ai-backend-lab

8 周从 Java 后端转向 AI 应用后端（LLM Application Backend）的实战仓库。
每个目录是一次可运行的交付，不是教程笔记。

## 进度

| 周   | 主题                                  | 交付                          | 状态  |
| --- | ----------------------------------- | --------------------------- | --- |
| W0  | 环境、模型接入、选型                          | pgvector + 三家 provider 流式调用 | 🚧   |
| W1  | Java 21 + Spring Boot 3 + Spring AI | 可切换 provider 的流式服务          | ⬜   |
| W2  | Prompt / Tool Calling / Memory      | 项目 0：AI 助手                  | ⬜   |
| W3  | RAG 上：Tika / 切分 / PgVector          | ingest 管道 + 检索接口            | ⬜   |
| W4  | RAG 下：混合检索 / Rerank / 评测            | 项目一：企业知识库                   | ⬜   |
| W5  | Agent：LangChain4j / 状态机 / MCP       | 项目二：异常处理 Agent              | ⬜   |
| W6  | 网关 + 工程化                            | 项目三：LLM 统一网关                | ⬜   |
| W7  | 面试冲刺                                | 62 题 + 3 套系统设计              | ⬜   |
| W8  | 面试与 Offer                           | —                           | ⬜   |

## 目录

```
day1-starter/      环境验证：pgvector 容器 + 零依赖的 SSE 流式调用
day2-starter/      JD 词频分析：31 份 JD → 23 词频表 + 27 个岗位口径
```

## 技术栈

- **语言**：Java 21
- **框架**：Spring Boot 3、Spring AI、LangChain4j
- **数据**：PostgreSQL + pgvector、Redis
- **解析**：Apache Tika
- **部署**：Docker Compose、Nginx
- **可观测**：Micrometer、Prometheus、Grafana

## 本地运行

```bash
cd day1-starter
cp .env.example .env          # 填入 key
docker compose up -d          # 起 pgvector
java HelloLLM.java            # 跑通流式调用
```

## 说明

- `pgdata/` 是数据库数据目录，已在 `.gitignore` 中排除
- 所有凭证走环境变量，仓库内不含任何 key

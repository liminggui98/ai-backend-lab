# Day 1 Starter（10/29）

三个文件，目标是明天上午 30 分钟内把环境验完，下午跑通第一次流式调用。

## 1. 起数据库（10 分钟）

```bash
docker compose up -d
docker compose logs -f pgvector      # 看到 "database system is ready to accept connections" 即可 Ctrl+C
```

验证 pgvector：

```bash
docker exec -it pgvector psql -U postgres -d ai_lab -c "select note, embedding from healthcheck;"
```

能查到 `pgvector is ready | [0.1,0.2,0.3]` 就成功了。

## 2. 跑第一次调用（10 分钟）

```bash
export DEEPSEEK_API_KEY=sk-xxx
java HelloLLM.java
```

正常输出：

```
首 token 延迟：620 ms

RAG 是先把相关资料检索出来、再交给大模型生成答案的技术方案。

总耗时：1.83 s，输出 42 字
```

## 3. 换 provider 试试（20 分钟）

同一份代码，只改环境变量：

| provider | LLM_BASE_URL | LLM_MODEL | Key |
|---|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` | `sk-`（平台自建） |
| 智谱 | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` | `sk-`（免费额度） |
| 百炼（专属空间） | `https://ws-xxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1` | 以控制台为准 | `sk-ws-`（绑定空间） |

> 百炼的 DashScope 公共域名（`dashscope.aliyuncs.com`）自 2026-09-30 起进入维护期，新业务走专属空间域名。
> 空间级 key 必须配空间级域名，混用会返回 `403 AccessDenied.Unpurchased`。

三家都跑通后，把首 token 延迟填进下面这张表——这是 W1 参数实验的基线。

| provider | 模型 | 首 token | 总耗时 |
|---|---|---|---|
| DeepSeek | deepseek-chat | | |
| 智谱 | glm-4-flash | | |
| 百炼 | | | |

三家跑完，把首 token 延迟和总耗时记下来——这就是 W1 参数实验的第一组数据。

## 常见报错

| 报错 | 原因 | 处理 |
|---|---|---|
| `HTTP 401` | key 错 / 没导出 | `echo $DEEPSEEK_API_KEY` 确认 |
| `HTTP 402` | 余额不足 | 充值 50 元 |
| `java: command not found` | 新终端没加载 | `source ~/.zshrc` |
| `Connection refused` | Docker 没启动 | 打开 Docker Desktop / OrbStack |
| 端口 5432 被占 | 本机已有 Postgres | 改 compose 里 `-p 5433:5432` |

## 今天不做的事

- 不建 Spring Boot 工程（W1 周一再做）
- 不看 RAG / Agent 教程
- 不装 Milvus / Redis（W3 再说）

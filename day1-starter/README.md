# 学习内容（10/29）

> 当天笔记：见 [NOTES.md](NOTES.md)（环境验证 + 三家实测 + 10 个问题）。
> 环境版本与 provider 配置见 [环境清单.md](环境清单.md)。

## 0. 目标

> 安装 Docker / OrbStack <br/>
>
> 起 pgvector 并验证 vector 扩展 <br/>
> 
> 建 ai-backend-lab 仓库 <br/>
> 
> 开 3 家 API（DeepSeek / 通义百炼 / 智谱），写统一调用脚本跑通流式输出 <br/>
> 
> 读 OpenAI 兼容协议 <br/>
> 
> 记 10 个不懂的概念 <br/>

## 1. 起数据库

```bash
docker compose up -d
docker compose logs -f pgvector      # 看到 "database system is ready to accept connections" 即可 Ctrl+C
```

验证 pgvector：

```bash
docker exec -it pgvector psql -U postgres -d ai_lab -c "select note, embedding from healthcheck;"
```

能查到 `pgvector is ready | [0.1,0.2,0.3]` 就成功了。

## 2. 跑第一次调用

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

## 3. 换 provider 试试

同一份代码，只改环境变量：

| provider         | LLM_BASE_URL                                                     | LLM_MODEL       | Key                  |
|------------------|------------------------------------------------------------------|-----------------|----------------------|
| DeepSeek         | `https://api.deepseek.com`                                       | `deepseek-flash` | `sk-`（平台自建）    |
| 智谱             | `https://open.bigmodel.cn/api/paas/v4`                           | `glm-4-flash`   | `sk-`（免费额度）    |
| 百炼（专属空间） | `https://ws-xxx.cn-xxx.maas.aliyuncs.com/compatible-mode/v1` | 以控制台为准    | `sk-ws-`（绑定空间） |

> 百炼的 DashScope 公共域名（`dashscope.aliyuncs.com`）自 2026-09-30 起进入维护期，新业务走专属空间域名。
> 空间级 key 必须配空间级域名，混用会返回 `403 AccessDenied.Unpurchased`。

三家都跑通后，把首 token 延迟填进下面这张表——这是 W1 参数实验的基线。

| provider | 模型          | 首 token    | 总耗时    |
|----------|---------------|-------------|-----------|
| DeepSeek | deepseek-flash | 0.36–0.98 s | 1.0–1.3 s |
| 智谱     | glm-4-flash   | 1.4–1.6 s   | 2.3–3.1 s |
| 百炼     | qwen3.7-plus  | 0.6–10.7 s  | 3.8–11 s  |

## 常见报错

| 报错                      | 原因                                    | 处理                                                                                |
|---------------------------|-----------------------------------------|-------------------------------------------------------------------------------------|
| `HTTP 401`                | key 错 / 没导出                         | `echo $DEEPSEEK_API_KEY` 确认                                                       |
| `HTTP 402`                | 余额不足                                | 充值 50 元                                                                          |
| `java: command not found` | JDK 没进 PATH（本机靠 `~/.zshrc` 注入） | `source ~/.zshrc`；非交互 shell 需在脚本里显式设 PATH                               |
| `Connection refused`      | Docker 没启动                           | 打开 Docker Desktop / OrbStack                                                      |
| 端口 5432 被占            | 本机已有 Postgres                       | 改 compose 里 `-p 5433:5432`                                                        |
| 程序卡住无输出            | 推理模型在思考，或流式连接不再送数据    | 先等 15 s；仍无输出就 Ctrl+C。`HttpRequest.timeout` 只覆盖到响应头，body 流没有超时 |


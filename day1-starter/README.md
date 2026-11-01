# 学习内容

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
set -a
source .env
set +a
java HelloLLM.java
```

正常输出：

见 [HelloLLM 输出内容.txt](HelloLLM%20输出内容.txt)

## 3. 数据记录

| provider         | provider 使用的BASE_URL                                      | provider 使用 MODEL | provider 对应的 API_KEY 形式 |
|------------------|--------------------------------------------------------------|---------------------|------------------------------|
| DeepSeek         | `https://api.deepseek.com`                                   | `deepseek-flash`    | `sk-`（平台自建）            |
| 智谱             | `https://open.bigmodel.cn/api/paas/v4`                       | `glm-4-flash`       | `sk-`（免费额度）            |
| 百炼（专属空间） | `https://ws-xxx.cn-xxx.maas.aliyuncs.com/compatible-mode/v1` | `qwen-plus`         | `sk-ws-`（绑定空间）         |

> 百炼的 DashScope 公共域名（`dashscope.aliyuncs.com`）自 2026-09-30 起进入维护期，新业务走专属空间域名。
> 空间级 key 必须配空间级域名，混用会返回 `403 AccessDenied.Unpurchased`。

设置环境变量（同 [2. 跑第一次调用](#2-跑第一次调用)），多跑几次，把首 token 延迟、总耗时填进下面这张表。

> 下表由 `python3 ttft_stats.py` 统计 [HelloLLM 输出内容.txt](HelloLLM%20输出内容.txt) 中十次流式请求得到（avg / P50 /
> P90）。

| provider | 模型           | 首 token（avg / P50 / P90） | 总耗时（avg / P50 / P90）   |
|----------|----------------|-----------------------------|-----------------------------|
| DeepSeek | deepseek-flash | 165.4 / 122.5 / 286.6 ms    | 1092.7 / 1049.0 / 1332.1 ms |
| 智谱     | glm-4-flash    | 980.2 / 699.0 / 1782.2 ms   | 1883.1 / 1731.0 / 2588.9 ms |
| 百炼     | qwen-plus      | 417.8 / 371.0 / 636.7 ms    | 1624.9 / 1636.5 / 1783.5 ms |

## 常见报错

| 报错                      | 原因                                    | 处理                                                                                |
|---------------------------|-----------------------------------------|-------------------------------------------------------------------------------------|
| `HTTP 401`                | key 错 / 没导出                         | `echo $DEEPSEEK_API_KEY` 确认                                                       |
| `HTTP 402`                | 余额不足                                | 充值 50 元                                                                          |
| `java: command not found` | JDK 没进 PATH（本机靠 `~/.zshrc` 注入） | `source ~/.zshrc`；非交互 shell 需在脚本里显式设 PATH                               |
| `Connection refused`      | Docker 没启动                           | 打开 Docker Desktop / OrbStack                                                      |
| 端口 5432 被占            | 本机已有 Postgres                       | 改 compose 里 `-p 5433:5432`                                                        |
| 程序卡住无输出            | 推理模型在思考，或流式连接不再送数据    | 先等 15 s；仍无输出就 Ctrl+C。`HttpRequest.timeout` 只覆盖到响应头，body 流没有超时 |


#!/usr/bin/env python3
"""Day 3 遗留项：上下文超限的「真实返回体」探测（API对比表.md 第 D 节）。

只做一件事：往三家 provider 各发一次**故意超长**的请求，把 HTTP 状态与错误码记下来。
超限请求在参数校验阶段就被拒，不产生 token 计费；max_tokens=1 是兜底，万一行了就只回 1 个 token。

用法：
    python3 context-limit-probe.py            # 默认 40 万字符（≈26 万 token），稳超 128K
    python3 context-limit-probe.py 600000     # 自定义字符数

凭证从 ../day1-starter/.env 读，不写进仓库。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ENV_PATH = os.path.join(HERE, "..", "day3-starter", ".env")


def load_env(path):
    env = {}
    if not os.path.exists(path):
        return env
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


PROVIDERS = [
    # 名字, key 环境变量（按序取第一个有值的）, base_url, model
    ("DeepSeek", ["DEEPSEEK_API_KEY"], "https://api.deepseek.com", "deepseek-chat"),
    ("智谱", ["ZHIPU_API_KEY"], "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    ("百炼", ["BAILIAN_API_KEY", "DASHSCOPE_API_KEY"],
     "https://ws-pjpffg7677688nuc.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus"),
]


def probe(name, keys, base_url, model, chars):
    key = next((os.environ.get(k) or ENV.get(k) for k in keys if os.environ.get(k) or ENV.get(k)), None)
    if not key:
        return {"provider": name, "skipped": f"未找到 {keys}"}

    # 一段可被正常理解的填充文本，避免被当成乱码/攻击请求
    filler = "上下文长度探测。请忽略下面的重复内容，只回复 ok。\n"
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是测试助手，只回复 ok。"},
            {"role": "user", "content": filler + "测试填充。" * (chars // 5)},
        ],
        "max_tokens": 1,
        "stream": False,
    }
    raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=raw,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
        method="POST",
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            payload = resp.read().decode("utf-8", "replace")
            return {"provider": name, "status": resp.status, "body": payload[:600],
                    "elapsed": round(time.time() - t0, 1), "note": "竟然没被拒（注意成本）"}
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8", "replace")
        return {"provider": name, "status": e.code, "body": payload[:600],
                "elapsed": round(time.time() - t0, 1)}
    except Exception as e:  # 网络/超时
        return {"provider": name, "error": repr(e), "elapsed": round(time.time() - t0, 1)}


if __name__ == "__main__":
    chars = int(sys.argv[1]) if len(sys.argv) > 1 else 400_000
    only = sys.argv[2] if len(sys.argv) > 2 else None  # 只跑某一家：DeepSeek / 智谱 / 百炼
    ENV = load_env(ENV_PATH)
    print(f"payload ≈ {chars} 字符，约 {chars // 1.5:.0f} token（中文按 1.5 字符/token 估）\n")
    for name, keys, base, model in PROVIDERS:
        if only and name != only:
            continue
        r = probe(name, keys, base, model, chars)
        print(f"--- {name} ({model}) ---")
        print(json.dumps(r, ensure_ascii=False, indent=2))
        print()

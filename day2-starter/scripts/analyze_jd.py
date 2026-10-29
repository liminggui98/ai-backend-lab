#!/usr/bin/env python3
"""Day 2 · JD 词频统计（31 份文件 / 27 个独立岗位）

统计口径：
- 语料：JD库/*.md，排除 README.md（索引）
- 排除：每份末尾「## 关键词提炼」段（二次摘要，计入会自我放大）、引用块备注、元信息「方向 / 招聘人」行
- 计入：元信息表（含「标签」行）+ 职位描述正文（职责 / 要求 / 优先 / 亮点）
- 出现次数：关键词在文本中的非重叠匹配次数（按文件累计）
- 出现份数：至少命中一次的 JD 文件数（/31）
- 去重岗位数：同源多版本合并后计数（阿里商品理解 ×3、美团 Agent ×2、快手可灵 ×2 → /27）
- 要求级别：按去重岗位判断 —— 过半岗位在「任职要求」段提到 → 硬性；
            在「优先/加分」段提到 → 优先；只在职责或标签出现 → 提及

输出：
- out/keyword_stats.csv / .json
- out/keyword_tables.md（可直接贴进报告）
"""

import csv
import json
import re
from collections import OrderedDict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
JD_DIR = BASE / "JD库"
OUT_DIR = BASE / "out"

KEYWORDS = [
    ("RAG / 检索增强", [r"RAG", r"检索增强", r"检索生成", r"知识增强", r"RAGAS"]),
    ("Agent / 智能体", [r"Agent", r"Agents", r"Agentic", r"智能体", r"Multi-?Agent"]),
    ("Function Calling / Tool Use", [r"Function[ -]?Calling", r"Tool Use", r"Tool Calling", r"工具调用", r"函数调用"]),
    ("MCP", [r"MCP"]),
    ("Spring AI", [r"Spring\s*AI"]),
    ("LangChain4j", [r"LangChain4j", r"LangChain", r"LangGraph"]),
    ("向量库（pgvector / Milvus）", [r"向量库", r"向量数据库", r"pgvector", r"Milvus", r"FAISS", r"Qdrant", r"Weaviate", r"Pinecone", r"向量索引", r"向量检索", r"VectorStore", r"向量存储"]),
    ("Embedding / 向量化", [r"Embedding", r"向量化", r"向量表征", r"文本向量", r"bge-", r"嵌入模型"]),
    ("混合检索 / RRF", [r"混合检索", r"RRF", r"BM25", r"稀疏检索", r"关键词检索", r"倒数排名", r"Hybrid Search", r"关键词\s*\+\s*向量", r"关键词\+向量"]),
    ("Rerank / 重排", [r"Rerank", r"重排", r"重排序", r"Cross-?Encoder"]),
    ("评测（RAGAS / 召回率）", [r"评测", r"评估", r"RAGAS", r"召回率", r"准确率", r"Bad Case", r"Eval", r"评估框架", r"效果验证", r"基准测试", r"Benchmark", r"A/B"]),
    ("Prompt 工程", [r"Prompt", r"提示词", r"提示工程", r"few-?shot", r"CoT", r"思维链", r"System Prompt"]),
    ("结构化输出", [r"结构化输出", r"Structured Output", r"JSON\s*Schema", r"JSON\s*Mode", r"JSON\s*格式", r"输出格式", r"格式约束", r"结构化抽取", r"结构化提取", r"信息抽取"]),
    ("SSE / 流式输出", [r"SSE", r"Server-Sent", r"流式", r"Streaming", r"打字机"]),
    ("多模型路由 / 网关", [r"模型路由", r"LLM\s*网关", r"AI\s*网关", r"网关", r"多模型", r"Provider", r"模型接入", r"路由调度", r"模型切换", r"容灾切换", r"MaaS", r"统一入口"]),
    ("限流 / 熔断 / 降级", [r"限流", r"熔断", r"降级", r"流控", r"Rate\s*Limit", r"Sentinel", r"Hystrix", r"Resilience4j", r"过载保护", r"背压", r"流量控制"]),
    ("Token 成本 / 计费", [r"Token\s*(成本|预算|计费|消耗|用量|优化|治理)", r"每百万\s*Token", r"成本", r"计费", r"预算", r"定价", r"账单", r"Credits?"]),
    ("可观测（Micrometer / Prometheus）", [r"可观测", r"监控", r"Prometheus", r"Grafana", r"Micrometer", r"Trace", r"链路追踪", r"日志", r"告警", r"OpenTelemetry", r"SkyWalking", r"SLA"]),
    ("安全（Prompt 注入 / 越权）", [r"安全", r"Prompt\s*注入", r"注入", r"越权", r"权限", r"合规", r"风控", r"脱敏", r"审计", r"攻击", r"护栏", r"Guardrail"]),
    ("多租户", [r"多租户", r"租户", r"Tenant"]),
    ("Java / Spring Boot 3 / 并发", [r"Java", r"Spring", r"JVM", r"并发", r"多线程", r"线程池", r"NIO", r"Netty"]),
    ("Docker / K8s", [r"Docker", r"Kubernetes", r"K8s", r"容器", r"Helm", r"云原生", r"CI/CD", r"Jenkins", r"镜像"]),
    ("微调 / LoRA（了解即可）", [r"微调", r"fine-?tun", r"LoRA", r"SFT", r"RLHF", r"DPO", r"CPT", r"后训练"]),
]

EXTRA_KEYWORDS = [
    ("MySQL / Redis / Kafka / MQ", [r"MySQL", r"Redis", r"Kafka", r"RocketMQ", r"消息队列", r"MQ"]),
    ("高并发 / 分布式 / 微服务", [r"高并发", r"分布式", r"微服务", r"高可用"]),
    ("AI Coding（Claude Code / Cursor / Copilot / SDD）", [r"AI\s*Coding", r"Claude Code", r"Cursor", r"Copilot", r"vibe", r"SDD", r"AICoding", r"AI\s*代码", r"AI\s*辅助开发"]),
    ("上下文工程 / 记忆", [r"上下文", r"Context", r"记忆", r"Memory"]),
    ("搜索 / 推荐 / 广告", [r"搜索", r"推荐", r"广告"]),
    ("LLM 原理（Transformer / Token / 采样）", [r"Transformer", r"Token\s*机制", r"采样参数", r"上下文窗口", r"大模型.{0,6}原理", r"LLM.{0,6}原理"]),
    ("ReAct / 规划 / 任务分解", [r"ReAct", r"任务规划", r"任务分解", r"推理规划", r"Planning"]),
    ("知识库 / 企业知识库", [r"知识库", r"Knowledge Base"]),
    ("NLP / 信息抽取 / 知识图谱", [r"NLP", r"信息抽取", r"知识图谱", r"语义"]),
    ("框架生态（LangChain / LangGraph）", [r"LangChain", r"LangGraph"]),
    ("多模态 / VLM", [r"多模态", r"VLM", r"Vision"]),
    ("Dify / Coze / AutoGen / CrewAI / LlamaIndex", [r"Dify", r"Coze", r"AutoGen", r"CrewAI", r"LlamaIndex"]),
    ("团队管理 / 技术负责人", [r"团队管理", r"技术负责人", r"Tech Lead", r"带团队", r"管理经验"]),
    ("GPU / 推理服务 / 模型部署", [r"GPU", r"vLLM", r"推理服务", r"模型部署", r"Ollama"]),
]

# 同源多版本 → 同一岗位（README「同源版本对照」）
SAME_SOURCE = {
    "阿里巴巴-ai应用研发工程师-40-60K·16薪.md": "阿里-商品理解",
    "阿里巴巴-AI应用研发高级工程师-40-60K·16薪.md": "阿里-商品理解",
    "阿里巴巴-AI应用研发（商品理解方向）-40-60K·16薪.md": "阿里-商品理解",
    "美团-AI Agent工程师（业务应用开发方向）-40-70K·15薪.md": "美团-AI Agent 工程师",
    "美团-AI Agent工程师（工程架构-业务应用）-30-60K·15薪.md": "美团-AI Agent 工程师",
    "快手-后端开发工程师（可灵AI）-30-60K·16薪.md": "快手-可灵后端",
    "快手-后端开发工程师（可灵AI）-40-70K·16薪.md": "快手-可灵后端",
}

TRACK_B = {
    "小红书-后端研发工程师（广告风控）-35-65K·16薪.md",
    "小红书-直播Java研发工程师-35-60K·16薪.md",
    "美团-营销后端Java工程师-50-65K·15薪.md",
    "小米-后端高级开发工程师（稳定性保障SRE）-25-50K·16薪.md",
    "快手-后端开发工程师（可灵AI）-30-60K·16薪.md",
    "快手-后端开发工程师（可灵AI）-40-70K·16薪.md",
    "柯莱特集团-Java后端架构师（AI平台方向）-30-60K·14薪.md",
    "字节跳动-后端（地图应用、导航方向）-24-45K.md",
}

REQ_RE = re.compile(r"要求|任职|资格|基本要求|职责要求")
PLUS_RE = re.compile(r"优先|加分")
CUT_RE = re.compile(r"^##\s*关键词提炼")
PLUS_LINE_RE = re.compile(r"^[-*\d.、)\s]*(AI\s*)?(加分项?|优先项?|优先考虑|具备以下优先)")


def ascii_boundary(fragment: str) -> str:
    # Python 的 \w 含中文，RAG系统 不命中 \bRAG\b，故用 ASCII 环视
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 \-/]*", fragment):
        return rf"(?<![A-Za-z0-9]){fragment}(?![A-Za-z0-9])"
    return fragment


def compile_pattern(fragments):
    return re.compile("|".join(ascii_boundary(f) for f in fragments), re.IGNORECASE)


def split_sections(text):
    """逐行给出 (段落类别, 内容)。类别：req / plus / duty / meta / other。"""
    section = "duty"
    in_cut = False
    for line in text.splitlines():
        stripped = line.strip()
        if CUT_RE.match(stripped):
            in_cut = True
            continue
        if stripped.startswith("#"):
            in_cut = False
            if stripped.startswith("###"):
                if REQ_RE.search(stripped):
                    section = "req"
                elif PLUS_RE.search(stripped):
                    section = "plus"
                elif re.search(r"职责|描述|内容|方向|团队|产品", stripped):
                    section = "duty"
                else:
                    section = "other"
            else:
                section = "duty"
            continue
        if in_cut or not stripped:
            continue
        # 编辑性内容：引用块备注、元信息「方向 / 招聘人」行
        if stripped.startswith(">"):
            continue
        if re.match(r"^\|\s*(方向|招聘人)\s*\|", stripped):
            continue
        if stripped.startswith("|"):
            yield "meta", stripped
        elif PLUS_LINE_RE.match(stripped):
            yield "plus", stripped
        else:
            yield section, stripped


def load_docs():
    docs = []
    for path in sorted(JD_DIR.glob("*.md")):
        if path.name == "README.md":
            continue
        docs.append({"file": path.name, "position": SAME_SOURCE.get(path.name, path.name),
                     "text": path.read_text(encoding="utf-8")})
    return docs


def count_keyword(docs, pattern):
    per_file = OrderedDict()
    req_pos, plus_pos = set(), set()
    total = 0
    for doc in docs:
        n = 0
        has_req = has_plus = False
        for section, line in split_sections(doc["text"]):
            m = len(pattern.findall(line))
            if m:
                n += m
                if section == "req":
                    has_req = True
                elif section == "plus":
                    has_plus = True
        if n:
            per_file[doc["file"]] = n
            total += n
            if has_req:
                req_pos.add(doc["position"])
            elif has_plus:
                plus_pos.add(doc["position"])
    return total, per_file, req_pos, plus_pos


def level_of(present_pos, req_pos, plus_pos):
    if present_pos == 0:
        return "—"
    if len(req_pos) >= 2 and len(req_pos) / present_pos >= 0.5:
        return "硬性"
    if len(req_pos | plus_pos) >= 2 and len(req_pos | plus_pos) / present_pos >= 0.5:
        return "优先"
    return "提及"


def stats_for(docs, name, fragments):
    pattern = compile_pattern(fragments)
    total, per_file, req_pos, plus_pos = count_keyword(docs, pattern)
    positions = {d["position"] for d in docs if d["file"] in per_file}
    ai_positions = {d["position"] for d in docs if d["file"] in per_file and d["file"] not in TRACK_B}
    return {
        "keyword": name,
        "docs": len(per_file),
        "hits": total,
        "positions": len(positions),
        "ai_positions": len(ai_positions),
        "req_positions": len(req_pos),
        "plus_positions": len(plus_pos),
        "level": level_of(len(positions), req_pos, plus_pos),
        "companies": list(OrderedDict.fromkeys(f.split("-")[0] for f in per_file)),
        "files": list(per_file),
        "file_hits": per_file,
    }


def main():
    docs = load_docs()
    n_docs = len(docs)
    n_positions = len({d["position"] for d in docs})
    n_ai_positions = len({d["position"] for d in docs if d["file"] not in TRACK_B})

    rows = [stats_for(docs, name, frags) for name, frags in KEYWORDS]
    extras = [stats_for(docs, name, frags) for name, frags in EXTRA_KEYWORDS]
    for r in rows + extras:
        r["avg_per_doc"] = round(r["hits"] / r["docs"], 2) if r["docs"] else 0

    OUT_DIR.mkdir(exist_ok=True)
    payload = {
        "corpus": {"docs": n_docs, "positions": n_positions, "ai_positions": n_ai_positions,
                   "track_b": sorted(TRACK_B), "same_source": SAME_SOURCE,
                   "excluded": ["README.md", "## 关键词提炼 段", "引用块备注", "元信息方向行"]},
        "keywords": rows,
        "extras": extras,
    }
    (OUT_DIR / "keyword_stats.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    with (OUT_DIR / "keyword_stats.csv").open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["关键词", f"出现份数/{n_docs}", f"去重岗位/{n_positions}", f"AI岗位/{n_ai_positions}",
                    "出现次数", "要求级别", "命中公司"])
        for r in rows:
            w.writerow([r["keyword"], r["docs"], r["positions"], r["ai_positions"], r["hits"],
                        r["level"], "、".join(r["companies"])])

    write_tables(rows, extras, n_docs, n_positions, n_ai_positions)
    print_summary(rows, extras, n_docs, n_positions, n_ai_positions)


def write_tables(rows, extras, n_docs, n_pos, n_ai):
    lines = [f"### 主关键词（按出现份数降序，{n_docs} 份 / {n_pos} 个独立岗位）\n",
             "| # | 关键词 | 出现份数 | 去重岗位 | AI 岗 | 出现次数 | 级别 | 命中公司 |",
             "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for i, r in enumerate(sorted(rows, key=lambda x: (-x["docs"], -x["hits"])), 1):
        lines.append(f"| {i} | {r['keyword']} | {r['docs']}/{n_docs} | {r['positions']}/{n_pos} | "
                     f"{r['ai_positions']}/{n_ai} | {r['hits']} | {r['level']} | {'、'.join(r['companies'])} |")
    lines += ["\n> 级别按「去重岗位」判断：硬性 = 过半岗位在「任职要求」段提到；优先 = 出现在「优先/加分」段；提及 = 只在职责或标签出现。\n",
              f"\n### 扩展词（23 词之外，{n_docs} 份）\n",
              "| # | 关键词 | 出现份数 | 去重岗位 | 出现次数 | 级别 | 命中公司 |",
              "| --- | --- | --- | --- | --- | --- | --- |"]
    for i, e in enumerate(sorted(extras, key=lambda x: (-x["docs"], -x["hits"])), 1):
        lines.append(f"| {i} | {e['keyword']} | {e['docs']}/{n_docs} | {e['positions']}/{n_pos} | "
                     f"{e['hits']} | {e['level']} | {'、'.join(e['companies'])} |")
    (OUT_DIR / "keyword_tables.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def print_summary(rows, extras, n_docs, n_pos, n_ai):
    print(f"语料：{n_docs} 份 / {n_pos} 个独立岗位（AI 岗 {n_ai} 个）\n")
    print(f"{'关键词':<32}{'份':>4}{'岗':>4}{'AI':>4}{'次':>5}  {'级别':<4} 代表公司")
    for r in sorted(rows, key=lambda x: (-x["docs"], -x["hits"])):
        print(f"{r['keyword']:<32}{r['docs']:>4}{r['positions']:>4}{r['ai_positions']:>4}{r['hits']:>5}  "
              f"{r['level']:<4} {'、'.join(r['companies'])[:36]}")
    print("\n扩展词：")
    for e in sorted(extras, key=lambda x: -x["docs"]):
        print(f"{e['keyword']:<44}{e['docs']:>3} 份 {e['positions']:>3} 岗 {e['hits']:>4} 次  {e['level']}")
    print("\n文件：out/keyword_stats.csv, out/keyword_stats.json, out/keyword_tables.md")


if __name__ == "__main__":
    main()

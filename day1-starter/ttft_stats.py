#!/usr/bin/env python3
"""统计 `HelloLLM 输出内容.txt` 中各模式流式请求的首 token 延迟指标。

用法：python3 ttft_stats.py [数据文件]
  数据文件默认取脚本同目录下的 `HelloLLM 输出内容.txt`

输出：按 provider/model 分组，只统计"是否流式=是"的行，
      分别计算首 token 与总耗时的平均值、P50、P90（统一毫秒）。

作者：LiMingGui98
创建日期：2026-11-01
"""
import statistics
import sys
from pathlib import Path

# 数据行按空白切分后的列索引
COL_PROVIDER, COL_MODEL, COL_STREAM, COL_FIRST_TOKEN, COL_TOTAL = 0, 1, 2, 3, 4
HEADER_LINES = 2  # 表头 + 分隔线
METRICS = [("first", "首 token"), ("total", "总耗时")]


def parse_ms(text):
    """'273ms' -> 273；非数字（如 '-'）返回 None。"""
    if text.endswith("ms") and text[:-2].isdigit():
        return int(text[:-2])
    return None


def percentile(sorted_values, p):
    """线性插值百分位（与 numpy 默认一致），输入需已排序。"""
    n = len(sorted_values)
    if n == 0:
        return None
    if n == 1:
        return sorted_values[0]
    rank = (n - 1) * p / 100.0
    lo = int(rank)
    hi = lo + 1
    if hi >= n:
        return sorted_values[-1]
    frac = rank - lo
    return sorted_values[lo] * (1 - frac) + sorted_values[hi] * frac


def collect(path):
    """解析文件，返回 {模式: {first: [ms], total: [ms]}}，只保留流式且数值有效的行。"""
    groups = {}
    for line in path.read_text(encoding="utf-8").splitlines()[HEADER_LINES:]:
        cols = line.split()
        if len(cols) < 6:
            continue
        if cols[COL_STREAM] != "是":
            continue
        first = parse_ms(cols[COL_FIRST_TOKEN])
        total = parse_ms(cols[COL_TOTAL])
        if first is None or total is None:
            continue
        data = groups.setdefault(f"{cols[COL_PROVIDER]}/{cols[COL_MODEL]}", {})
        data.setdefault("first", []).append(first)
        data.setdefault("total", []).append(total)
    return groups


def render(groups):
    out = []
    for key, label in METRICS:
        out.append(f"## {label} (ms)")
        out.append("| 模式 | 样本数 | 平均 | P50 | P90 |")
        out.append("| --- | --- | --- | --- | --- |")
        for mode, data in sorted(groups.items()):
            values = sorted(data[key])
            out.append(
                f"| {mode} | {len(values)} | {statistics.mean(values):.1f} "
                f"| {percentile(values, 50):.1f} | {percentile(values, 90):.1f} |"
            )
        out.append("")
    return "\n".join(out)


def main():
    default = Path(__file__).resolve().parent / "HelloLLM 输出内容.txt"
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else default
    if not path.exists():
        print(f"文件不存在：{path}", file=sys.stderr)
        return 1
    groups = collect(path)
    if not groups:
        print("没有找到流式请求数据", file=sys.stderr)
        return 1
    print(render(groups))
    return 0


if __name__ == "__main__":
    sys.exit(main())

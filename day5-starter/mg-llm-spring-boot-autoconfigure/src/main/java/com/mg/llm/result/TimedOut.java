package com.mg.llm.result;

public record TimedOut(String prompt, long cost) implements LLMResult {
}

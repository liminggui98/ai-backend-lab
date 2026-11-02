package com.mg.llm.result;

public record RateLimited(String code, String type, String message) implements LLMResult {
}

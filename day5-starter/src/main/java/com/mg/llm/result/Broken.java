package com.mg.llm.result;

public record Broken(String code, String type, String message) implements LLMResult {
}

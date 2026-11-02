package com.mg.llm.result;

public record ClientException(String message, Exception e) implements LLMResult {
}

package com.mg.llm.prompt;

public record SystemMessage(String content) implements Message {
    @Override
    public String role() {
        return "system";
    }
}

package com.mg.llm.message;

public record SystemMessage(String content) implements Message {
    @Override
    public String role() {
        return "system";
    }
}

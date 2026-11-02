package com.mg.llm.message;

public record UserMessage(String content) implements Message {
    @Override
    public String role() {
        return "user";
    }
}

package com.mg.llm.prompt;

public record UserMessage(String content) implements Message {
    @Override
    public String role() {
        return "user";
    }
}

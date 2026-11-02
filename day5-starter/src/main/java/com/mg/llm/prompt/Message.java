package com.mg.llm.prompt;

public sealed interface Message permits SystemMessage, UserMessage {
    String role();

    String content();

    public static UserMessage userPrompt(String userContent) {
        return new UserMessage(userContent);
    }

    public static SystemMessage systemPrompt(String content) {
        return new SystemMessage(content);
    }
}

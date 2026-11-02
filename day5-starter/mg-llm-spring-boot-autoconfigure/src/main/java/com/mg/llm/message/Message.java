package com.mg.llm.message;

public sealed interface Message permits SystemMessage, UserMessage {
    String role();

    String content();

    static UserMessage user(String userContent) {
        return new UserMessage(userContent);
    }

    static SystemMessage system(String content) {
        return new SystemMessage(content);
    }
}

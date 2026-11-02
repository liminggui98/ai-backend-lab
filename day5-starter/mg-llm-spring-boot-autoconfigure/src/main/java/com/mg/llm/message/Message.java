package com.mg.llm.message;

/**
 * LLM 消息的统一抽象，当前仅支持 system / user 两种角色。
 *
 * @author LiMingGui98
 * @since 2026-11-02
 */
public sealed interface Message permits SystemMessage, UserMessage {
    String role();

    String content();

    /**
     * 创建用户消息。
     *
     * @param userContent 用户输入内容
     * @return 用户消息
     */
    static UserMessage user(String userContent) {
        return new UserMessage(userContent);
    }

    /**
     * 创建系统消息。
     *
     * @param content 系统提示内容
     * @return 系统消息
     */
    static SystemMessage system(String content) {
        return new SystemMessage(content);
    }
}

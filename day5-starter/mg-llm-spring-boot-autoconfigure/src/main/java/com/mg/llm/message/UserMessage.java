package com.mg.llm.message;

/**
 * 用户消息，{@link #role()} 固定返回 user。
 *
 * @param content 消息内容
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record UserMessage(String content) implements Message {
    @Override
    public String role() {
        return "user";
    }
}

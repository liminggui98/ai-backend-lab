package com.mg.llm.message;

/**
 * 系统消息，{@link #role()} 固定返回 system。
 *
 * @param content 消息内容
 * @author LiMingGui98
 * @since 2026-11-02
 */
public record SystemMessage(String content) implements Message {
    @Override
    public String role() {
        return "system";
    }
}

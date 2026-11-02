package com.mg.llm.client;


import com.mg.llm.prompt.Message;

public class ChatCompletionRequest {
    private Message systemMessage;

    private Message userMessage;

    private Boolean stream = false;

    public ChatCompletionRequest(Message systemMessage, Message userMessage, Boolean stream) {
        this.systemMessage = systemMessage;
        this.userMessage = userMessage;

        if (stream != null) {
            this.stream = stream;
        }
    }

    public Message getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(Message systemMessage) {
        this.systemMessage = systemMessage;
    }

    public Message getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(Message userMessage) {
        this.userMessage = userMessage;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}

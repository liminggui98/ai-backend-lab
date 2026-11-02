package com.mg.llm.client;


import com.mg.llm.prompt.Message;
import lombok.Data;

@Data
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

    public String getCompletionChatReqText(String model) {
        return """
                {
                    "model": "%s",
                    "messages": %s,
                    "stream": %b,
                    "top_p": 0.9,
                    "temperature": 1
                }
                """.formatted(model, getMessageStr(), stream);
    }

    private String getMessageStr() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        if (systemMessage != null) {
            sb.append("""
                    {
                        "role": "%s",
                        "content": "%s"
                    }
                    """.formatted(systemMessage.role(), systemMessage.content()));
        }
        if (userMessage != null) {
            sb.append("""
                    ,
                    {
                        "role": "%s",
                        "content": "%s"
                    }
                    """.formatted(userMessage.role(), userMessage.content()));
        }
        sb.append("]");
        return sb.toString();
    }
}

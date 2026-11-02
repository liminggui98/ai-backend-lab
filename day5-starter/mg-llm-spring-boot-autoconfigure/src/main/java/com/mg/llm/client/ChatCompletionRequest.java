package com.mg.llm.client;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mg.llm.message.Message;
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

    /**
     * 用 fastjson2 序列化请求体：字段名（messages 复数）、布尔（stream）、字符串转义都交给序列化器处理，
     * 避免手拼 JSON 时 content 里出现引号 / 换行导致请求体非法。
     *
     * @param model 模型名
     * @return OpenAI 兼容协议的 chat/completions 请求体
     */
    public String getCompletionChatReqText(String model, Float temperature) {
        JSONArray messages = new JSONArray();
        if (systemMessage != null) {
            messages.add(new JSONObject().fluentPut("role", systemMessage.role()).fluentPut("content", systemMessage.content()));
        }
        if (userMessage != null) {
            messages.add(new JSONObject().fluentPut("role", userMessage.role()).fluentPut("content", userMessage.content()));
        }

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", stream);

        if (temperature != null) {
            payload.put("temperature", temperature);
        }

        return payload.toJSONString();
    }
}

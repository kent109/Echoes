package com.gemform.be.echoes.tool;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装一个工具，执行完把它的结果以 assistant 消息写进会话记忆。
 * <p>
 * 挂在工具层而不是记忆 advisor 层：MessageChatMemoryAdvisor 的 order 是
 * Integer.MIN_VALUE+200、ToolCallingAdvisor 是 +300，工具那一轮整段发生在记忆
 * advisor 的视野之外，advisor 层永远看不到 ToolResponseMessage。
 */
public class MemoryRecordingToolCallback implements ToolCallback {

    // 工具结果可能是整页 JSON，而记忆窗口会把每一行原样回放给模型
    private static final int MAX_RECORDED_RESULT_LENGTH = 500;

    private final ToolCallback delegate;

    private final ChatMemory chatMemory;

    public MemoryRecordingToolCallback(ToolCallback delegate, ChatMemory chatMemory) {
        this.delegate = delegate;
        this.chatMemory = chatMemory;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Object conversationId = toolContext == null
                ? null
                : toolContext.getContext().get(ChatMemory.CONVERSATION_ID);
        String result = delegate.call(toolInput, toolContext);
        // 没有会话 id 就只执行不记账：ToolContext 只装 .toolContext(...) 传进来的键，
        // advisor 参数不会自动合并
        if (result != null && conversationId instanceof String id && !id.isBlank()) {
            // 将工具返回结果转化为Assistant消息，添加到记忆落盘
            chatMemory.add(id, new AssistantMessage(summarize(toolInput, result)));
        }
        return result;
    }

    private String summarize(String toolInput, String result) {
        String recorded = result.length() > MAX_RECORDED_RESULT_LENGTH
                ? result.substring(0, MAX_RECORDED_RESULT_LENGTH) + "…"
                : result;
        return "[tool] " + delegate.getToolDefinition().name() + "(" + toolInput + ") -> " + recorded;
    }
}

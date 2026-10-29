package com.gemform.be.echoes.tool;

import java.util.Arrays;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

public final class MemoryRecordingTools {

    private MemoryRecordingTools() {
    }

    public static ToolCallback[] wrap(ChatMemory chatMemory, Object... toolObjects) {
        return Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(toolObjects).build().getToolCallbacks())
                .map(callback -> (ToolCallback) new MemoryRecordingToolCallback(callback, chatMemory))
                .toArray(ToolCallback[]::new);
    }
}

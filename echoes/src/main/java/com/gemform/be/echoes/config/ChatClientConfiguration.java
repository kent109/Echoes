package com.gemform.be.echoes.config;

import com.gemform.be.echoes.tool.EchoTool;
import com.gemform.be.echoes.tool.MemoryRecordingTools;
import com.gemform.be.echoes.rag.OkhttpChangelogQueryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfiguration {

    // 工具清单只在这里组装：加新工具就在本方法入参和 wrap(...) 里各加一项
    @Bean("chatToolCallbacks")
    ToolCallback[] chatToolCallbacks(ChatMemory chatMemory, EchoTool echoTool,
            OkhttpChangelogQueryService okhttpChangelogQueryService) {
        return MemoryRecordingTools.wrap(chatMemory, echoTool, okhttpChangelogQueryService);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}

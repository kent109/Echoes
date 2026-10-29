package com.gemform.be.echoes.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    // SPRING_AI_CHAT_MEMORY.conversation_id is VARCHAR(36); longer ids fail at insert time
    private static final int MAX_CONVERSATION_ID_LENGTH = 36;

    private final ChatClient chatClient;

    private final ToolCallback[] toolCallbacks;

    public ChatController(ChatClient chatClient, @Qualifier("chatToolCallbacks") ToolCallback[] toolCallbacks) {
        this.chatClient = chatClient;
        this.toolCallbacks = toolCallbacks;
    }

    public record ChatRequest(String conversationId, String content, Boolean useTools) {

        // 调用方不写 useTools 就是 true：工具照常带上，只有显式传 false 才摘掉
        public boolean attachTools() {
            return useTools == null || useTools;
        }
    }

    @PostMapping
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        requireValid(request);
        String reply = prompt(request).call().content();
        return Map.of("conversationId", request.conversationId(), "reply", reply);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        // Validation happens before the stream starts, so failures can still be plain 400s
        requireValid(request);
        return prompt(request).stream().content();
    }

    private ChatClient.ChatClientRequestSpec prompt(ChatRequest request) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .user(request.content())
                .toolContext(Map.of(ChatMemory.CONVERSATION_ID, request.conversationId()))
                // 记忆 advisor 的参数不会自动进 ToolContext，会话 id 要显式带过去
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()));
        if (request.attachTools()) {
            spec.tools((Object[]) toolCallbacks);
        }
        return spec;
    }

    private static void requireValid(ChatRequest request) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must not be blank");
        }
        if (request.conversationId().length() > MAX_CONVERSATION_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId must not exceed " + MAX_CONVERSATION_ID_LENGTH + " characters");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content must not be blank");
        }
    }
}

package com.gemform.be.echoes.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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

    public ChatController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public record ChatRequest(String conversationId, String content) {
    }

    @PostMapping
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        requireValid(request);
        String reply = chatClient.prompt()
                .user(request.content())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .call()
                .content();
        return Map.of("conversationId", request.conversationId(), "reply", reply);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        // Validation happens before the stream starts, so failures can still be plain 400s
        requireValid(request);
        return chatClient.prompt()
                .user(request.content())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .stream()
                .content();
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

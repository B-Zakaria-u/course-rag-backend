package org.mql.coursebackend.controller;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.mql.coursebackend.dto.ChatResponse;
import org.mql.coursebackend.entity.ChatMessage;
import org.mql.coursebackend.entity.ChatSession;
import org.mql.coursebackend.service.ChatService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@RequestParam(value = "chatId", required = false) String chatId,
            @RequestBody String message,
            @RequestParam(value = "mode", defaultValue = "LOCAL") String mode) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = "default-user";
        }
        return chatService.chat(chatId, message, mode);
    }

    @PostMapping("/new")
    public ChatSession createChat() {
        return chatService.createChat();
    }

    @GetMapping
    public List<ChatSession> getAllChats() {
        return chatService.getAllChats();
    }

    @GetMapping("/{chatId}/history")
    public List<ChatMessage> getChatHistory(@PathVariable String chatId) {
        return chatService.getChatHistory(chatId);
    }

    @DeleteMapping("/{chatId}")
    public void deleteChat(@PathVariable String chatId) {
        chatService.deleteChat(chatId);
    }

    @PutMapping("/{chatId}/title")
    public ChatSession updateChatTitle(@PathVariable String chatId,
            @RequestBody String newTitle) {
        return chatService.updateChatTitle(chatId, newTitle);
    }
}

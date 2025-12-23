package org.mql.coursebackend.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.annotation.PostConstruct;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;

import org.mql.coursebackend.dto.ChatResponse;
import org.mql.coursebackend.entity.ChatMessage;
import org.mql.coursebackend.entity.ChatSession;
import org.mql.coursebackend.rag.WebSearchContentRetriever;
import org.mql.coursebackend.repository.ChatMessageRepository;
import org.mql.coursebackend.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@Slf4j
@Transactional
public class ChatService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    private final WebSearchContentRetriever webRetriever;

    private final Map<String, CourseAgent> agentCache = new ConcurrentHashMap<>();

    @Value("${course.rag.system-prompt}")
    private String systemPrompt;

    public ChatService(ChatLanguageModel chatLanguageModel,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            WebSearchContentRetriever webRetriever) {

        this.chatLanguageModel = chatLanguageModel;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.webRetriever = webRetriever;

        log.info("ChatService initialized with RAG capabilities and Web Search");
    }

    @PostConstruct
    public void init() {
        log.info("Loaded System Prompt: [{}]", systemPrompt);
    }

    private CourseAgent getOrCreateAgent(String chatId, String mode) {
        String cacheKey = chatId + "_" + mode;
        if (agentCache.containsKey(cacheKey)) {
            // log.info("Agent Cache HIT for key: {}", cacheKey);
            return agentCache.get(cacheKey);
        }
        log.info("Agent Cache MISS for key: {} - Creating new Agent", cacheKey);

        ContentRetriever retriever = createRetriever(chatId, mode);

        // Build the agent with the specific retriever and system prompt
        CourseAgent agent = AiServices.builder(CourseAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(retriever)
                .systemMessageProvider(memoryId -> systemPrompt)
                .build();

        agentCache.put(cacheKey, agent);
        return agent;
    }

    private ContentRetriever createRetriever(String chatId, String mode) {
        if ("WEB".equalsIgnoreCase(mode)) {
            return webRetriever;
        } else {
            Filter chatFilter = metadataKey("chatId").isEqualTo(chatId);
            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(5) // Increased from 3 to 5 for better context
                    .minScore(0.5)
                    // .filter(chatFilter) // If documents are global, remove this.
                    .filter(chatFilter)
                    .build();
        }
    }

    // CRUD Methods
    public ChatSession createChat() {
        String id = java.util.UUID.randomUUID().toString();
        ChatSession session = ChatSession.builder()
                .id(id)
                .title("New Chat")
                .build();
        return sessionRepository.save(session);
    }

    public List<ChatSession> getAllChats() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ChatMessage> getChatHistory(String chatId) {
        return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
    }

    public void deleteChat(String chatId) {
        messageRepository.deleteByChatId(chatId);
        sessionRepository.deleteById(chatId);
        // Remove all agents for this chat from cache
        agentCache.keySet().removeIf(k -> k.startsWith(chatId));
    }

    public ChatSession updateChatTitle(String chatId, String newTitle) {
        return sessionRepository.findById(chatId).map(session -> {
            session.setTitle(newTitle);
            return sessionRepository.save(session);
        }).orElseThrow(() -> new RuntimeException("Chat not found"));
    }

    public ChatResponse chat(String chatId, String message, String mode) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Processing chat request - Chat ID: {}, Message: {}, Mode: {}", chatId, message, mode);

            // Ensure chat exists
            if (!sessionRepository.existsById(chatId)) {
                log.info("Chat ID {} not found, creating new session.", chatId);
                ChatSession session = ChatSession.builder().id(chatId).title("Chat " + chatId.substring(0, 8)).build();
                sessionRepository.save(session);
            }

            // 1. Save User Message
            ChatMessage userMsg = ChatMessage.builder()
                    .chatId(chatId)
                    .type("USER")
                    .content(message)
                    .build();
            messageRepository.save(userMsg);

            // Get chat-specific agent
            CourseAgent agent = getOrCreateAgent(chatId, mode);

            // 2. Generate Answer using Native RAG
            // The agent will handle retrieval, prompt construction, and LLM call.
            log.info("Delegating to Agent...");

            Result<String> result = agent.chat(chatId, message);

            String answer = result.content();
            log.info("Generated Answer: {}", answer);

            // 3. Save AI Response
            ChatMessage aiMsg = ChatMessage.builder()
                    .chatId(chatId)
                    .type("AI")
                    .content(answer)
                    .build();
            messageRepository.save(aiMsg);

            // 4. Extract Sources from Result
            List<ChatResponse.SourceDocument> sources = new ArrayList<>();
            if (result.sources() != null && !isAnswerNegative(answer)) {
                sources = result.sources().stream()
                        .map(content -> {
                            TextSegment segment = content.textSegment();
                            String filename = segment.metadata().getString("filename");
                            String chunkIndexStr = segment.metadata().getString("chunk_index");
                            int chunkIndex = chunkIndexStr != null ? Integer.parseInt(chunkIndexStr) : 0;

                            String text = segment.text();
                            String excerpt = text.length() > 200 ? text.substring(0, 200) + "..." : text;

                            return ChatResponse.SourceDocument.builder()
                                    .filename(filename != null ? filename : "Unknown")
                                    .excerpt(excerpt)
                                    .chunkIndex(chunkIndex)
                                    .build();
                        })
                        .collect(Collectors.toList());
            }

            log.info("Generated response for Chat ID: {} with {} sources", chatId, sources.size());
            log.info("Total request time: {} ms", System.currentTimeMillis() - startTime);

            return ChatResponse.builder()
                    .answer(answer)
                    .sources(sources)
                    .build();

        } catch (Exception e) {
            log.error("Error processing chat for Chat ID: {}", chatId, e);
            return ChatResponse.builder()
                    .answer("I apologize, but I encountered an error while processing your request. Please try again.")
                    .sources(new ArrayList<>())
                    .build();
        }
    }

    private boolean isAnswerNegative(String answer) {
        if (answer == null)
            return true;
        String lower = answer.toLowerCase();
        return lower.contains("i cannot answer")
                || (lower.contains("i cannot") && lower.contains("provided documents"))
                || lower.contains("i cannot answer this question based on the provided documents")
                || (lower.contains("don't know") && lower.contains("context"));
    }
}
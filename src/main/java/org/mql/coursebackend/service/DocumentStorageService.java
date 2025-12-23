package org.mql.coursebackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;

import org.mql.coursebackend.dto.DocumentInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentStorageService {

    private final Map<String, DocumentInfo> documents = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final File storageFile;
    private final ChromaDocumentRecoveryService recoveryService;

    public DocumentStorageService(@Lazy ChromaDocumentRecoveryService recoveryService) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.storageFile = new File("documents-metadata.json");
        this.recoveryService = recoveryService;
    }

    @PostConstruct
    public void loadDocuments() {
        if (storageFile.exists()) {
            try {
                Map<String, DocumentInfo> loadedDocs = objectMapper.readValue(
                        storageFile,
                        new TypeReference<Map<String, DocumentInfo>>() {
                        });
                documents.putAll(loadedDocs);
                System.out.println("Loaded " + documents.size() + " documents from storage");
            } catch (IOException e) {
                System.err.println("Failed to load documents: " + e.getMessage());
            }
        }

        if (documents.isEmpty()) {
            System.out.println("No documents in storage, attempting recovery from ChromaDB...");
            try {
                List<DocumentInfo> recovered = recoveryService.recoverDocumentsFromChroma();
                System.out.println("Recovered " + recovered.size() + " documents from ChromaDB");
            } catch (Exception e) {
                System.err.println("Failed to recover documents from ChromaDB: " + e.getMessage());
            }
        }
    }

    private void saveDocuments() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, documents);
        } catch (IOException e) {
            System.err.println("Failed to save documents: " + e.getMessage());
        }
    }

    public DocumentInfo addDocument(String filename, long fileSize, int totalChunks, String chatId) {
        String id = UUID.randomUUID().toString();
        DocumentInfo documentInfo = DocumentInfo.builder()
                .id(id)
                .filename(filename)
                .fileSize(fileSize)
                .uploadedAt(LocalDateTime.now())
                .totalChunks(totalChunks)
                .chatId(chatId)
                .build();

        documents.put(id, documentInfo);
        saveDocuments();
        return documentInfo;
    }

    public List<DocumentInfo> getAllDocuments(String chatId) {
        List<DocumentInfo> allDocs = new ArrayList<>(documents.values());
        if (chatId == null || chatId.isEmpty()) {
            return allDocs.stream()
                    .filter(d -> d.getChatId() == null || d.getChatId().isEmpty())
                    .toList();
        }
        return allDocs.stream()
                .filter(d -> chatId.equals(d.getChatId()))
                .toList();
    }

    public Optional<DocumentInfo> getDocument(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public void deleteDocument(String id) {
        documents.remove(id);
        saveDocuments();
    }
}

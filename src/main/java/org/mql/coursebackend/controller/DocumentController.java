package org.mql.coursebackend.controller;

import lombok.RequiredArgsConstructor;

import org.mql.coursebackend.dto.DocumentInfo;
import org.mql.coursebackend.service.ChromaDocumentRecoveryService;
import org.mql.coursebackend.service.DocumentStorageService;
import org.mql.coursebackend.service.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final IngestionService ingestionService;
    private final DocumentStorageService documentStorageService;
    private final ChromaDocumentRecoveryService chromaRecoveryService;

    @PostMapping
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "chatId", required = false) String chatId) {
        try {
            ingestionService.ingest(file, chatId);
            return ResponseEntity.ok("Document ingested successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to ingest document: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfo>> getAllDocuments(
            @RequestParam(value = "chatId", required = false) String chatId) {
        List<DocumentInfo> documents = documentStorageService.getAllDocuments(chatId);
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable String id) {
        documentStorageService.deleteDocument(id);
        return ResponseEntity.ok("Document deleted successfully");
    }

    @PostMapping("/recover")
    public ResponseEntity<List<DocumentInfo>> recoverDocumentsFromChroma() {
        List<DocumentInfo> recovered = chromaRecoveryService.recoverDocumentsFromChroma();
        return ResponseEntity.ok(recovered);
    }
}

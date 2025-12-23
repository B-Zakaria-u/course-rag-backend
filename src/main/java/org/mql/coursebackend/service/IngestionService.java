package org.mql.coursebackend.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import dev.langchain4j.data.document.DocumentSplitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentStorageService documentStorageService;

    private static final int MAX_DOCUMENTS_PER_CHAT = 5;

    @SuppressWarnings("deprecation")
    public void ingest(MultipartFile file, String chatId) throws IOException {
        if (chatId != null && !chatId.isEmpty()) {
            int currentCount = documentStorageService.getAllDocuments(chatId).size();
            if (currentCount >= MAX_DOCUMENTS_PER_CHAT) {
                throw new IllegalStateException(
                        "Maximum number of documents (" + MAX_DOCUMENTS_PER_CHAT + ") reached for this chat.");
            }
        }

        try (InputStream inputStream = file.getInputStream()) {
            Document document = new ApacheTikaDocumentParser().parse(inputStream);

            document.metadata().add("filename", file.getOriginalFilename());
            document.metadata().add("source", file.getOriginalFilename());
            document.metadata().add("file_size", String.valueOf(file.getSize()));

            if (chatId != null && !chatId.isEmpty()) {
                document.metadata().add("chatId", chatId);
            }

            DocumentSplitter splitter = DocumentSplitters.recursive(
                    1000,
                    100);

            List<TextSegment> segments = splitter.split(document);
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);

                Document segmentDocument = Document.from(
                        segment.text(),
                        segment.metadata().copy());
                segmentDocument.metadata().add("chunk_index", String.valueOf(i));
                segmentDocument.metadata().add("total_chunks", String.valueOf(segments.size()));

                documents.add(segmentDocument);
            }
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build();
            ingestor.ingest(documents);

            documentStorageService.addDocument(
                    file.getOriginalFilename(),
                    file.getSize(),
                    segments.size(),
                    chatId);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to ingest document: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Ingestion failed", e);
        }
    }
}

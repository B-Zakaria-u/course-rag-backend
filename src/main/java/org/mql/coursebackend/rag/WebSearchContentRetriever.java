package org.mql.coursebackend.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.mql.coursebackend.service.WebScraperService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSearchContentRetriever implements ContentRetriever {

    private final WebScraperService webScraperService;

    @Override
    public List<Content> retrieve(Query query) {
        log.info("Web Retrieval for: {}", query.text());
        List<Content> contents = new ArrayList<>();

        List<String> urls = webScraperService.search(query.text());

        for (String url : urls) {
            String text = webScraperService.scrape(url);
            if (!text.isEmpty()) {
                Metadata metadata = new Metadata();
                metadata.put("filename", url);
                metadata.put("chunk_index", "0");
                TextSegment segment = TextSegment.from(text, metadata);
                contents.add(Content.from(segment));
            }
        }

        return contents;
    }
}

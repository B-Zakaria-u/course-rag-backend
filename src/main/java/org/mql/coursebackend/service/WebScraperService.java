package org.mql.coursebackend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class WebScraperService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    public List<String> search(String query) {
        List<String> urls = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            log.info("Searching URL: {}", searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(5000)
                    .get();

            Elements results = doc.select(".result__a");

            for (Element result : results) {
                String url = result.attr("href");
                if (url != null && !url.isEmpty() && !url.contains("duckduckgo.com") && urls.size() < 5) {
                    urls.add(url);
                }
            }
        } catch (IOException e) {
            log.error("Search failed for query: {}", query, e);
        }
        return urls;
    }

    public String scrape(String url) {
        try {
            log.info("Scraping URL: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(5000)
                    .get();

            doc.select("script, style, nav, footer, header").remove();
            String text = doc.body().text();
            return text.length() > 2000 ? text.substring(0, 2000) : text;
        } catch (IOException e) {
            log.error("Scraping failed for URL: {}", url, e);
            return "";
        }
    }
}

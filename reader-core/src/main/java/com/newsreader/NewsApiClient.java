package com.newsreader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsreader.model.NewsApiArticle;

public class NewsApiClient {
    private static final Logger logger = LoggerFactory.getLogger(NewsApiClient.class);
    private static final String BASE_URL = "https://newsapi.org/v2/";
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NewsApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }


    public List<NewsApiArticle> getEverything(String domain) {
        try {
            String url = BASE_URL + "everything" +
                    "?domains=" + domain +
                    "&apiKey=" + apiKey;

            HttpGet request = new HttpGet(url);
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String result = EntityUtils.toString(entity);
                JsonNode rootNode = objectMapper.readTree(result);
                
                if (rootNode.has("status") && "ok".equals(rootNode.get("status").asText())) {
                    JsonNode articlesNode = rootNode.get("articles");
                    List<NewsApiArticle> articles = new ArrayList<>();
                    
                    for (JsonNode articleNode : articlesNode) {
                        NewsApiArticle article = objectMapper.treeToValue(articleNode, NewsApiArticle.class);
                        articles.add(article);
                    }
                    
                    return articles;
                } else {
                    String errorMessage = rootNode.has("message") ? rootNode.get("message").asText() : "Unknown error";
                    logger.error("API Error: {}", errorMessage);
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching news: ", e);
        }
        
        return new ArrayList<>();
    }
}
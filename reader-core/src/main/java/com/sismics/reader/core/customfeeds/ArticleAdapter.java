package com.sismics.reader.core.customfeeds;

import java.util.Date;
import java.util.UUID;

import com.sismics.reader.core.model.jpa.Article;

/**
 * Adapter class for creating Article objects.
 * This class simplifies the creation and configuration of Article entities.
 * 
 * @author you
 */
public class ArticleAdapter {
    
    /**
     * Creates a new Article from a URL.
     * 
     * @param url The URL of the article
     * @param feedId The ID of the feed this article belongs to
     * @return A configured Article object
     */
    public Article fromUrl(String url, String feedId) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (feedId == null || feedId.isEmpty()) {
            throw new IllegalArgumentException("Feed ID cannot be null or empty");
        }
        
        Article article = new Article();
        
        // Set required fields
        article.setId(UUID.randomUUID().toString());
        article.setTitle(url);
        article.setFeedId(feedId);
        article.setUrl(url);
        article.setGuid(url); // Using URL as GUID as a default
        article.setPublicationDate(new Date());
        article.setCreateDate(new Date());
        
        return article;
    }
}
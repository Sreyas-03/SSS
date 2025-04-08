package com.newsreader.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import com.newsreader.model.NewsApiArticle;
import com.sismics.reader.core.model.jpa.Article;

public class ArticleConverter {
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    
    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static Article convertToArticle(NewsApiArticle newsApiArticle, String feedId) {
        Article article = new Article(UUID.randomUUID().toString());
        
        // Set required fields
        article.setFeedId(feedId);
        article.setGuid(newsApiArticle.getUrl()); // Using URL as GUID
        
        // Parse and set publication date
        Date pubDate = parseDate(newsApiArticle.getPublishedAtStr());
        article.setPublicationDate(pubDate != null ? pubDate : new Date());
        
        // Set creation date
        article.setCreateDate(new Date());
        
        // Set other available fields
        article.setTitle(newsApiArticle.getTitle());
        article.setUrl(newsApiArticle.getUrl());
        article.setCreator(newsApiArticle.getAuthor());
        article.setDescription(newsApiArticle.getContent() != null ? 
                newsApiArticle.getContent() : newsApiArticle.getDescription());
        
        return article;
    }
    
    private static Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            return ISO_FORMAT.parse(dateStr);
        } catch (ParseException e) {
            // Try to handle other formats
            try {
                // Handle format with milliseconds
                if (dateStr.contains(".")) {
                    dateStr = dateStr.substring(0, dateStr.indexOf(".")) + "Z";
                }
                return ISO_FORMAT.parse(dateStr);
            } catch (ParseException ex) {
                return new Date(); // Fallback to current date
            }
        }
    }
}
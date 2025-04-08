package com.newsreader;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.newsreader.model.NewsApiArticle;
import com.newsreader.util.ArticleConverter;
import com.sismics.reader.core.dao.file.rss.RssReader;
import com.sismics.reader.core.model.jpa.Article;
import com.sismics.reader.core.model.jpa.Feed;

public class NewsApiAdapter {
    private static final Logger logger = LoggerFactory.getLogger(NewsApiAdapter.class);
    private final NewsApiClient newsApiClient;
    private String feedId;
    // private final String feedId;

    public NewsApiAdapter(String apiKey) {
        this.newsApiClient = new NewsApiClient(apiKey);
        this.feedId = UUID.randomUUID().toString(); // Generate a feed ID
    }

    public NewsApiAdapter(String apiKey, String feedId) {
        this.newsApiClient = new NewsApiClient(apiKey);
        this.feedId = feedId;
    }

    /**
     * Search for articles and convert them to Article format.
     * 
     * @param query  Search query
     * @param domain URL to search in
     * @return List of articles in your application's format
     */
    /**
     * Search for articles and convert them to an RssReader format.
     *
     * @param domain URL to search in
     * @return RssReader object populated with articles from News API
     */
    public RssReader searchArticlesToRssReader(String domain) {
        String newsApiDomain = domain.replaceAll("^(https?://)?(www\\.)?", "").replaceAll("/+$", "");
        
        // Get articles from News API
        List<NewsApiArticle> newsApiArticles = newsApiClient.getEverything(newsApiDomain);
        //  if the list is empty then return an empty RssReader
        if (newsApiArticles.isEmpty()) {
            return new RssReader() {
                @Override
                public Feed getFeed() {
                    return null;
                }

                @Override
                public List<Article> getArticleList() {
                    return new ArrayList<>();
                }
            };
        }

        // Create a new Feed object
        Feed feed = new Feed();
        feed.setId(this.feedId);
        feed.setRssUrl(domain);
        feed.setUrl(domain);
        feed.setTitle("News API Feed: " + domain);
        feed.setDescription("Articles from News API for domain: " + domain);
        feed.setCreateDate(new Date());
        feed.setLastFetchDate(new Date());

        // Convert News API articles to Article objects
        List<Article> articles = convertArticles(newsApiArticles);

        // Create a custom RssReader with our data
        RssReader rssReader = new CustomRssReader(feed, articles);

        return rssReader;
    }

    private static class CustomRssReader extends RssReader {
        private Feed feed;
        private List<Article> articleList;

        public CustomRssReader(Feed feed, List<Article> articleList) {
            super();
            this.feed = feed;
            this.articleList = articleList;
        }

        @Override
        public Feed getFeed() {
            return feed;
        }

        @Override
        public List<Article> getArticleList() {
            return articleList;
        }
    }

    private List<Article> convertArticles(List<NewsApiArticle> newsApiArticles) {
        List<Article> articles = new ArrayList<>();

        for (NewsApiArticle newsApiArticle : newsApiArticles) {
            try {
                Article article = ArticleConverter.convertToArticle(newsApiArticle, feedId);
                articles.add(article);
            } catch (Exception e) {
                logger.error("Error converting article: {}", newsApiArticle.getTitle(), e);
            }
        }

        return articles;
    }

    public void displayArticles(List<Article> articles) {
        System.out.println("\n===== Articles =====");
        
        if (articles.isEmpty()) {
            System.out.println("No articles found.");
            return;
        }
        
        for (int i = 0; i < articles.size(); i++) {
            Article article = articles.get(i);
            System.out.println((i + 1) + ". " + article.getTitle());
            System.out.println("   Author: " + article.getCreator());
            System.out.println("   Published: " + article.getPublicationDate());
            System.out.println("   URL: " + article.getUrl());
            System.out.println();
        }
    }
}

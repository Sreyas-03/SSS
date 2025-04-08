package com.sismics.reader.core.customfeeds;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sismics.reader.core.dao.file.rss.RssReader;
import com.sismics.reader.core.model.jpa.Article;
import com.sismics.reader.core.model.jpa.Feed;


public class CustomFeedAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CustomFeedAdapter.class);
    private final String feedId;
    private final String feedTitle;
    private final String author;
    // private final String feedId;

    public CustomFeedAdapter(String feedTitle) {
        this.feedId = UUID.randomUUID().toString(); // Generate a feed ID
        this.feedTitle = feedTitle;
        this.author = "Anonymous";
    }

    public CustomFeedAdapter(String userName, String feedId, String feedTitle) {
        this.feedId = feedId;
        this.feedTitle = feedTitle;
        this.author = userName;
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
    public RssReader searchArticlesToRssReader(List<String> customArticlesUrlList) {
        // String newsApiDomain = domain.replaceAll("^(https?://)?(www\\.)?", "").replaceAll("/+$", "");
        
        // Get articles from News API
        // List<NewsApiArticle> newsApiArticles = newsApiClient.getEverything(newsApiDomain);
        List<Article> customArticleList = new ArrayList<>();
        ArticleAdapter articleAdapter = new ArticleAdapter();
        
        for (String customArticleUrl : customArticlesUrlList) {
            Article customArticle = articleAdapter.fromUrl(customArticleUrl, feedId);
            customArticleList.add(customArticle);
        }
        // Create a new Feed object
        Feed feed = new Feed();
        feed.setId(this.feedId);
        feed.setRssUrl(this.feedId);
        feed.setUrl(this.feedId);
        feed.setTitle(this.feedTitle);
        feed.setDescription(this.author);
        feed.setCreateDate(new Date());
        feed.setLastFetchDate(new Date());

        // Convert News API articles to Article objects
        // List<Article> articles = convertArticles(newsApiArticles);

        // Create a custom RssReader with our data
        RssReader rssReader = new CustomRssReader(feed, customArticleList);

        return rssReader;
    }

    private static class CustomRssReader extends RssReader {
        private final Feed feed;
        private final List<Article> articleList;

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
    //  The idea is that, once the RssReader object is created, we will call the Feedservice.Synchronise method.


    // public void displayArticles(List<Article> articles) {
    //     System.out.println("\n===== Articles =====");
        
    //     if (articles.isEmpty()) {
    //         System.out.println("No articles found.");
    //         return;
    //     }
        
    //     for (int i = 0; i < articles.size(); i++) {
    //         Article article = articles.get(i);
    //         System.out.println((i + 1) + ". " + article.getTitle());
    //         System.out.println("   Author: " + article.getCreator());
    //         System.out.println("   Published: " + article.getPublicationDate());
    //         System.out.println("   URL: " + article.getUrl());
    //         System.out.println();
    //     }
    // }
}


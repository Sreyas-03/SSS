package com.sismics.reader.core.service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.sismics.reader.core.dao.file.html.FeedChooserStrategy;
import com.sismics.reader.core.dao.file.html.RssExtractor;
import com.sismics.reader.core.dao.file.rss.RssReader;
import com.sismics.reader.core.dao.jpa.*;
import com.sismics.reader.core.dao.jpa.criteria.ArticleCriteria;
import com.sismics.reader.core.dao.jpa.criteria.FeedCriteria;
import com.sismics.reader.core.dao.jpa.criteria.FeedSubscriptionCriteria;
import com.sismics.reader.core.dao.jpa.criteria.UserArticleCriteria;
import com.sismics.reader.core.dao.jpa.dto.ArticleDto;
import com.sismics.reader.core.dao.jpa.dto.FeedDto;
import com.sismics.reader.core.dao.jpa.dto.FeedSubscriptionDto;
import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.event.ArticleCreatedAsyncEvent;
import com.sismics.reader.core.event.ArticleDeletedAsyncEvent;
import com.sismics.reader.core.event.ArticleUpdatedAsyncEvent;
import com.sismics.reader.core.event.FaviconUpdateRequestedEvent;
import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.model.jpa.*;
import com.sismics.reader.core.strategy.FeedParsingStrategy.HtmlPageParsingStrategy;
import com.sismics.reader.core.strategy.FeedParsingStrategy.NewsApiParsingStrategy;
import com.sismics.reader.core.strategy.FeedParsingStrategy.RssAtomParsingStrategy;
import com.sismics.reader.core.util.EntityManagerUtil;
import com.sismics.reader.core.util.TransactionUtil;
import com.sismics.reader.core.util.http.ReaderHttpClient;
import com.sismics.reader.core.util.sanitizer.ArticleSanitizer;
import com.sismics.reader.core.util.sanitizer.TextSanitizer;
import com.sismics.util.UrlUtil;
import com.sismics.reader.core.constant.Constants;
import com.sismics.reader.core.strategy.FeedParsingStrategy;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.newsreader.NewsApiAdapter;

/**
 * Feed service.
 *
 * @author jtremeaux 
 */
public class FeedService extends AbstractScheduledService {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    boolean firstTime = true;

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
    }

    @Override
    protected void runOneIteration() {
        // Don't let Guava manage our exceptions, or they will be swallowed and the service will silently stop
        try {
            TransactionUtil.handle(() -> synchronizeAllFeeds());
        } catch (Throwable t) {
            log.error("Error synchronizing feeds", t);
        }
    }
    
    @Override
    protected Scheduler scheduler() {
        // TODO Implement a better schedule strategy... Use update period specified in the feed if avail & use last update date from feed to backoff
        return Scheduler.newFixedDelaySchedule(0, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Synchronize all feeds.
     */
    public void synchronizeAllFeeds() {
        // Update all feeds currently having subscribed users
        FeedDao feedDao = new FeedDao();
        FeedCriteria feedCriteria = new FeedCriteria()
                .setWithUserSubscription(true);
        List<FeedDto> feedList = feedDao.findByCriteria(feedCriteria);
        List<FeedSynchronization> feedSynchronizationList = new ArrayList<FeedSynchronization>();
        for (FeedDto feed : feedList) {
            FeedSynchronization feedSynchronization = new FeedSynchronization();
            feedSynchronization.setFeedId(feed.getId());
            feedSynchronization.setSuccess(true);
            long startTime = System.currentTimeMillis();
            
            try {
                synchronize(feed.getRssUrl());
            } catch (Exception e) {
                log.error(MessageFormat.format("Error synchronizing feed at URL: {0}", feed.getRssUrl()), e);
                feedSynchronization.setSuccess(false);
                feedSynchronization.setMessage(ExceptionUtils.getStackTrace(e));
            }
            feedSynchronization.setDuration((int) (System.currentTimeMillis() - startTime));
            feedSynchronizationList.add(feedSynchronization);
            TransactionUtil.commit();
        }

        // If all feeds have failed, then we infer that the network is probably down
        FeedSynchronizationDao feedSynchronizationDao = new FeedSynchronizationDao();
        boolean networkDown = true;
        for (FeedSynchronization feedSynchronization : feedSynchronizationList) {
            if (feedSynchronization.isSuccess()) {
                networkDown = false;
                break;
            }
        }

        if(firstTime){
            firstTime = false;
            String filePath = "./src/main/webapp/custom-feeds.json"; // Replace with the actual file path
            try (FileWriter fileWriter = new FileWriter(filePath)) {
                fileWriter.write(""); // Overwrite the file with an empty string
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Update the status of all synchronized feeds
        if (!networkDown) {
            for (FeedSynchronization feedSynchronization : feedSynchronizationList) {
                feedSynchronizationDao.create(feedSynchronization);
                feedSynchronizationDao.deleteOldFeedSynchronization(feedSynchronization.getFeedId(), 600);
            }
            TransactionUtil.commit();
        }
    }

    /**
     * Synchronize the feed to local database.
     * 
     * @param url RSS url of a feed or page containing a feed to synchronize
     */
    public Feed synchronize(String url) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Parse the feed
        RssReader rssReader = parseFeedOrPage(url, true);
        Feed newFeed = rssReader.getFeed();
        List<Article> articleList = rssReader.getArticleList();
        ArticleFeedService articleFeedService = new ArticleFeedService();
        articleFeedService.completeArticleList(articleList);

        // Get articles that were removed from RSS compared to last fetch
        List<Article> articleToRemove = articleFeedService.getArticleToRemove(articleList);
        if (!articleToRemove.isEmpty()) {
            for (Article article : articleToRemove) {
                // Update unread counts
                // FIXME count be optimized in 1 query instead of a*s*2
                List<UserArticleDto> userArticleDtoList = new UserArticleDao()
                        .findByCriteria(new UserArticleCriteria()
                                .setArticleId(article.getId())
                                .setFetchAllFeedSubscription(true) // to test: subscribe another user, u2, read u1, not u2, u1 is decremented anyway
                                .setUnread(true));

                for (UserArticleDto userArticleDto : userArticleDtoList) {
                    FeedSubscriptionDto feedSubscriptionDto = new FeedSubscriptionDao().findFirstByCriteria(new FeedSubscriptionCriteria()
                            .setId(userArticleDto.getFeedSubscriptionId()));
                    if (feedSubscriptionDto != null) {
                        new FeedSubscriptionDao().updateUnreadCount(feedSubscriptionDto.getId(), feedSubscriptionDto.getUnreadUserArticleCount() - 1);
                    }
                }
            }

            // Delete articles that don't exist anymore
            for (Article article: articleToRemove) {
                new ArticleDao().delete(article.getId());
            }

            // Removed articles from index
            ArticleDeletedAsyncEvent articleDeletedAsyncEvent = new ArticleDeletedAsyncEvent();
            articleDeletedAsyncEvent.setArticleList(articleToRemove);
            AppContext.getInstance().getAsyncEventBus().post(articleDeletedAsyncEvent);
        }

        // Create the feed if necessary (not created and currently in use by another user)
        FeedDao feedDao = new FeedDao();
        String rssUrl = newFeed.getRssUrl();
        Feed feed = feedDao.getByRssUrl(rssUrl);
        if (feed == null) {
            feed = new Feed();
            feed.setUrl(newFeed.getUrl());
            feed.setBaseUri(newFeed.getBaseUri());
            feed.setRssUrl(rssUrl);
            feed.setTitle(StringUtils.abbreviate(newFeed.getTitle(), 100));
            feed.setLanguage(newFeed.getLanguage() != null && newFeed.getLanguage().length() <= 10 ? newFeed.getLanguage() : null);
            feed.setDescription(StringUtils.abbreviate(newFeed.getDescription(), 4000));
            feed.setLastFetchDate(new Date());
            feedDao.create(feed);
            EntityManagerUtil.flush();

            // Try to download the feed's favicon
            FaviconUpdateRequestedEvent faviconUpdateRequestedEvent = new FaviconUpdateRequestedEvent();
            faviconUpdateRequestedEvent.setFeed(feed);
            AppContext.getInstance().getAsyncEventBus().post(faviconUpdateRequestedEvent);
        } else {
            FaviconFeedService faviconFeedService = new FaviconFeedService();   
            // Try to update the feed's favicon every week
            boolean updateFavicon = faviconFeedService.isFaviconUpdated(feed);

            // Update metadata
            feed.setUrl(newFeed.getUrl());
            feed.setBaseUri(newFeed.getBaseUri());
            feed.setTitle(StringUtils.abbreviate(newFeed.getTitle(), 100));
            feed.setLanguage(newFeed.getLanguage() != null && newFeed.getLanguage().length() <= 10 ? newFeed.getLanguage() : null);
            feed.setDescription(StringUtils.abbreviate(newFeed.getDescription(), 4000));
            feed.setLastFetchDate(new Date());
            feedDao.update(feed);

            // Update the favicon
            if (updateFavicon) {
                FaviconUpdateRequestedEvent faviconUpdateRequestedEvent = new FaviconUpdateRequestedEvent();
                faviconUpdateRequestedEvent.setFeed(feed);
                AppContext.getInstance().getAsyncEventBus().post(faviconUpdateRequestedEvent);
            }
        }
        
        // Update existing articles
        Map<String, Article> articleMap = new HashMap<String, Article>();
        for (Article article : articleList) {
            articleMap.put(article.getGuid(), article);
        }

        List<String> guidIn = new ArrayList<String>();
        for (Article article : articleList) {
            guidIn.add(article.getGuid());
        }
        
        ArticleSanitizer sanitizer = new ArticleSanitizer();
        ArticleDao articleDao = new ArticleDao();
        if (!guidIn.isEmpty()) {
            ArticleCriteria articleCriteria = new ArticleCriteria()
                    .setFeedId(feed.getId())
                    .setGuidIn(guidIn);
            List<ArticleDto> currentArticleDtoList = articleDao.findByCriteria(articleCriteria);
            List<Article> articleUpdatedList = new ArrayList<Article>();
            for (ArticleDto currentArticle : currentArticleDtoList) {
                Article newArticle = articleMap.remove(currentArticle.getGuid());
                
                Article article = new Article();
                article.setPublicationDate(currentArticle.getPublicationDate());
                article.setId(currentArticle.getId());
                article.setFeedId(feed.getId());
                article.setUrl(newArticle.getUrl());
                article.setTitle(StringUtils.abbreviate(TextSanitizer.sanitize(newArticle.getTitle()), 4000));
                article.setCreator(StringUtils.abbreviate(newArticle.getCreator(), 200));
                String baseUri = UrlUtil.getBaseUri(feed, newArticle);
                article.setDescription(sanitizer.sanitize(baseUri, newArticle.getDescription()));
                article.setCommentUrl(newArticle.getCommentUrl());
                article.setCommentCount(newArticle.getCommentCount());
                article.setEnclosureUrl(newArticle.getEnclosureUrl());
                article.setEnclosureLength(newArticle.getEnclosureLength());
                article.setEnclosureType(newArticle.getEnclosureType());

                if (!Strings.nullToEmpty(currentArticle.getTitle()).equals(Strings.nullToEmpty(article.getTitle())) ||
                        !Strings.nullToEmpty(currentArticle.getDescription()).equals(Strings.nullToEmpty(article.getDescription()))) {
                    articleDao.update(article);
                    articleUpdatedList.add(article);
                }
            }
            
            // Update indexed article
            if (!articleUpdatedList.isEmpty()) {
                ArticleUpdatedAsyncEvent articleUpdatedAsyncEvent = new ArticleUpdatedAsyncEvent();
                articleUpdatedAsyncEvent.setArticleList(articleUpdatedList);
                AppContext.getInstance().getAsyncEventBus().post(articleUpdatedAsyncEvent);
            }
        }
        
        // Create new articles
        if (!articleMap.isEmpty()) {
            FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                    .setFeedId(feed.getId());
            
            FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
            List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
            
            UserArticleDao userArticleDao = new UserArticleDao();
            for (Article article : articleMap.values()) {
                // Create the new article
                article.setFeedId(feed.getId());
                article.setTitle(StringUtils.abbreviate(TextSanitizer.sanitize(article.getTitle()), 4000));
                article.setCreator(StringUtils.abbreviate(article.getCreator(), 200));
                String baseUri = UrlUtil.getBaseUri(feed, article);
                article.setDescription(sanitizer.sanitize(baseUri, article.getDescription()));
                articleDao.create(article);
    
                // Create the user articles eagerly for users already subscribed
                // FIXME count be optimized in 1 query instad of a*s
                for (FeedSubscriptionDto feedSubscription : feedSubscriptionList) {
                    UserArticle userArticle = new UserArticle();
                    userArticle.setArticleId(article.getId());
                    userArticle.setUserId(feedSubscription.getUserId());
                    userArticleDao.create(userArticle);

                    feedSubscription.setUnreadUserArticleCount(feedSubscription.getUnreadUserArticleCount() + 1);
                    feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount());
                }
            }

            // Add new articles to the index
            ArticleCreatedAsyncEvent articleCreatedAsyncEvent = new ArticleCreatedAsyncEvent();
            articleCreatedAsyncEvent.setArticleList(Lists.newArrayList(articleMap.values()));
            AppContext.getInstance().getAsyncEventBus().post(articleCreatedAsyncEvent);
        }

        long endTime = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            log.info(MessageFormat.format("Synchronized feed at URL {0} in {1}ms, {2} articles added, {3} deleted", url, endTime - startTime, articleMap.size(), articleToRemove.size()));
        }
        
        return feed;
    }



    /**
     * Synchronize the feed to local database.
     * 
     * @param rssReader RSS url of a feed or page containing a feed to synchronize
     */
    public Feed synchronize(RssReader rssReader) {
        long startTime = System.currentTimeMillis();
        
        // Parse the feed
        Feed newFeed = rssReader.getFeed();
        String url = newFeed.getRssUrl();
        List<Article> articleList = rssReader.getArticleList();
        ArticleFeedService articleFeedService = new ArticleFeedService();
        articleFeedService.completeArticleList(articleList);

        // Get articles that were removed from RSS compared to last fetch
        List<Article> articleToRemove = articleFeedService.getArticleToRemove(articleList);
        if (!articleToRemove.isEmpty()) {
            for (Article article : articleToRemove) {
                // Update unread counts
                // FIXME count be optimized in 1 query instead of a*s*2
                List<UserArticleDto> userArticleDtoList = new UserArticleDao()
                        .findByCriteria(new UserArticleCriteria()
                                .setArticleId(article.getId())
                                .setFetchAllFeedSubscription(true) // to test: subscribe another user, u2, read u1, not u2, u1 is decremented anyway
                                .setUnread(true));

                for (UserArticleDto userArticleDto : userArticleDtoList) {
                    FeedSubscriptionDto feedSubscriptionDto = new FeedSubscriptionDao().findFirstByCriteria(new FeedSubscriptionCriteria()
                            .setId(userArticleDto.getFeedSubscriptionId()));
                    if (feedSubscriptionDto != null) {
                        new FeedSubscriptionDao().updateUnreadCount(feedSubscriptionDto.getId(), feedSubscriptionDto.getUnreadUserArticleCount() - 1);
                    }
                }
            }

            // Delete articles that don't exist anymore
            for (Article article: articleToRemove) {
                new ArticleDao().delete(article.getId());
            }

            // Removed articles from index
            ArticleDeletedAsyncEvent articleDeletedAsyncEvent = new ArticleDeletedAsyncEvent();
            articleDeletedAsyncEvent.setArticleList(articleToRemove);
            AppContext.getInstance().getAsyncEventBus().post(articleDeletedAsyncEvent);
        }

        // Create the feed if necessary (not created and currently in use by another user)
        FeedDao feedDao = new FeedDao();
        String rssUrl = newFeed.getRssUrl();
        Feed feed = feedDao.getByRssUrl(rssUrl);
        if (feed == null) {
            feed = new Feed();
            feed.setUrl(newFeed.getUrl());
            feed.setBaseUri(newFeed.getBaseUri());
            feed.setRssUrl(rssUrl);
            feed.setTitle(StringUtils.abbreviate(newFeed.getTitle(), 100));
            feed.setLanguage(newFeed.getLanguage() != null && newFeed.getLanguage().length() <= 10 ? newFeed.getLanguage() : null);
            feed.setDescription(StringUtils.abbreviate(newFeed.getDescription(), 4000));
            feed.setLastFetchDate(new Date());
            feedDao.create(feed);
            EntityManagerUtil.flush();

            // Try to download the feed's favicon
            FaviconUpdateRequestedEvent faviconUpdateRequestedEvent = new FaviconUpdateRequestedEvent();
            faviconUpdateRequestedEvent.setFeed(feed);
            AppContext.getInstance().getAsyncEventBus().post(faviconUpdateRequestedEvent);
        } else {
            FaviconFeedService faviconFeedService = new FaviconFeedService();   
            // Try to update the feed's favicon every week
            boolean updateFavicon = faviconFeedService.isFaviconUpdated(feed);

            // Update metadata
            feed.setUrl(newFeed.getUrl());
            feed.setBaseUri(newFeed.getBaseUri());
            feed.setTitle(StringUtils.abbreviate(newFeed.getTitle(), 100));
            feed.setLanguage(newFeed.getLanguage() != null && newFeed.getLanguage().length() <= 10 ? newFeed.getLanguage() : null);
            feed.setDescription(StringUtils.abbreviate(newFeed.getDescription(), 4000));
            feed.setLastFetchDate(new Date());
            feedDao.update(feed);

            // Update the favicon
            if (updateFavicon) {
                FaviconUpdateRequestedEvent faviconUpdateRequestedEvent = new FaviconUpdateRequestedEvent();
                faviconUpdateRequestedEvent.setFeed(feed);
                AppContext.getInstance().getAsyncEventBus().post(faviconUpdateRequestedEvent);
            }
        }
        
        // Update existing articles
        Map<String, Article> articleMap = new HashMap<String, Article>();
        for (Article article : articleList) {
            articleMap.put(article.getGuid(), article);
        }

        List<String> guidIn = new ArrayList<String>();
        for (Article article : articleList) {
            guidIn.add(article.getGuid());
        }
        
        ArticleSanitizer sanitizer = new ArticleSanitizer();
        ArticleDao articleDao = new ArticleDao();
        if (!guidIn.isEmpty()) {
            ArticleCriteria articleCriteria = new ArticleCriteria()
                    .setFeedId(feed.getId())
                    .setGuidIn(guidIn);
            List<ArticleDto> currentArticleDtoList = articleDao.findByCriteria(articleCriteria);
            List<Article> articleUpdatedList = new ArrayList<Article>();
            for (ArticleDto currentArticle : currentArticleDtoList) {
                Article newArticle = articleMap.remove(currentArticle.getGuid());
                
                Article article = new Article();
                article.setPublicationDate(currentArticle.getPublicationDate());
                article.setId(currentArticle.getId());
                article.setFeedId(feed.getId());
                article.setUrl(newArticle.getUrl());
                article.setTitle(StringUtils.abbreviate(TextSanitizer.sanitize(newArticle.getTitle()), 4000));
                article.setCreator(StringUtils.abbreviate(newArticle.getCreator(), 200));
                String baseUri = UrlUtil.getBaseUri(feed, newArticle);
                article.setDescription(sanitizer.sanitize(baseUri, newArticle.getDescription()));
                article.setCommentUrl(newArticle.getCommentUrl());
                article.setCommentCount(newArticle.getCommentCount());
                article.setEnclosureUrl(newArticle.getEnclosureUrl());
                article.setEnclosureLength(newArticle.getEnclosureLength());
                article.setEnclosureType(newArticle.getEnclosureType());

                if (!Strings.nullToEmpty(currentArticle.getTitle()).equals(Strings.nullToEmpty(article.getTitle())) ||
                        !Strings.nullToEmpty(currentArticle.getDescription()).equals(Strings.nullToEmpty(article.getDescription()))) {
                    articleDao.update(article);
                    articleUpdatedList.add(article);
                }
            }
            
            // Update indexed article
            if (!articleUpdatedList.isEmpty()) {
                ArticleUpdatedAsyncEvent articleUpdatedAsyncEvent = new ArticleUpdatedAsyncEvent();
                articleUpdatedAsyncEvent.setArticleList(articleUpdatedList);
                AppContext.getInstance().getAsyncEventBus().post(articleUpdatedAsyncEvent);
            }
        }
        
        // Create new articles
        if (!articleMap.isEmpty()) {
            FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                    .setFeedId(feed.getId());
            
            FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
            List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
            
            UserArticleDao userArticleDao = new UserArticleDao();
            for (Article article : articleMap.values()) {
                // Create the new article
                article.setFeedId(feed.getId());
                article.setTitle(StringUtils.abbreviate(TextSanitizer.sanitize(article.getTitle()), 4000));
                article.setCreator(StringUtils.abbreviate(article.getCreator(), 200));
                String baseUri = UrlUtil.getBaseUri(feed, article);
                article.setDescription(sanitizer.sanitize(baseUri, article.getDescription()));
                articleDao.create(article);
    
                // Create the user articles eagerly for users already subscribed
                // FIXME count be optimized in 1 query instad of a*s
                for (FeedSubscriptionDto feedSubscription : feedSubscriptionList) {
                    UserArticle userArticle = new UserArticle();
                    userArticle.setArticleId(article.getId());
                    userArticle.setUserId(feedSubscription.getUserId());
                    userArticleDao.create(userArticle);

                    feedSubscription.setUnreadUserArticleCount(feedSubscription.getUnreadUserArticleCount() + 1);
                    feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount());
                }
            }

            // Add new articles to the index
            ArticleCreatedAsyncEvent articleCreatedAsyncEvent = new ArticleCreatedAsyncEvent();
            articleCreatedAsyncEvent.setArticleList(Lists.newArrayList(articleMap.values()));
            AppContext.getInstance().getAsyncEventBus().post(articleCreatedAsyncEvent);
        }

        long endTime = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            log.info(MessageFormat.format("Synchronized feed at URL {0} in {1}ms, {2} articles added, {3} deleted", url, endTime - startTime, articleMap.size(), articleToRemove.size()));
        }
        
        return feed;
    }


    /**
     * Parse a page containing a RSS or Atom feed, or HTML linking to a feed.
     * 
     * @param url Url to parse
     * @param parsePage If true, try to parse the resource as an HTML page linking to a feed
     * @return Reader
     */
    private RssReader parseFeedOrPage(String url, boolean parsePage) throws Exception {
        try {
            return new RssAtomParsingStrategy().parse(url, parsePage); //Try RSS/Atom parsing first
        } catch (Exception eRss) {
            boolean recoverable = !(eRss instanceof UnknownHostException ||
                    eRss instanceof FileNotFoundException);
            if (parsePage && recoverable) {
                try {
                    return new HtmlPageParsingStrategy().parse(url, parsePage); //Try HTML page parsing
                } catch (Exception ePage) {
                    try {
                        return new NewsApiParsingStrategy().parse(url, parsePage); //Try NewsApi
                    } catch (Exception eNewApi) {
                        logParsingError(url, eNewApi); // Log error
                        throw eNewApi;
                    }
                }
            } else {
                logParsingError(url, eRss);
            }
            throw eRss;
        }
    }
    
    private void logParsingError(String url, Exception e) {
        if (log.isWarnEnabled()) {
            if (e instanceof UnknownHostException ||
                    e instanceof FileNotFoundException ||
                    e instanceof ConnectException) {
                log.warn(MessageFormat.format("Error parsing HTML page at URL {0} : {1}", url, e.getMessage()));
            } else {
                log.warn(MessageFormat.format("Error parsing HTML page at URL {0}", url));
            }
        }
    }
}

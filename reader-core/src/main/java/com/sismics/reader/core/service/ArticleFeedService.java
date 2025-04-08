package com.sismics.reader.core.service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.DurationFieldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.sismics.reader.core.dao.jpa.ArticleDao;
import com.sismics.reader.core.dao.jpa.FeedSubscriptionDao;
import com.sismics.reader.core.dao.jpa.UserArticleDao;
import com.sismics.reader.core.dao.jpa.criteria.ArticleCriteria;
import com.sismics.reader.core.dao.jpa.criteria.UserArticleCriteria;
import com.sismics.reader.core.dao.jpa.dto.ArticleDto;
import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.model.jpa.Article;
import com.sismics.reader.core.model.jpa.FeedSubscription;
import com.sismics.reader.core.model.jpa.UserArticle;
import com.sismics.reader.core.util.TransactionUtil;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.core.util.jpa.PaginatedLists;;

public class ArticleFeedService extends AbstractScheduledService {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(ArticleFeedService.class);

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
            FeedService feedService = new FeedService();
            TransactionUtil.handle(() -> feedService.synchronizeAllFeeds());
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
     * Create the first batch of user articles when subscribing to a feed, so that the user has at least
     * a few unread articles.
     * 
     * @param userId User ID
     * @param feedSubscription Feed subscription
     */
    public void createInitialUserArticle(String userId, FeedSubscription feedSubscription) {
        UserArticleCriteria userArticleCriteria = new UserArticleCriteria()
                .setUserId(userId)
                .setSubscribed(true)
                .setFeedId(feedSubscription.getFeedId());

        UserArticleDao userArticleDao = new UserArticleDao();
        PaginatedList<UserArticleDto> paginatedList = PaginatedLists.create(); //TODO we could fetch as many articles as in the feed, not 10
        userArticleDao.findByCriteria(paginatedList, userArticleCriteria, null, null);
        for (UserArticleDto userArticleDto : paginatedList.getResultList()) {
            if (userArticleDto.getId() == null) {
                UserArticle userArticle = new UserArticle();
                userArticle.setArticleId(userArticleDto.getArticleId());
                userArticle.setUserId(userId);
                userArticleDao.create(userArticle);
                feedSubscription.setUnreadCount(feedSubscription.getUnreadCount() + 1);
            } else if (userArticleDto.getReadTimestamp() == null) {
                feedSubscription.setUnreadCount(feedSubscription.getUnreadCount() + 1);
            }
        }

        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadCount());
    }

    /**
     * Add missing data to articles after parsing.
     *
     * @param articleList The list of articles
     */
    public void completeArticleList(List<Article> articleList) {
        for (Article article : articleList) {
            Date now = new Date();
            if (article.getPublicationDate() == null || article.getPublicationDate().after(now)) {
                article.setPublicationDate(now);
            }
        }
    }

    /**
     * Delete articles that were removed (ninja edited) from the feed.
     *
     * @param articleList Articles just downloaded
     */
    public List<Article> getArticleToRemove(List<Article> articleList) {
        List<Article> removedArticleList = new ArrayList<Article>();
        
        // Check if the oldest article from stream was already synced
        Article oldestArticle = getOldestArticle(articleList);
        if (oldestArticle == null) {
            return removedArticleList;
        }
        ArticleDto localArticle = new ArticleDao().findFirstByCriteria(new ArticleCriteria()
                .setGuidIn(Lists.newArrayList(oldestArticle.getGuid())));
        if (localArticle == null) {
            return removedArticleList;
        }

        // Get newer articles in stream
        List<Article> newerArticles = getNewerArticleList(articleList, oldestArticle);
        Set<String> newerArticleGuids = new HashSet<String>();
        for (Article article : newerArticles) {
            newerArticleGuids.add(article.getGuid());
        }

        // Get newer articles in local DB
        List<ArticleDto> newerLocalArticles = new ArticleDao().findByCriteria(new ArticleCriteria()
                .setFeedId(localArticle.getFeedId())
                .setPublicationDateMin(oldestArticle.getPublicationDate()));

        // Delete articles removed from stream, and not too old
        Date dateMin = new DateTime().withFieldAdded(DurationFieldType.days(), -1).toDate();
        for (ArticleDto newerLocalArticle : newerLocalArticles) {
            if (!newerArticleGuids.contains(newerLocalArticle.getGuid()) && newerLocalArticle.getCreateDate().after(dateMin)) {
                removedArticleList.add(new Article(newerLocalArticle.getId()));
            }
        }
        
        return removedArticleList;
    }

    private List<Article> getNewerArticleList(List<Article> articleList, Article oldestArticle) {
        List<Article> presentArticles = new ArrayList<Article>();
        for (Article article : articleList) {
            if (article.getPublicationDate().after(oldestArticle.getPublicationDate())) {
                presentArticles.add(article);
            }
        }
        return presentArticles;
    }

    private Article getOldestArticle(List<Article> articleList) {
        Article oldestArticle = null;
        for (Article article : articleList) {
            if (oldestArticle == null || article.getPublicationDate().before(oldestArticle.getPublicationDate())) { // check me
                oldestArticle = article;
            }
        }
        return oldestArticle;
    }
}

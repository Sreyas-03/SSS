package com.sismics.reader.core.service;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.sismics.reader.core.model.jpa.Feed;
import com.sismics.reader.core.util.TransactionUtil;;

public class FaviconFeedService extends AbstractScheduledService {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(FaviconFeedService.class);

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
     * Update the favicon once a week.
     *
     * @param feed The feed
     * @return True if the favicon must be updated
     */
    public boolean isFaviconUpdated(Feed feed) {
        boolean newDay = feed.getLastFetchDate() == null ||
                DateTime.now().getDayOfYear() != new DateTime(feed.getLastFetchDate()).getDayOfYear();
        int daysFromCreation = Days.daysBetween(Instant.now(), new Instant(feed.getCreateDate().getTime())).getDays();
        return newDay && daysFromCreation % 7 == 0;
    }
}


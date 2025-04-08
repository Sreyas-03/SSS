package com.sismics.reader.core.customfeeds;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sismics.reader.core.dao.file.rss.RssReader;
import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.service.FeedService;

public class CustomFeedCreateCommander {
    
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);


    public void createCustomFeed(String userName, String feedId, String feedTitle, List<String> customArticlesUrlList){
        CustomFeedAdapter customFeedAdapter = new CustomFeedAdapter(userName, feedId, feedTitle);
        RssReader rssReader = customFeedAdapter.searchArticlesToRssReader(customArticlesUrlList);
        final FeedService feedService = AppContext.getInstance().getFeedService();
        // Feed feed = feedService.synchronize(rssReader);
        feedService.synchronize(rssReader);
    }
    // By now, the new feed should have been created and the articles added to the database.
    // The next step is to allow the users to subscribe to these feeds based on the feedID.
}

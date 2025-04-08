package com.sismics.reader.core.strategy;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.newsreader.NewsApiAdapter;
import com.sismics.reader.core.constant.Constants;
import com.sismics.reader.core.dao.file.html.FeedChooserStrategy;
import com.sismics.reader.core.dao.file.html.RssExtractor;
import com.sismics.reader.core.dao.file.rss.RssReader;
import com.sismics.reader.core.util.http.ReaderHttpClient;

public interface FeedParsingStrategy {
    RssReader parse(String url, boolean parsePage) throws Exception;

    // Concrete Strategy 1: Parse as RSS/Atom Feed
    class RssAtomParsingStrategy implements FeedParsingStrategy {
        private static final Logger log = LoggerFactory.getLogger(RssAtomParsingStrategy.class);

        @Override
        public RssReader parse(String url, boolean parsePage) throws Exception {
            try {
                final RssReader reader = new RssReader();
                new ReaderHttpClient() {
                    @Override
                    public Void process(InputStream is) throws Exception {
                        reader.readRssFeed(is);
                        return null;
                    }
                }.open(new URL(url));
                reader.getFeed().setRssUrl(url);
                return reader;
            } catch (Exception eRss) {
                if (log.isDebugEnabled()) {
                    log.debug("Error parsing RSS/Atom feed: " + url, eRss);
                }
                throw eRss;
            }
        }
    }

    // Concrete Strategy 2: Parse as HTML Page Linking to a Feed
    class HtmlPageParsingStrategy implements FeedParsingStrategy {
        private static final Logger log = LoggerFactory.getLogger(HtmlPageParsingStrategy.class);

        @Override
        public RssReader parse(String url, boolean parsePage) throws Exception {
            try {
                final RssExtractor extractor = new RssExtractor(url);
                new ReaderHttpClient() {
                    @Override
                    public Void process(InputStream is) throws Exception {
                        extractor.readPage(is);
                        return null;
                    }
                }.open(new URL(url));
                List<String> feedList = extractor.getFeedList();
                if (feedList == null || feedList.isEmpty()) {
                    log.warn("No feed links found on page: " + url);
                    throw new Exception("No feed links found on page: " + url); // Throw exception instead of logging the parsing error
                }
                String feed = new FeedChooserStrategy().guess(feedList);
                return new RssAtomParsingStrategy().parse(feed, false); // Chain to RSS/Atom parsing
            } catch (Exception ePage) {
                if (log.isDebugEnabled()) {
                    log.debug("Error parsing HTML page for feeds: " + url, ePage);
                }
                throw ePage;
            }
        }
    }

    // Concrete Strategy 3: Parse using News API Adapter
    class NewsApiParsingStrategy implements FeedParsingStrategy {
        private static final Logger log = LoggerFactory.getLogger(NewsApiParsingStrategy.class);

        @Override
        public RssReader parse(String url, boolean parsePage) throws Exception {
            try {
                Constants constants = new Constants();
                String apikey = constants.CONFIG_NEWS_API_KEY;
                NewsApiAdapter newsApiAdapter = new NewsApiAdapter(apikey); // Corrected variable name

                RssReader reader = newsApiAdapter.searchArticlesToRssReader(url);
                newsApiAdapter.displayArticles(reader.getArticleList());
                return reader;
            } catch (Exception eNewApi) {
                if (log.isDebugEnabled()) {
                    log.debug("Error using News API adapter: " + url, eNewApi);
                }
                throw eNewApi;
            }
        }
    }
}
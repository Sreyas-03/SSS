package com.sismics.reader.core.service;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.sismics.reader.core.constant.Constants;
import com.sismics.reader.core.util.DirectoryUtil;
import com.sismics.reader.core.util.TransactionUtil;

public class DirectoryIndexingService extends AbstractScheduledService {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(DirectoryIndexingService.class);

    /**
     * Lucene directory.
     */
    private Directory directory;
    
    /**
     * Index reader.
     */
    private DirectoryReader directoryReader;
    
    
    /**
     * Lucene storage config.
     */
    private String luceneStorageConfig;

    public DirectoryIndexingService(String luceneStorageConfig) {
        this.luceneStorageConfig = luceneStorageConfig;
    }
    @Override
    protected void startUp() {
        // RAM directory storage by default
        if (luceneStorageConfig == null || luceneStorageConfig.equals(Constants.LUCENE_DIRECTORY_STORAGE_RAM)) {
            directory = new RAMDirectory();
            log.info("Using RAM Lucene storage");
        } else if (luceneStorageConfig.equals(Constants.LUCENE_DIRECTORY_STORAGE_FILE)) {
            File luceneDirectory = DirectoryUtil.getLuceneDirectory();
            log.info("Using file Lucene storage: {}", luceneDirectory);
            try {
                directory = new SimpleFSDirectory(luceneDirectory, new SimpleFSLockFactory());
            } catch (IOException e) {
                log.error("Error initializing Lucene index", e);
            }
        }
    }

    @Override
    protected void shutDown() {
        if (directoryReader != null) {
            try {
                directoryReader.close();
            } catch (IOException e) {
                log.error("Error closing the index reader", e);
            }
        }
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException e) {
                log.error("Error closing Lucene index", e);
            }
        }
    }
    
    @Override
    protected void runOneIteration() throws Exception {
        TransactionUtil.handle(() -> {
            // NOP
        });
    }
    
    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(0, 1, TimeUnit.HOURS);
    }
    
    /**
     * Getter of directory.
     *
     * @return the directory
     */
    public Directory getDirectory() {
        return directory;
    }
    
    /**
     * Returns a valid directory reader.
     * Take care of reopening the reader if the index has changed
     * and closing the previous one.
     *
     * @return the directoryReader
     */
    public DirectoryReader getDirectoryReader() {
        if (directoryReader == null) {
            if (!DirectoryReader.indexExists(directory)) {
                log.info("Lucene directory not yet created");
                return null;
            }
            try {
                directoryReader = DirectoryReader.open(directory);
            } catch (IOException e) {
                log.error("Error creating the directory reader", e);
            }
        } else {
            try {
                DirectoryReader newReader = DirectoryReader.openIfChanged(directoryReader);
                if (newReader != null) {
                    directoryReader.close();
                    directoryReader = newReader;
                }
            } catch (IOException e) {
                log.error("Error while reopening the directory reader", e);
            }
        }
        return directoryReader;
    }
}

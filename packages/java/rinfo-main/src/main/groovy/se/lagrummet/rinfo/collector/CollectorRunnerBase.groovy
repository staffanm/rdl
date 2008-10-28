package se.lagrummet.rinfo.collector;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.ConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class CollectorRunnerBase {

    private final Logger logger = LoggerFactory.getLogger(CollectorRunnerBase.class);

    public static final int DEFAULT_INITIAL_DELAY = 0;
    public static final int DEFAULT_SCHEDULE_INTERVAL = 600;
    public static final String DEFAULT_TIME_UNIT_NAME = "SECONDS";

    private int initialDelay = DEFAULT_INITIAL_DELAY;
    private int scheduleInterval = DEFAULT_SCHEDULE_INTERVAL;
    private String timeUnitName;

    private TimeUnit timeUnit;
    private Semaphore semaphore = new Semaphore(1);

    private ScheduledExecutorService scheduleService;

    public int getInitialDelay() {
        return initialDelay;
    }
    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getScheduleInterval() {
        return scheduleInterval;
    }
    public void setScheduleInterval(int scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }

    public String getTimeUnitName() {
        return timeUnit.toString();
    }
    public void setTimeUnitName(String timeUnitName) {
        this.timeUnit = TimeUnit.valueOf(timeUnitName);
    }

    public abstract Collection getSourceFeedUrls();

    public void startup() {
        if (scheduleInterval == -1) {
            logger.info("Disabled scheduled collects.");
        } else {
            // FIXME: error handling (currently silently dies on bad feeds)
            scheduleService = Executors.newSingleThreadScheduledExecutor();
            scheduleService.scheduleAtFixedRate(
                { collectAllFeeds() }, initialDelay, scheduleInterval, timeUnit);
            String unitName = timeUnit.toString().toLowerCase()
            logger.info("Scheduled collect every "+scheduleInterval +
                    " "+unitName+" (starting in "+initialDelay+" "+unitName+").");
        }
    }

    public void shutdown() {
        if (scheduleService != null) {
            scheduleService.shutdown();
        }
    }

    public boolean triggerFeedCollect(URL feedUrl) {
        if (!getSourceFeedUrls().contains(feedUrl.toString())) {
            // FIXME: throw NotAllowedSourceFeedException?
            logger.warn("Warning - triggerFeedCollect called with disallowed " +
                    "feed url: <"+feedUrl+">");
            return false;
        }
        if (!semaphore.tryAcquire()) {
            logger.info("Busy - skipping collect of <"+feedUrl+">");
            return false;
        }
        try {
            Executor executor = Executors.newSingleThreadExecutor();
            executor.execute({ collectFeed(feedUrl) });
            executor.shutdown();
            return true;
        } finally {
            semaphore.release();
        }
    }

    public boolean collectAllFeeds() {
        if (getSourceFeedUrls() == null) {
            return true;
        }
        if (!semaphore.tryAcquire()) {
            logger.info("Busy - skipping collect of all source feeds.");
            return false;
        }
        try {
            logger.info("Starting to collect " + getSourceFeedUrls().size() +
                    " source feeds.");
            for (String feedUrl : getSourceFeedUrls()) {
                collectFeed(new URL(feedUrl));
            }
            logger.info("Done collecting source feeds.");
            return true;
        } finally {
            semaphore.release();
        }
    }

    // TODO:IMPROVE: current semaphore simultaneous collect prevention? E.g.:
    //  - synchronized triggerFeedCollect or scheduled collectAllFeeds somehow?
    //  - or safety in FeedCollector instead?
    //  - or pop from synchronized queue?
    protected abstract void collectFeed(URL feedUrl);

}

package se.lagrummet.rinfo.collector.atom;

import java.io.*;
import java.util.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.activation.MimeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.client.HttpClient;

import org.apache.abdera.Abdera;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.AtomDate;
import org.apache.abdera.model.Content;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;

import org.apache.abdera.ext.history.FeedPagingHelper;
import se.lagrummet.rinfo.collector.ParseFeedException;


/**
 * A FeedArchiveReader guaranteed to track backwards in time through feed pages,
 * examining each entry in youngest to oldest order per page by calling
 * {@link stopOnEntry}. When completed, it will re-read all visited pages
 * in turn and process them in chronological order from oldest (known) to
 * youngest.
 *
 * <em>Warning!</em> Instances of this class are not thread safe.
 */
public abstract class FeedArchivePastToPresentReader extends FeedArchiveReader {

    private final Logger logger = LoggerFactory.getLogger(
            FeedArchivePastToPresentReader.class);

    protected LinkedList<FeedReference> feedTrail;
    protected Map<IRI, AtomDate> entryModificationMap;
    protected Entry knownStoppingEntry;

    protected FeedReference feedOfFeeds;

    public FeedArchivePastToPresentReader() {
        super();
    }

    public FeedArchivePastToPresentReader(HttpClient httpClient) {
        super(httpClient);
    }

    @Override
    public void beforeTraversal() {
        feedTrail = new LinkedList<FeedReference>();
        entryModificationMap = new HashMap<IRI, AtomDate>();
        knownStoppingEntry = null;
    }

    @Override
    public void afterTraversal() throws URISyntaxException, IOException, ParseFeedException {
        IRI fofId = null;
        Map<IRI, AtomDate> fofMap = null;
        if (feedOfFeeds != null) {
            try {
                Feed rootFeed = feedOfFeeds.openFeed();
                fofId = rootFeed.getId();
                fofMap = getFeedEntryDataIndex().getEntryDataForCompleteFeedId(fofId);
                if (fofMap == null) {
                    fofMap = new LinkedHashMap<IRI, AtomDate>();
                }
                processFeedOfFeeds(rootFeed);
            } catch (RuntimeException mapperParsingException) {
                if (logger!=null)
                    logger.error("Feed Error! Failed to open feed!",mapperParsingException);
                mapperParsingException.fillInStackTrace();
                throw mapperParsingException;
            } finally {
                feedOfFeeds.close();
            }
        }
        for (FeedReference feedRef : feedTrail) {
            try {
                Feed feed = feedRef.openFeed();
                feed = feed.sortEntriesByUpdated(/*new_first=*/false);

                // IMPROVE: must not have paged feed links! Fail if so.
                //
                // Also fail if feed is already known but only now was
                // marked as complete.. (Otherwise collector must construct
                // complete source+entry index).
                //
                // .. not at all necessary to use this pastToPresent
                // two-pass logic on complete feeds - there will be only
                // one feedRef in feedTrail!
                // Could possibly also use the complete state to simplify
                // the stopOnEntry mechanism? Remember, we need all to make
                // a complete diff. Another reason to separate "archive
                // reading" from "complete reading"...
                boolean completeFeed = FeedPagingHelper.isComplete(feed);

                Map<IRI, AtomDate> deletedMap = (completeFeed)?
                        computeDeletedFromComplete(feed) : getDeletedMarkers(feed);

                List<Entry> effectiveEntries = new ArrayList<Entry>();
                for (Entry entry: feed.getEntries()) {
                    IRI entryId = entry.getId();
                    Date entryUpdated = entry.getUpdated();
                    if (deletedMap.containsKey(entryId)) {
                        if (isYoungerThan(deletedMap.get(entryId).getDate(), entryUpdated)) {
                            // IMPROVE: only if deleted is youngest, not same-age?
                            // Also, ignore deleted as now, or do delete and re-add?
                            continue;
                        } else {
                            deletedMap.remove(entryId);
                        }
                    }
                    AtomDate youngestAtomDate = entryModificationMap.get(
                            entryId);
                    boolean notSeenOrYoungestOfSeen =
                            youngestAtomDate == null ||
                            youngestAtomDate.getDate().equals(entryUpdated);
                    if (notSeenOrYoungestOfSeen) {
                        boolean knownOrOlderThanKnown =
                            knownStoppingEntry != null && ((
                                entryId.equals(
                                    knownStoppingEntry.getId()) &&
                                        entryUpdated.equals(knownStoppingEntry.getUpdated())) || isOlderThan(entryUpdated,
                                    knownStoppingEntry.getUpdated()));
                        if (knownOrOlderThanKnown) {
                            continue;
                        }
                        effectiveEntries.add(entry);
                    }
                }

                // IMPROVE: not necessary if incremental logging is used. See
                // also the IMPROVE after processFeedPageInOrder call.
                if (completeFeed) {
                    storeIntermediateFeedEntryDataIndex(feed);
                }

                boolean ok = processFeedPageInOrder(feedRef.getFeedUrl(), feed,
                        effectiveEntries, deletedMap);

                // IMPROVE: don't do this here? Should impl take care of doing this
                // in a granular, storage-specific way? Like:
                // - make sure or assume that all in new feed older than
                //   knownStoppingEntry are stored,
                // - remove all deleted and
                // - add new to entry index for feed..
                if (completeFeed) {
                    storeNewFeedEntryDataIndex(feed);
                }

                if (ok && fofId != null) {
                    fofMap.put(feed.getId(), feed.getUpdatedElement().getValue());
                    getFeedEntryDataIndex().storeEntryDataForCompleteFeedId(fofId, fofMap);
                }
            } catch (RuntimeException mapperParsingException) {
                if (logger!=null)
                    logger.error("Feed Error! Failed to read feed!",mapperParsingException);
                mapperParsingException.fillInStackTrace();
                throw mapperParsingException;
            } finally {
                feedRef.close();
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        // NOTE: cleanup trail if an exception occurred in afterTraversal.
        for (FeedReference feedRef : feedTrail) {
            try {
                feedRef.close();
            } catch (IOException e) {
                logger.error("Could not close " + feedRef, e);
            }
        }
    }

    @Override
    public URL readFeedPage(URL url) throws IOException, ParseFeedException {
        if (hasVisitedArchivePage(url)) {
            logger.info("Stopping on visited archive page: <"+url+">");
            return null;
        } else {
            return super.readFeedPage(url);
        }
    }

    protected boolean isFeedOfFeeds(Feed feed) {
        for (Entry entry : feed.getEntries()) {
            return isFeedContent(entry.getContentElement());
        }
        return false;
    }

    protected boolean isFeedContent(Content content) {
        if (content == null || content.getSrc() == null) {
            return false;
        }
        MimeType mt = content.getMimeType();
        return mt != null &&
            mt.getBaseType().equals("application/atom+xml") &&
            mt.getParameter("type").equals("feed");
    }

    protected void processFeedOfFeeds(Feed feed) throws IOException, ParseFeedException {
        Map<IRI, AtomDate> fofMap = getFeedEntryDataIndex().
                getEntryDataForCompleteFeedId(feed.getId());
        feed = feed.sortEntriesByUpdated(/*new_first=*/true);
        for (Entry entry : feed.getEntries()) {
            Content content = entry.getContentElement();
            if (!isFeedContent(content)) {
                continue;
            }
            processSubFeedEntry(fofMap, entry);
        }
    }

    private void processSubFeedEntry(Map<IRI, AtomDate> fofMap, Entry entry)
            throws IOException, ParseFeedException {
        AtomDate updated = entry.getUpdatedElement().getValue();
        AtomDate lastUpdated = (fofMap != null)?
                fofMap.get(entry.getId()) : null;
        if (updated != null && lastUpdated != null) {
            if (!isYoungerThan(updated.getDate(), lastUpdated.getDate())) {
                return;
            }
        }
        URL subFeedUrl = null;
        try {
            subFeedUrl = entry.getContentElement().getResolvedSrc().toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        readFeedPage(subFeedUrl);
    }

    @Override
    public boolean processFeedPage(URL pageUrl, Feed feed) throws Exception {
        // IMPROVE:?
        //if (!pageUrl.equals(subscriptionUrl)) {
        //    assert FeedPagingHelper.isArchive(feed);
        //}

        if (feedOfFeeds == null && feedTrail.size() == 0) {
            if (isFeedOfFeeds(feed)) {
                feedOfFeeds = new FeedReference(pageUrl, feed);
                return false;
            }
        }

        feedTrail.addFirst(new FeedReference(pageUrl, feed));
        feed = feed.sortEntriesByUpdated(/*new_first=*/true);

        Map<IRI, AtomDate> deletedMap = getDeletedMarkers(feed);
        for (Map.Entry<IRI, AtomDate> item : deletedMap.entrySet()) {
            putUriDateIfNewOrYoungest(entryModificationMap, item.getKey(), item.getValue());
        }

        // TODO:? needs to scan the rest with the same updated stamp before
        // stopping (even if this means following more pages back in time?)?
        // IMPROVE: It would thus also be wise to mark/remove entries in feedTrail
        // which have been visited (so the subclass don't have to check this
        // twice).
        for (Entry entry : feed.getEntries()) {
            if (stopOnEntry(entry)) {
                logger.info("Stopping on known entry: <" +entry.getId() +
                        "> ["+entry.getUpdatedElement().getString()+"]");
                knownStoppingEntry = entry;
                return false;
            }
            if (!putUriDateIfNewOrYoungest(entryModificationMap,
                    entry.getId(),
                    entry.getUpdatedElement().getValue())) {
                logger.info("Skipping older version of entry {}", entry.getId());
            }
        }

        return true;
    }

    /**
     * Default method used to get tombstone markers from a feed.
     * @return A map of entry id:s and deletion times. The default uses {@link
     *         AtomEntryDeleteUtil.getDeletedMarkers}.
     */
    public Map<IRI, AtomDate> getDeletedMarkers(Feed feed)
            throws URISyntaxException {
        return AtomEntryDeleteUtil.getDeletedMarkers(feed);
    }

    /**
     * Template method intended for the actual feed processing.
     * This method is guaranteed to be called in sequence from oldest page
     * to newest, with a feed entries sorted in chronological order <em>from
     * oldest to newest</em>. Note that the feed will contain <em>all</em>
     * entries, even the ones older than any known processed entry.
     *
     * @param pageUrl The URL of the feed page.
     * @param feed The feed itself (with entries sorted in chronological order).
     *
     * @param effectiveEntries Entries in the current feed, filtered so that:
     * <ul>
     *   <li>No younger entries exist in the range of collected feed pages.</li>
     *   <li>The entry has no tombstone in the current feed.</li>
     * </ul>
     *
     * @param deletedMap A map of tombstones (given in one of the forms
     *        supported by {@link getDeletedMarkers}).
     * @return whether all items were properly processed or not.
     */
    public abstract boolean processFeedPageInOrder(URL pageUrl, Feed feed,
            List<Entry> effectiveEntries, Map<IRI, AtomDate> deletedMap);

    /**
     * Template method to stop on known feed entry.
     * @return whether to continue climbing backwards in time collecting feed
     *         pages to process.
     */
    public abstract boolean stopOnEntry(Entry entry);

    /**
     * Default method to stop on known visited feed archive pages.
     * @return whether to read the page or not. Default always returns false.
     */
    public boolean hasVisitedArchivePage(URL pageUrl) {
        return false;
    }

    /**
     * Optional getter for a {@link FeedEntryDataIndex}, used if an
     * encountered feed is marked as <em>complete</em>. See that interface for
     * details.
     * @throws UnsupportedOperationException by default.
     */
    public FeedEntryDataIndex getFeedEntryDataIndex() {
        throw new UnsupportedOperationException("No support for complete feed indexing.");
    }

    Map<IRI, AtomDate> computeDeletedFromComplete(Feed feed) {
        Map<IRI, AtomDate> collectedEntryData = getFeedEntryDataIndex().
                getEntryDataForCompleteFeedId(feed.getId());
        Set<IRI> deletedIris = new HashSet<IRI>();
        if (collectedEntryData == null) {
            return Collections.emptyMap();
        }
        deletedIris.addAll(collectedEntryData.keySet());
        for (Entry entry: feed.getEntries()) {
            deletedIris.remove(entry.getId());
        }
        Map<IRI, AtomDate> deletedMap = new HashMap<IRI, AtomDate>();
        for (IRI deletedIri : deletedIris) {
            deletedMap.put(deletedIri, feed.getUpdatedElement().getValue());
        }
        return deletedMap;
    }

    /**
     * This is necessary to guarantee knowledge of "possibly stored posts", to
     * be able to always determine which things should be deleted on future
     * collects. This is done by storing a union of existing and new entries.
     * This list will contain entries not yet stored, but also not yet deleted,
     * which guarantees that a new diff will not miss anything to be deleted.
     */
    void storeIntermediateFeedEntryDataIndex(Feed feed) {
        Map<IRI, AtomDate> allEntryData = getFeedEntryDataIndex().
                getEntryDataForCompleteFeedId(feed.getId());
        if (allEntryData == null) {
            allEntryData = new HashMap<IRI, AtomDate>();
        }
        for (Entry entry: feed.getEntries()) {
            allEntryData.put(entry.getId(), entry.getUpdatedElement().getValue());
        }
        getFeedEntryDataIndex().storeEntryDataForCompleteFeedId(
                feed.getId(), allEntryData);
    }

    void storeNewFeedEntryDataIndex(Feed feed) throws IOException {
        Map<IRI, AtomDate> entryMap = new HashMap<IRI, AtomDate>();
        for (Entry entry: feed.getEntries()) {
            entryMap.put(entry.getId(), entry.getUpdatedElement().getValue());
        }
        getFeedEntryDataIndex().storeEntryDataForCompleteFeedId(
                feed.getId(), entryMap);
    }

    public static boolean isYoungerThan(Date date, Date thanDate) {
        return date.compareTo(thanDate) > 0;
    }

    public static boolean isOlderThan(Date date, Date thanDate) {
        return date.compareTo(thanDate) < 0;
    }

    static boolean putUriDateIfNewOrYoungest(Map<IRI, AtomDate> map,
            IRI iri, AtomDate atomDate) {
        AtomDate storedAtomDate = map.get(iri);
        if (storedAtomDate != null) {
            Date date = atomDate.getDate();
            Date storedDate = storedAtomDate.getDate();
            // keep largest date => ignore all older (smaller)
            if (isOlderThan(atomDate.getDate(), storedAtomDate.getDate())) {
                return false;
            }
        }
        map.put(iri, atomDate);
        return true;
    }


    public static class FeedReference {

        private URL feedUrl;
        private URI tempFileUri;
        private InputStream tempInStream;

        public FeedReference(URL feedUrl, Feed feed)
                throws IOException, FileNotFoundException {
            this.feedUrl = feedUrl;
            File tempFile = File.createTempFile("feed", ".atom");
            tempFileUri = tempFile.toURI();
            OutputStream outStream = new FileOutputStream(tempFile);
            try {
                Abdera.getInstance().getWriter().writeTo(feed, outStream);
            } finally {
                outStream.close();
            }
        }

        public URL getFeedUrl() {
            return feedUrl;
        }

        public Feed openFeed() throws IOException, FileNotFoundException, ParseFeedException {
            tempInStream = new FileInputStream(getTempFile());
            Feed feed = parseFeed(tempInStream, feedUrl);
            return feed;
        }

        public void close() throws IOException {
            if (tempInStream != null) {
              tempInStream.close();
              tempInStream = null;
            }
            File tempFile = getTempFile();
            if (tempFile.exists()) {
              tempFile.delete();
            }
        }

        public String toString() {
            return "FeedReference(feedUrl="+this.feedUrl +
                    ", tempFileUri="+this.tempFileUri+")";
        }

        private File getTempFile() {
            return new File(tempFileUri);
        }

    }

}

package se.lagrummet.rinfo.main.storage


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.openrdf.repository.Repository

import org.apache.abdera.Abdera
import org.apache.abdera.model.Entry
import org.apache.abdera.model.Feed
import org.apache.abdera.model.Link
import org.apache.abdera.i18n.iri.IRI

import se.lagrummet.rinfo.store.depot.Depot
import se.lagrummet.rinfo.store.depot.DepotSession
import se.lagrummet.rinfo.store.depot.DepotEntry
import se.lagrummet.rinfo.store.depot.SourceContent
import se.lagrummet.rinfo.store.depot.DuplicateDepotEntryException

import se.lagrummet.rinfo.collector.atom.FeedEntryDataIndex


class StorageSession {

    private final Logger logger = LoggerFactory.getLogger(StorageSession)

    public static final String VIA_META_RESOURCE = "collector-via.entry"

    StorageCredentials credentials
    Collection<StorageHandler> storageHandlers =
            new ArrayList<StorageHandler>()
    DepotSession depotSession
    CollectorLogSession logSession
    FeedEntryDataIndex feedEntryDataIndex
    boolean isCheckerCollect

    StorageSession(StorageCredentials credentials,
            DepotSession depotSession,
            Collection<StorageHandler> storageHandlers,
            CollectorLogSession logSession,
            FeedEntryDataIndex feedEntryDataIndex,
            boolean isCheckerCollect = false) {
        this.credentials = credentials
        this.storageHandlers = storageHandlers
        this.depotSession = depotSession
        this.logSession = logSession
        this.feedEntryDataIndex = feedEntryDataIndex
        this.isCheckerCollect = isCheckerCollect
        logSession.start(credentials)
    }

    Depot getDepot() {
        return depotSession.getDepot()
    }

    DepotEntry getEntryOrDeletedEntry(URI entryId) {
        return getDepot().getEntryOrDeletedEntry(entryId)
    }

    void close() {
        try {
            depotSession.close()
        } finally {
            logSession.close()
        }
    }

    void beginPage(URL pageUrl, Feed feed) {
        logSession.logFeedPageVisit(pageUrl, feed)
    }

    void endPage(URL pageUrl) {
    }

    void onPageError(Exception e, URL pageUrl) {
        logSession.logFeedPageError(e, pageUrl)
    }

    boolean hasCollected(Entry sourceEntry) {
        return hasCollected(sourceEntry,
                getEntryOrDeletedEntry(sourceEntry.getId().toURI()))
    }

    boolean hasCollected(Entry sourceEntry, DepotEntry depotEntry) {
        return (depotEntry != null && !depotEntry.isDeleted() &&
                sourceIsNotAnUpdate(sourceEntry, depotEntry))
    }

    boolean storeEntry(Feed sourceFeed, Entry sourceEntry,
            List<SourceContent> contents, List<SourceContent> enclosures) {

        URI entryId = sourceEntry.getId().toURI()
        logger.info("Examining entry <${entryId}>..")
        DepotEntry depotEntry = getEntryOrDeletedEntry(entryId)

        // NOTE: Needed since even if hasCollected is true (via stopOnEntry),
        // there may be several entries with the same timestamp.
        // IMPROVE:? Will this "thrash" on many true:s?
        if (hasCollected(sourceEntry, depotEntry)) {
            logger.info("Encountered collected entry with id=<" +
                    sourceEntry.getId()+">, updated=[" +
                    sourceEntry.getUpdated()+"]. Skipping.")
            return true
        }

        boolean doCreate = (depotEntry == null || depotEntry.isDeleted())
        if (depotEntry != null && depotEntry.isDeleted()) {
            depotEntry.resurrect()
        }

        Date timestamp = new Date()
        try {
            if (doCreate) {
                logger.info("New entry <${entryId}>.")
                depotEntry = depotSession.createEntry(
                        entryId, timestamp, contents, enclosures)
            } else {
                // NOTE: If source has been collected but appears as newly published:
                if (!(sourceEntry.getUpdated() > sourceEntry.getPublished())) {
                    logger.warn("Collected entry <"+sourceEntry.getId() +
                            "> exists as <"+entryId +
                            "> but does not appear as updated. Source:" +
                            sourceEntry)
                    // NOTE: could treat this as an illegal publication practise..
                    //throw new DuplicateDepotEntryException(depotEntry);
                }
                logger.info("Updating entry <${entryId}>.")
                depotSession.update(depotEntry, timestamp, contents, enclosures)
            }
            setViaEntry(depotEntry, sourceFeed, sourceEntry)

            for (StorageHandler handler : storageHandlers) {
                handler.onModified(this, depotEntry, doCreate)
            }

            logSession.logUpdatedEntry(sourceFeed, sourceEntry, depotEntry)

            //check if the feedurl is recollect first to avoid slowing stuff down?
            ReCollectQueue.instance.tryRemove(sourceEntry)

            return true

        } catch (Exception e) {
            /* TODO: explicit handling (and logging) of more errors:
                - retriable:
                    java.net.SocketException
                -  more source errors (report and log):
                    javax.net.ssl.SSLPeerUnverifiedException
                    MissingRdfContentException
                    DuplicateDepotEntryException
            */
            def gotErrorAction =
                    logSession.logError(e, timestamp, sourceFeed, sourceEntry)

            boolean shouldContinue = isCheckerCollect

            switch (gotErrorAction) {
                case ErrorAction.SKIPANDCONTINUE:
                    logger.warn("Skipping entry <${entryId}> but collect continues. Caused by: " + e)
                    depotSession.rollbackPending()
                    shouldContinue = true
                    break
                case ErrorAction.STOREANDCONTINUE:
                    logger.warn("Storing entry <${entryId}> with problem but collect continues. Caused by: " + e)
                    //check if the feedurl is recollect first to avoid slowing stuff down?
                    ReCollectQueue.instance.tryRemove(sourceEntry)
                    shouldContinue = true
                    break
                case ErrorAction.SKIPANDHALT:
                    def shouldContinueAsString = isCheckerCollect ? "continues" : "is stopped"
                    logger.error("Error storing entry <${entryId}> and collect " + shouldContinueAsString + ". Caused by: " + e)
                    depotSession.rollbackPending()
                    break
                case ErrorAction.CONTINUEANDRETRYLATER:
                    logger.info("Failed to download something in ${sourceEntry.id} adding the entry to the recollect queue")
                    ReCollectQueue.instance.add(new FailedEntry(contentEntry: sourceEntry))
                    logger.info("Rolling back current entry ${sourceEntry.id}")
                    depotSession.rollbackPending()
                    shouldContinue = true
                    break
                default:
                    throw new IllegalStateException("Unknown ErrorAction: " + gotErrorAction + ". Caused by: " + e)
            }

            return shouldContinue
        }
    }

    void deleteEntry(Feed sourceFeed, URI entryId, Date sourceDeletedDate) {
        DepotEntry depotEntry = getEntryOrDeletedEntry(entryId)
        // TODO:? could this mean we have an error causing loss of collector metadata?
        if (depotEntry == null) {
            logger.warn("Could not delete entry, missing <${entryId}>.")
            return
        }
        if (depotEntry.isDeleted()) {
            logger.warn("Not deleting already deleted entry <${entryId}>.")
            return
        }
        logger.info("Deleting entry <${entryId}>.")
        // TODO:? saveDeletionMetaInfo? (smart deletion detect; e.g. feed + tombstone)
        // .. or is tombstone in feed enough (for e.g. event index)?
        // TODO: previously used sourceDeletedDate; must surely have been wrong?
        Date deletedDate = new Date()
        depotSession.delete(depotEntry, deletedDate)
        for (StorageHandler handler : storageHandlers) {
            handler.onDelete(this, depotEntry)
        }
        logSession.logDeletedEntry(
                sourceFeed, entryId, sourceDeletedDate, depotEntry)
    }

    static Entry getViaEntry(DepotEntry depotEntry) {
        InputStream viaEntryInStream = depotEntry.getMetaInputStream(VIA_META_RESOURCE)
        if (viaEntryInStream == null)
            return null
        Entry viaEntry = null
        try {
            viaEntry = (Entry) Abdera.getInstance().getParser().parse(
                    viaEntryInStream).getRoot();
        } finally {
            viaEntryInStream.close()
        }
        return viaEntry
    }

    static void setViaEntry(DepotEntry depotEntry,
            Feed sourceFeed, Entry sourceEntry) {
        Entry viaEntry = sourceEntry.clone()
        // TODO:IMPROVE: remove tombstones; except del.id == depotEntry.id if deleted..
        // TODO: fail on missing sourceFeed.id..
        viaEntry.setSource(sourceFeed)
        // TODO:IMPROVE: is this way of setting base URI enough?
        viaEntry.setBaseUri(viaEntry.getSource().getResolvedBaseUri())
        viaEntry.getSource().setBaseUri(null)
        OutputStream viaEntryOutStream = depotEntry.getMetaOutputStream(VIA_META_RESOURCE)
        try {
            viaEntry.writeTo(viaEntryOutStream)
        } finally {
            viaEntryOutStream.close()
        }
    }

    protected static boolean sourceIsNotAnUpdate(Entry sourceEntry,
            DepotEntry depotEntry) {
        Entry viaEntry = getViaEntry(depotEntry)
        // TODO:IMPROVE:?
        // If depotEntry; check stored source and allow update if *both*
        //     sourceEntry.updated>.created (above) *and* > depotEntry.updated..
        //     .. and "source feed" is "same as last"? (indirected via rdf facts)?
        // TODO:? Assert id == id (& feed.id == ..)?
        return !(sourceEntry.getUpdated() > viaEntry?.getUpdated())
    }

}

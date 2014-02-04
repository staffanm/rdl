package se.lagrummet.rinfo.service

import org.slf4j.LoggerFactory

import org.apache.abdera.model.Feed
import org.apache.abdera.model.Entry

import org.openrdf.repository.Repository
import org.openrdf.repository.RepositoryConnection

import org.elasticsearch.index.mapper.MapperParsingException

import org.apache.abdera.model.AtomDate
import org.apache.abdera.model.Entry
import org.apache.abdera.model.Feed
import org.apache.abdera.i18n.iri.IRI

import se.lagrummet.rinfo.collector.atom.FeedArchivePastToPresentReader


class SesameLoader extends FeedArchivePastToPresentReader {

    Repository repository
    RepositoryConnection conn
    ElasticLoader elasticLoader

    private final logger = LoggerFactory.getLogger(SesameLoader)

    SesameLoader(Repository repository, elasticLoader=null) {
        this.repository = repository
        this.elasticLoader = elasticLoader
    }

    @Override
    public void initialize() {
        super.initialize()
        conn = repository.getConnection()
    }

    @Override
    public void shutdown() {
        try {
            super.shutdown()
        } finally {
            conn.close()
        }
    }

    boolean stopOnEntry(Entry entry) {
        // TODO:? store "youngest collected entry + date", stop only on that?
        // (pageUrl, entry and date)
        def repoEntry = new RepoEntry(this, entry)
        return repoEntry.isCollected()
    }

    boolean processFeedPageInOrder(URL pageUrl, Feed feed,
            List<Entry> effectiveEntries, Map<IRI, AtomDate> deletedMap) {
        deleteFromMarkers(feed, deletedMap)
        for (Entry entry : effectiveEntries) {
            create(entry)
        }
        return true
    }

    protected void deleteFromMarkers(Feed sourceFeed, Map<IRI, AtomDate> deletedMap) {
        for (Map.Entry<URI, Date> delItem : deletedMap.entrySet()) {
            delete(delItem.getKey().toURI(), delItem.getValue().getDate())
        }
    }

    protected void create(entry) {
        def repoEntry = new RepoEntry(this, entry)
        // TODO: isn't this an exception, "not supposed to happen"?
        // (FeedArchivePastToPresentReader shouldn't supply known stuff..)
        if (repoEntry.isCollected()) {
            logger.debug("Skipping collected entry <${entry.id}> [${entry.updated}]")
            return
        }

        try {
            logger.info("Storing entry <${entry.id}>")
            repoEntry.create()
            elasticLoader?.create(conn, entry, this)
        } catch (MapperParsingException e) {
            logger.warn("MapperParsingException caught when storing entry <${entry.id}>. Details: " + e.getMessage())
        }
    }

    protected void delete(entryId, deletedTime) {
        def repoEntry = new RepoEntry(this, entryId, deletedTime)
        logger.info("Deleting entry <${repoEntry.id}>")
        repoEntry.delete()
        elasticLoader?.delete(entryId)
    }

}

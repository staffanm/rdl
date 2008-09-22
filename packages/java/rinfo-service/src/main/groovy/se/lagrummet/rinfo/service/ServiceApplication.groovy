package se.lagrummet.rinfo.service

import org.restlet.Application
import org.restlet.Context
import org.restlet.Finder
import org.restlet.Handler
import org.restlet.Restlet
import org.restlet.Router
import org.restlet.data.CharacterSet
import org.restlet.data.MediaType
import org.restlet.data.Method
import org.restlet.data.Request
import org.restlet.data.Response
import org.restlet.data.Status

import org.apache.commons.configuration.AbstractConfiguration
import org.apache.commons.configuration.ConfigurationException
import org.apache.commons.configuration.PropertiesConfiguration

import org.openrdf.repository.Repository
import org.openrdf.repository.http.HTTPRepository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import org.openrdf.sail.nativerdf.NativeStore

import java.util.concurrent.Executors


class ServiceApplication extends Application {

    public static final String CONFIG_PROPERTIES_FILE_NAME = "rinfo-service.properties"
    public static final String RDF_REPO_CONTEXT_KEY = "rinfo.service.rdfrepo"

    private Repository repo

    public ServiceApplication(Context parentContext) {
        super(parentContext)
        configure(new PropertiesConfiguration(CONFIG_PROPERTIES_FILE_NAME))
    }

    protected void configure(AbstractConfiguration config) {
        def repoPath = config.getString("rinfo.service.sesameRepoPath")
        def remoteRepoName = config.getString("rinfo.service.sesameRemoteRepoName")
        if (repoPath =~ /^https?:/) {
            repo = new HTTPRepository(repoPath, remoteRepoName)
        } else {
            def dataDir = new File(repoPath)
            repo = new SailRepository(new NativeStore(dataDir))
        }
        repo.initialize()
        def attrs = getContext().getAttributes()
        attrs.putIfAbsent(RDF_REPO_CONTEXT_KEY, repo)
    }

    @Override
    public synchronized Restlet createRoot() {
        def router = new Router(getContext())
        router.attachDefault(new Finder(getContext(), RDFLoaderHandler))
        return router
    }

    @Override
    public void stop() {
        super.stop()
        repo.shutDown()
    }

}


class RDFLoaderHandler extends Handler {

    @Override
    public boolean allowPost() { return true; }

    @Override
    public void handlePost() {
        String feedUrl = request.getEntityAsForm().getFirstValue("feed")
        if (feedUrl == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST,
                    "Missing feed parameter.")
            return
        }
        triggerFeedCollect(new URL(feedUrl))
        response.setEntity("Scheduled collect of <${feedUrl}>.", MediaType.TEXT_PLAIN)
    }

    boolean triggerFeedCollect(URL feedUrl) {
        // TODO: verify source of request and/or feedUrl
        // FIXME: error handling.. (report and/or (public) log)

        def repo = (Repository) getContext().getAttributes().get(
                ServiceApplication.RDF_REPO_CONTEXT_KEY)
        // TODO:IMPROVE: Ok to make a new instance for each request? Shouldn't
        // be so expensive, and isolates it (shouldn't be app-global..)
        def rdfStoreLoader = new SesameLoader(repo)
        def executor = Executors.newSingleThreadExecutor()
        executor.execute({
                rdfStoreLoader.readFeed(feedUrl)
            })
        executor.shutdown()
        return true
    }

}

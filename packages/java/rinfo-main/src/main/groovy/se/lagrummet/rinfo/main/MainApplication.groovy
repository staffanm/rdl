package se.lagrummet.rinfo.main

import org.apache.commons.configuration.DefaultConfigurationBuilder

import org.restlet.Application
import org.restlet.Context
import org.restlet.Restlet
import org.restlet.resource.Finder
import org.restlet.routing.Router
import org.restlet.routing.Variable

import static org.restlet.routing.Template.MODE_EQUALS
import static org.restlet.routing.Template.MODE_STARTS_WITH

import se.lagrummet.rinfo.store.supply.DepotFinder

import static se.lagrummet.rinfo.base.TransformerUtil.newTemplates
import se.lagrummet.rinfo.base.rdf.GritTransformer


class MainApplication extends Application {

    public static final String CONFIG_FILE_NAME = "config.xml"

    Components components

    MainApplication(Context context) {
        super(context)
        def configBuilder = new DefaultConfigurationBuilder(CONFIG_FILE_NAME)
        components = new Components(configBuilder.getConfiguration())
        ContextAccess.setCollectScheduler(getContext(), components.getCollectScheduler())
        ContextAccess.setCollectorLog(getContext(), components.getCollectorLog())
    }

    @Override
    synchronized Restlet createRoot() {
        def router = new Router(getContext())
        router.attach("/collector",
                new Finder(getContext(), CollectorHandler)).setMatchingMode(MODE_EQUALS)
        router.attach("/system/log/",
                new Finder(getContext(), LogListResource)).setMatchingMode(MODE_EQUALS)

        ContextAccess.setLogToXhtml(getContext(), new GritTransformer(
                    newTemplates(getClass(), "/xslt/main_collector_log.xslt")));
        def tplt = router.attach("/system/log/collect/{contextPath}",
                new Finder(getContext(), CollectResource)).getTemplate()
        tplt.getVariables().put("contextPath", new Variable(Variable.TYPE_URI_PATH))
        tplt.setMatchingMode(MODE_STARTS_WITH)

        router.attachDefault(
                new DepotFinder(getContext(), components.getStorage().getDepot()))
        return router
    }

    @Override
    public void start() {
        super.start()
        components.startup()
    }

    @Override
    public void stop() {
        super.stop()
        components.shutdown()
    }

}

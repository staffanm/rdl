package se.lagrummet.rinfo.main

import org.apache.commons.configuration.Configuration
import org.apache.commons.configuration.ConfigurationException
import org.apache.commons.configuration.ConfigurationBuilder
import org.apache.commons.configuration.DefaultConfigurationBuilder

import org.restlet.Application
import org.restlet.Context
import org.restlet.Restlet
import org.restlet.data.Method
import org.restlet.data.Request
import org.restlet.data.Response
import org.restlet./*resource.*/Finder
import org.restlet./*routing.*/Router

import se.lagrummet.rinfo.store.supply.DepotFinder


class MainApplication extends Application {

    public static final String CONFIG_FILE_NAME = "config.xml"
    public static final String COLLECTOR_RUNNER_CONTEXT_KEY =
            "rinfo.main.collector.restlet.context"

    Components components

    MainApplication(Context context) {
        super(context)
        def configBuilder = new DefaultConfigurationBuilder(CONFIG_FILE_NAME)
        components = new Components(configBuilder.getConfiguration())
        getContext().getAttributes().putIfAbsent(
                COLLECTOR_RUNNER_CONTEXT_KEY, components.getCollectScheduler())
    }

    @Override
    synchronized Restlet createRoot() {
        def router = new Router(getContext())
        router.attach("/collector",
                new Finder(getContext(), CollectorHandler))
        router.attachDefault(
                new DepotFinder(getContext(), components.getStorage().getDepot()))
        return router
    }

    @Override
    public void start() {
        super.start()
        components.storage.startup()
        components.collectScheduler.startup()
    }

    @Override
    public void stop() {
        super.stop()
        try {
            collectScheduler.shutdown()
        } finally {
            storage.shutdown()
        }
    }

}

package se.lagrummet.rinfo.service

import org.restlet.Context
import org.restlet.data.CharacterSet
import org.restlet.data.Language
import org.restlet.data.MediaType
import org.restlet.data.Status
import org.restlet.Request
import org.restlet.Response
import org.restlet.representation.StringRepresentation
import org.restlet.representation.Representation
import org.restlet.resource.Finder
import org.restlet.resource.Get
import org.restlet.resource.ServerResource
import org.restlet.routing.Router

import org.openrdf.repository.Repository
import static org.openrdf.query.QueryLanguage.SPARQL
import org.openrdf.repository.util.RDFInserter

import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.map.SerializationConfig

import se.lagrummet.rinfo.base.rdf.RDFUtil
import se.lagrummet.rinfo.base.rdf.jsonld.JSONLDSerializer


class DataFinder extends Finder {

    String baseUri
    Repository repo
    Map contextData
    String contextUrl

    DataFinder(Context context, Repository repo, String baseUri) {
        super(context)
        this.baseUri = baseUri
        this.repo = repo
        def mapper = new ObjectMapper()
        // TODO: configurable context and contextUrl
        this.contextUrl = "/json-ld/context.json"
        def inStream = getClass().getResourceAsStream(contextUrl)
        try {
            this.contextData = mapper.readValue(inStream, Map)
        } finally {
            inStream.close()
        }
    }

    @Override
    ServerResource find(Request request, Response response) {
        final requestPath = request.attributes["path"]

        return new ServerResource() {

            @Get("n3|ttl|txt")
            Representation asN3() {
                //return getRepr(MediaType.TEXT_RDF_N3)
                return getRepr(MediaType.TEXT_PLAIN, "application/x-turtle")
            }

            @Get("rdf|xml")
            Representation asRdfXml() {
                //return getRepr(requestPath, MediaType.APPLICATION_RDF_XML)
                return getRepr(MediaType.TEXT_XML,
                        MediaType.APPLICATION_RDF_XML.toString())
            }

            @Get("json")
            Representation asJSON() {
                return getRepr(MediaType.APPLICATION_JSON)
            }

            def getRepr(mediaType) {
                return getRepr(mediaType, mediaType.toString())
            }

            def getRepr(mediaType, mediaTypeStr) {
                def resourceUri = baseUri + requestPath
                def repo = getRichRDF(resourceUri)
                if (repo == null) {
                    setStatus(Status.CLIENT_ERROR_NOT_FOUND)
                    return null
                }
                def rdfRepr = serializeRDF(repo, resourceUri, mediaTypeStr)
                return new StringRepresentation(rdfRepr, mediaType,
                        null, new CharacterSet("utf-8"))
            }

        }

    }

    /**
     * Create an RDF representation based on the data for the given resource,
     * with added contextual data for relevant incoming and outgoing relations.
     */
    Repository getRichRDF(String resourceUri) {
        def itemRepo = RDFUtil.createMemoryRepository()
        def itemConn = itemRepo.getConnection()
        boolean empty = true
        try {
            def conn = repo.getConnection()
            try {
                def graphQuery = conn.prepareGraphQuery(SPARQL,
                        constructRelRevDataSparql)
                graphQuery.setBinding("current",
                        conn.valueFactory.createURI(resourceUri))
                graphQuery.evaluate(new RDFInserter(itemConn))
            } finally {
                conn.close()
            }
            empty = itemConn.size() == 0 // itemConn.isEmpty()
        } finally {
            itemConn.close()
        }

        return (empty)? null: itemRepo
    }

    String serializeRDF(Repository itemRepo, String resourceUri, String mediaType) {
        if (mediaType == "application/json") {
            def json = new JSONLDSerializer(contextData).toJSON(itemRepo, resourceUri)
            if (json != null) {
                //json["@context"] = contextMap
                json["@context"] = contextUrl
            }
            def jsonMapper = new ObjectMapper()
            jsonMapper.configure(
                    SerializationConfig.Feature.INDENT_OUTPUT, true)
            return jsonMapper.writeValueAsString(json)
        }
        def outStream = new ByteArrayOutputStream()
        try {
            RDFUtil.serialize(itemRepo, mediaType, outStream)
            return outStream.toString()
        } finally {
            outStream.close()
        }
    }

    String getConstructRelRevDataSparql() {
        return getClass().getResourceAsStream(
                "/construct_relrev_data.rq").getText("utf-8")
    }

}

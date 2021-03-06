package se.lagrummet.rinfo.base.rdf;

import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.DatatypeConfigurationException;

import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Literal;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.util.RDFInserter;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParserFactory;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.RDFWriterFactory;
import org.openrdf.rio.RDFWriterRegistry;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;
import org.openrdf.sail.memory.MemoryStore;

import rdfa.adapter.sesame.RDFaParserFactory;


public class RDFUtil {

    private static final Logger logger = LoggerFactory.getLogger(RDFUtil.class);

    public static final String TURTLE = "application/x-turtle";
    public static final String RDF_XML = "application/rdf+xml";

    static final RDFParserFactory RDFA_PARSER_FACTORY;
    static {
        RDFA_PARSER_FACTORY = new RDFaParserFactory();
        RDFParserRegistry.getInstance().add(RDFA_PARSER_FACTORY);
    }


    // Repo-level operations

    public static Repository createMemoryRepository() throws RepositoryException {
        Repository r = new SailRepository(new MemoryStore());
        r.initialize();
        return r;
    }

    public static void loadDataFromURL(
            Repository repo, URL url)
        throws IOException, RDFParseException, RepositoryException
    {
        loadDataFromURL(repo, url, null);
    }

    public static void loadDataFromURL(Repository repo, URL url, String mediaType)
            throws IOException, RDFParseException, RepositoryException {
        URLConnection conn = url.openConnection();
        if (mediaType != null) {
            conn.setRequestProperty("Accept", mediaType);
        }
        conn.connect();
        InputStream stream = conn.getInputStream();
        try {
            loadDataFromStream(repo, stream, url.toString(), mediaType);
        } finally {
            stream.close();
        }
    }

    public static void loadDataFromFile(Repository repo, File file)
            throws IOException, FileNotFoundException,
                   RDFParseException, RepositoryException
    {
        loadDataFromFile(repo, file, null);
    }

    public static void loadDataFromFile(
            Repository repo, File file, String mediaType)
            throws IOException, FileNotFoundException,
                   RDFParseException, RepositoryException
    {
        loadDataFromFile(repo, file, file.toURI().toString(), mediaType);
    }

    public static void loadDataFromFile(
            Repository repo, File file, String baseUri, String mediaType)
            throws IOException, FileNotFoundException,
                   RDFParseException, RepositoryException
    {
        if (mediaType == null) {
            mediaType = RDFFormat.forFileName(
                    file.getName()).getDefaultMIMEType();
        }
        loadDataFromStream(repo,
                new FileInputStream(file), baseUri,
                mediaType);
    }

    public static void loadDataFromStream(Repository repo,
            InputStream stream, String baseUri, String mediaType,
            Resource... contexts)
            throws IOException, RDFParseException, RepositoryException {
        RepositoryConnection conn = repo.getConnection();
        try {
            loadDataFromStream(conn, stream, baseUri, mediaType, contexts);
        } finally {
            conn.close();
        }
    }

    public static void loadDataFromStream(RepositoryConnection conn,
            InputStream stream, String baseUri, String mediaType,
            Resource... contexts)
            throws IOException, RDFParseException, RepositoryException {
        RDFFormat format = RDFFormat.forMIMEType(mediaType);
        if (format == null &&
                (mediaType.equals("application/xhtml+xml") ||
                 mediaType.equals("text/html"))) {
            format = RDFA_PARSER_FACTORY.getRDFFormat();
        }
        try {
            conn.setAutoCommit(false);
            conn.add(stream, baseUri, format, contexts);
            conn.commit();
        } catch (RepositoryException e) {
            conn.rollback();
        }
    }

    public static void addToRepo(Repository targetRepo, Repository repoToAdd)
            throws OpenRDFException {
        addToRepo(targetRepo, repoToAdd, false);
    }

    public static void addToRepo(Repository targetRepo, Repository repoToAdd,
            boolean preserveBNodeIDs)
            throws OpenRDFException {
        RepositoryConnection targetConn = targetRepo.getConnection();
        RepositoryConnection connToAdd = repoToAdd.getConnection();
        try {
            RDFInserter inserter =  new RDFInserter(targetConn);
            inserter.setPreserveBNodeIDs(preserveBNodeIDs);
            connToAdd.export(inserter);
        } finally {
            targetConn.close();
            connToAdd.close();
        }
    }

    public static void addFile(Repository repo, String fpath, RDFFormat format)
            throws IOException, RDFParseException, RepositoryException {
        File file = new File(fpath);
        String baseUri = file.toURI().toString();
        RepositoryConnection conn = repo.getConnection();
        try {
            conn.add(file, baseUri, format);
            conn.commit();
        } finally {
            conn.close();
        }
    }

    public static void serialize(Repository repo, String mediaType,
            OutputStream outStream, Resource... contexts)
            throws RDFHandlerException, RepositoryException, IOException {
        serialize(repo, mediaType, outStream, false, contexts);
    }

    public static void serialize(Repository repo, String mediaType,
            OutputStream outStream, boolean pretty, Resource... contexts)
            throws RDFHandlerException, RepositoryException, IOException {
        RepositoryConnection conn = repo.getConnection();
        try {
            serialize(conn, mediaType, outStream, pretty, contexts);
        } finally {
            conn.close();
        }
    }

    public static void serialize(RepositoryConnection conn, String mediaType,
            OutputStream outStream, boolean pretty, Resource... contexts)
            throws RDFHandlerException, RepositoryException, IOException {
        RDFFormat format = RDFFormat.forMIMEType(mediaType);
        RDFWriter writer = null;
        if (pretty && format.equals(RDFFormat.RDFXML)) {
            writer = new RDFXMLPrettyWriter(outStream);
        } else {
            RDFWriterFactory factory = (RDFWriterFactory) RDFWriterRegistry
                    .getInstance().get(format);
            writer = factory.getWriter(outStream);
        }
        try {
            conn.export(writer, contexts);
        } finally {
            if (writer instanceof Closeable) {
                ((Closeable)writer).close();
            }
        }
    }

    public static InputStream toInputStream(Repository repo, String mediaType,
            Resource... contexts)
            throws IOException, RepositoryException, RDFHandlerException {
        return toInputStream(repo, mediaType, false);
    }

    public static InputStream toInputStream(Repository repo, String mediaType,
            boolean pretty, Resource... contexts)
            throws IOException, RepositoryException, RDFHandlerException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            serialize(repo, mediaType, outStream, pretty, contexts);
        } finally {
            outStream.close();
        }
        return new ByteArrayInputStream(outStream.toByteArray());
    }

    public static Repository slurpRdf(String... paths)
            throws IOException, RepositoryException, RDFParseException {
        Repository repo = createMemoryRepository();
        slurpRdf(repo, paths);
        return repo;
    }

    public static Repository slurpRdf(Repository repo, String... paths)
            throws IOException, RepositoryException, RDFParseException {
        String[] patterns = new String[] {"n3", "rdf", "rdfs", "owl"};
        for (String path : paths) {
            File fileOrDir = new File(path);
            if (fileOrDir.isFile()) {
                logger.info("Loading: "+fileOrDir);
                loadDataFromFile(repo, fileOrDir);
            } else {
                for (Object o : FileUtils.listFiles(fileOrDir, patterns, true)) {
                    File file = (File) o;
                    logger.info("Loading: "+file);
                    loadDataFromFile(repo, file);
                }
            }
        }
        return repo;
    }


    public static Repository constructQuery(RepositoryConnection conn, String queryString)
            throws RepositoryException, MalformedQueryException,
                              QueryEvaluationException, RDFHandlerException {
        return constructQuery(conn, queryString, null);
    }

    public static Repository constructQuery(RepositoryConnection conn, String queryString,
            Map<String, Value> bindings)
            throws RepositoryException, MalformedQueryException,
                              QueryEvaluationException, RDFHandlerException {
        GraphQuery query = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
        if (bindings != null) {
            for (Map.Entry<String, Value> entry : bindings.entrySet()) {
                query.setBinding(entry.getKey(), entry.getValue());
            }
        }
        return constructQuery(conn, query);
    }

    public static Repository constructQuery(RepositoryConnection conn, GraphQuery query)
            throws RepositoryException, QueryEvaluationException, RDFHandlerException {
        Repository result = RDFUtil.createMemoryRepository();
        RepositoryConnection resConn = result.getConnection();
        boolean empty = true;
        try {
            query.evaluate(new RDFInserter(resConn));
            empty = resConn.size() == 0; // resConn.isEmpty()
        } finally {
            resConn.close();
        }
        return (empty)? null : result;
    }


    // Statement-level operations
    // NOTE: GraphUtil looked promising, but Graph:s aren't prominent in
    // Sesame 2 (as in hard to create, disconnected from repo etc)

    public static Statement one(RepositoryConnection conn,
            Resource s, URI p, Value o)
        throws RepositoryException {
        return one(conn, s, p, o, false);
    }

    public static Statement one(RepositoryConnection conn,
            Resource s, URI p, Value o,
            boolean includeInferred) throws RepositoryException {
        RepositoryResult<Statement> stmts = conn.getStatements(
               s, p, o, includeInferred);
        Statement st = null;
        while (stmts.hasNext()) {
            st = stmts.next();
            break;
        }
        stmts.close();
        return st;
    }

    public static Literal createDateTime(ValueFactory vf, Date time)
        throws RepositoryException {
        GregorianCalendar gregCal = new GregorianCalendar(
                TimeZone.getTimeZone("GMT"));
        gregCal.setTime(time);
        try {
            return vf.createLiteral(
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(
                        gregCal).toString(),
                    XMLSchema.DATETIME);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }


    // Idomatic operations..

    public static Repository replaceURI(Repository repo,
            java.net.URI oldUri,
            java.net.URI newUri) throws RepositoryException {
        return replaceURI(repo, oldUri, newUri, false);
    }

    public static Repository replaceURI(Repository repo,
            java.net.URI oldUri,
            java.net.URI newUri,
            boolean replacePredicates) throws RepositoryException {
        ValueFactory vf = repo.getValueFactory();
        return replaceURI(repo,
                vf.createURI(oldUri.toString()),
                vf.createURI(newUri.toString()),
                replacePredicates);
    }

    public static Repository replaceURI(Repository repo,
            URI oldUri,
            URI newUri) throws RepositoryException {
        return replaceURI(repo, oldUri, newUri, false);
    }

    public static Repository replaceURI(Repository repo,
            URI oldUri, URI newUri,
            boolean replacePredicates) throws RepositoryException {

        RepositoryConnection repoConn = repo.getConnection();
        Repository newRepo = createMemoryRepository();
        RepositoryConnection newRepoConn = newRepo.getConnection();

        try {
            RepositoryResult<Namespace> nsIter = repoConn.getNamespaces();
            while (nsIter.hasNext()) {
                Namespace ns = nsIter.next();
                newRepoConn.setNamespace(ns.getPrefix(), ns.getName());
            }
            nsIter.close();

            ValueFactory vf = newRepo.getValueFactory();

            RepositoryResult<Statement> stmts =
                    repoConn.getStatements(null, null, null, true);
            while (stmts.hasNext()) {
                Statement st = stmts.next();
                Resource subject = st.getSubject();
                URI predicate = st.getPredicate();
                Value object = st.getObject();
                if (subject instanceof URI) {
                    subject = changeURI(vf, ((URI) subject), oldUri, newUri);
                }
                if (replacePredicates) {
                    predicate = changeURI(vf, predicate, oldUri, newUri);
                }
                if (object instanceof URI) {
                    object = changeURI(vf, ((URI)object), oldUri, newUri);
                }

                newRepoConn.add(subject, predicate, object);
            }
            stmts.close();
        } finally {
            repoConn.close();
            newRepoConn.close();
        }
        return newRepo;
    }

    public static URI changeURI(ValueFactory vf, URI uri, URI oldUri, URI newUri) {
        String uriStr = uri.toString();
        String oldUriStr = oldUri.toString();
        if (!uriStr.startsWith(oldUriStr)) {
            return uri;
        } else {
            uriStr = newUri.toString() + uriStr.substring(oldUriStr.length());
        }
        return vf.createURI(uriStr);
    }

}

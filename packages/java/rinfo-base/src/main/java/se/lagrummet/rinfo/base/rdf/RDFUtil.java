package se.lagrummet.rinfo.base.rdf;

import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import org.openrdf.model.Literal;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFWriterFactory;
import org.openrdf.rio.RDFWriterRegistry;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;
import org.openrdf.sail.memory.MemoryStore;


public class RDFUtil {

    private static final Logger logger = LoggerFactory.getLogger(RDFUtil.class);


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
            InputStream stream, String baseUri, String mediaType)
            throws IOException, RDFParseException, RepositoryException {
        // TODO: more formats, e.g. RDFa (opt. guess from url?)
        RDFFormat format = RDFFormat.forMIMEType(mediaType);
        RepositoryConnection conn = repo.getConnection();
        try {
            conn.setAutoCommit(false);
            conn.add(stream, baseUri, format);
            conn.commit();
        } catch (RepositoryException e) {
            conn.rollback();
        } finally {
            conn.close();
        }
    }

    public static void addToRepo(Repository targetRepo, Repository repoToAdd)
            throws RepositoryException {
        RepositoryConnection targetConn = targetRepo.getConnection();
        RepositoryConnection connToAdd = repoToAdd.getConnection();
        try {
            targetConn.add(connToAdd.getStatements(null, null, null, false));
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

    public static void serialize(
            Repository repo, String mediaType, OutputStream outStream)
        throws RDFHandlerException, RepositoryException
    {
        RDFFormat format = RDFFormat.forMIMEType(mediaType);
        RDFWriter writer = null;
        // TODO: doesn't work with bnodes.
        //if (format.equals(RDFFormat.RDFXML)) {
        //    writer = new RDFXMLPrettyWriter(outStream);
        //} else {
            RDFWriterFactory factory = (RDFWriterFactory) RDFWriterRegistry
                    .getInstance().get(format);
            writer = factory.getWriter(outStream);
        //}
        RepositoryConnection conn = repo.getConnection();
        try {
            conn.exportStatements(null, null, null, false, writer);
        } finally {
            conn.close();
        }
        //writer.close()
    }

    public static InputStream toInputStream(Repository repo, String mediaType)
            throws IOException, RepositoryException, RDFHandlerException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        serialize(repo, mediaType, outStream);
        outStream.close();
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

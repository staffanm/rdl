package se.lagrummet.rinfo.store.supply;

import java.net.URI;
import java.net.URL;
import java.util.*;

import org.restlet.Context;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.Finder;
import org.restlet.resource.Handler;
import org.restlet.resource.Resource;

import se.lagrummet.rinfo.store.depot.DeletedDepotEntryException;
import se.lagrummet.rinfo.store.depot.DepotContent;
import se.lagrummet.rinfo.store.depot.DepotReadException;
import se.lagrummet.rinfo.store.depot.Depot;
import se.lagrummet.rinfo.store.depot.LockedDepotEntryException;


public class DepotFinder extends Finder {

    private Depot depot;

    public DepotFinder() { super(); }
    public DepotFinder(Context context) { super(context); }
    public DepotFinder(Context context, Depot depot) {
        this(context);
        this.depot = depot;
    }

    @Override
    public Handler findTarget(Request request, Response response) {
        List<DepotContent> results = null;
        try {
            // TODO:? E.g in a servlet container, how to handle </webapp/> base?
            // depot may not want such an "instrumental" base segment,
            // in case it should reinterpret non-full url as e.g. a tag uri..?
            // Is resourceRef.relativeRef ok? Only if we *don't* want the public ref!
            // See also resourceRef.baseRef

            String relativePath =
                    request.getResourceRef().getRelativeRef().getPath().toString();
            if (!relativePath.startsWith("/")) {
                relativePath = "/" + relativePath;
            }
            results = depot.find(relativePath);
        } catch (DeletedDepotEntryException e) {
            // TODO: Gone or 404?
            response.setStatus(Status.CLIENT_ERROR_GONE);
        } catch (LockedDepotEntryException e) {
            response.setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
        } catch (DepotReadException e) {
            throw new RuntimeException(e);
        }

        if (results==null) {
            // TODO: if (results == null)? Or on an exception? EntryDeletedException?
            //    response.setStatus(Status.CLIENT_ERROR_GONE, "Gone")
            return null;
        }
        // TODO: some kind of result which 303:s (use-case: resource path
        // subsumed by entry above which descibes it ("/ref/fs/sfs"..)..)
        // E.g.: if(404) findParentEntry, scan ENTRY-INFO/DESCRIBES.urls

        // TODO: Also, possible to have "symlink" resources, which at some
        // point in time become owl:sameAs, and thus needs e.g.
        // file://.../name-SYMLINK?

        // perhaps: new SupplyResource(results)
        Resource resource = new Resource(getContext(), request, response);
        List<Variant> reps = new ArrayList<Variant>();
        Reference baseRef = request.getOriginalRef().getBaseRef();
        if (baseRef == null) {
            baseRef = request.getResourceRef().getBaseRef();
        }
        for (DepotContent content : results) {
            reps.add(makeRepresentation(baseRef, content));
        }
        resource.setVariants(reps);
        resource.setNegotiateContent(true);
        /* TODO: 303 (or 307) instead of 200?
                 (For entry paths not ending with "/"? conneg before?)
        if (reps.size() > 1) {
            response.setStatus(Status.REDIRECTION_FOUND);
            //...
        }
        */
        return resource;
    }

    private FileRepresentation makeRepresentation(Reference baseRef, DepotContent content) {
        FileRepresentation fileRep = new FileRepresentation(
                content.getFile(), MediaType.valueOf(content.getMediaType()));
        fileRep.setModificationDate(new Date(content.getFile().lastModified()));
        fileRep.setLocationRef(new Reference(baseRef, content.getDepotUriPath()));
        if (content.getLang() != null) {
            List<Language> languages = new ArrayList<Language>();
            languages.add(Language.valueOf(content.getLang()));
            fileRep.setLanguages(languages);
        }
        return fileRep;
    }

}

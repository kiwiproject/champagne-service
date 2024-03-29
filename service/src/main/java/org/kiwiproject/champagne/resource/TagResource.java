package org.kiwiproject.champagne.resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.isNull;
import static org.kiwiproject.champagne.model.AuditRecord.Action.DELETED;
import static org.kiwiproject.champagne.util.DeployableSystems.checkUserAdminOfSystem;
import static org.kiwiproject.champagne.util.DeployableSystems.getSystemIdOrThrowBadRequest;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.champagne.dao.AuditRecordDao;
import org.kiwiproject.champagne.dao.TagDao;
import org.kiwiproject.champagne.model.AuditRecord;
import org.kiwiproject.champagne.model.Tag;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

@Path("/tag")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@PermitAll
@Slf4j
public class TagResource extends AuditableResource {

    private final TagDao tagDao;

    public TagResource(TagDao tagDao, AuditRecordDao auditRecordDao, ApplicationErrorDao errorDao) {
        super(auditRecordDao, errorDao);

        this.tagDao = tagDao;
    }

    @GET
    @Timed
    @ExceptionMetered
    public Response listTagsForCurrentSystem() {
        var systemId = getSystemIdOrThrowBadRequest();

        var tags = tagDao.findTagsForSystem(systemId);

        return Response.ok(tags).build();
    }

    @POST
    @Timed
    @ExceptionMetered
    public Response createTag(@Valid Tag tag) {
        checkUserAdminOfSystem();

        if (isNull(tag.getDeployableSystemId())) {
            var systemId = getSystemIdOrThrowBadRequest();
            tag = tag.withDeployableSystemId(systemId);
        }

        var tagId = tagDao.insertTag(tag);

        auditAction(tagId, Tag.class, AuditRecord.Action.CREATED);

        return Response.accepted().build();
    }

    @PUT
    @Path("/{id}")
    @Timed
    @ExceptionMetered
    public Response updateTag(Tag tag, @PathParam("id") Long tagId) {
        checkUserAdminOfSystem();

        var updateCount = tagDao.updateTag(tagId, tag.getName());

        if (updateCount > 0) {
            auditAction(tagId, Tag.class, AuditRecord.Action.UPDATED);
        }

        return Response.accepted().build();
    }

    @DELETE
    @Path("/{id}")
    @Timed
    @ExceptionMetered
    public Response deleteTag(@PathParam("id") Long id) {
        checkUserAdminOfSystem();

        var count = tagDao.deleteTag(id);

        if (count > 0) {
            auditAction(id, Tag.class, DELETED);
        }

        return Response.noContent().build();
    }
}

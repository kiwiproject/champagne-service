package org.kiwiproject.champagne.resource;

import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.champagne.dao.AuditRecordDao;
import org.kiwiproject.champagne.model.AuditRecord;
import org.kiwiproject.champagne.model.User;
import org.kiwiproject.champagne.util.AuthHelper;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

@DisplayName("AuditableResource")
@ExtendWith({DropwizardExtensionsSupport.class, ApplicationErrorExtension.class})
class AuditableResourceTest {

    private static final AuditRecordDao AUDIT_RECORD_DAO = mock(AuditRecordDao.class);
    private static final ApplicationErrorDao APPLICATION_ERROR_DAO = mock(ApplicationErrorDao.class);

    private static final TestResource TEST_RESOURCE = new TestResource(AUDIT_RECORD_DAO, APPLICATION_ERROR_DAO);

    private static final ResourceExtension APP = ResourceExtension.builder()
            .bootstrapLogging(false)
            .addResource(TEST_RESOURCE)
            .addProvider(JaxrsExceptionMapper.class)
            .build();

    @AfterEach
    void clearMocks() {
        reset(AUDIT_RECORD_DAO);
    }

    @Nested
    class AuditAction {

        @Test
        void shouldRecordActionWhenPrincipalExists() {
            TEST_RESOURCE.setupAuth = true;

            var response = APP.client().target("/record")
                    .request()
                    .get();

            assertOkResponse(response);

            verify(AUDIT_RECORD_DAO).insertAuditRecord(argThat(auditRecord -> auditRecord.getUserSystemIdentifier().equals("Bob")));
        }

        @Test
        void shouldNotRecordActionWhenPrincipalIsMissing() {
            TEST_RESOURCE.setupAuth = false;

            var response = APP.client().target("/record")
                    .request()
                    .get();

            assertOkResponse(response);

            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    @Path("/")
    public static class TestResource extends AuditableResource {

        public boolean setupAuth;

        public TestResource(AuditRecordDao auditRecordDao, ApplicationErrorDao errorDao) {
            super(auditRecordDao, errorDao);
        }

        @GET
        @Path("/record")
        public Response noAuth() {
            if (setupAuth) {
                AuthHelper.setupCurrentPrincipalFor("Bob");
            }

            auditAction(1L, User.class, AuditRecord.Action.CREATED);

            // Always remove just in case
            AuthHelper.removePrincipal();

            return Response.ok().build();
        }

    }
}

package org.kiwiproject.champagne.resource;

import static javax.ws.rs.client.Entity.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertAcceptedResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.GenericType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.champagne.dao.AuditRecordDao;
import org.kiwiproject.champagne.dao.ComponentDao;
import org.kiwiproject.champagne.dao.HostDao;
import org.kiwiproject.champagne.junit.jupiter.DeployableSystemExtension;
import org.kiwiproject.champagne.junit.jupiter.JwtExtension;
import org.kiwiproject.champagne.model.AuditRecord;
import org.kiwiproject.champagne.model.AuditRecord.Action;
import org.kiwiproject.champagne.model.Component;
import org.kiwiproject.champagne.model.Host;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.mockito.ArgumentCaptor;

@DisplayName("HostConfigurationResource")
@ExtendWith({DropwizardExtensionsSupport.class, ApplicationErrorExtension.class})
class HostConfigurationResourceTest {
    private static final HostDao HOST_DAO = mock(HostDao.class);
    private static final ComponentDao COMPONENT_DAO = mock(ComponentDao.class);
    private static final AuditRecordDao AUDIT_RECORD_DAO = mock(AuditRecordDao.class);
    private static final ApplicationErrorDao APPLICATION_ERROR_DAO = mock(ApplicationErrorDao.class);
    private static final HostConfigurationResource HOST_CONFIGURATION_RESOURCE = new HostConfigurationResource(HOST_DAO, COMPONENT_DAO, AUDIT_RECORD_DAO, APPLICATION_ERROR_DAO);

    private static final ResourceExtension APP = ResourceExtension.builder()
            .bootstrapLogging(false)
            .addResource(HOST_CONFIGURATION_RESOURCE)
            .addProvider(JaxrsExceptionMapper.class)
            .build();

    @RegisterExtension
    public final JwtExtension jwtExtension = new JwtExtension("bob");

    @RegisterExtension
    public final DeployableSystemExtension deployableSystemExtension = new DeployableSystemExtension(1L, true);

    @AfterEach
    void tearDown() {
        reset(HOST_DAO, COMPONENT_DAO, AUDIT_RECORD_DAO);
    }

    @Nested
    class ListHostsForEnvironment {

        @Test
        void shouldReturnListOfHostsForEnv() {
            var host = Host.builder()
                    .id(1L)
                    .hostname("localhost")
                    .deployableSystemId(1L)
                    .build();

            when(HOST_DAO.findHostsByEnvId(1L, 1L)).thenReturn(List.of(host));

            var response = APP.client().target("/host/{envId}")
                    .resolveTemplate("envId", 1L)
                    .request()
                    .get();

            assertOkResponse(response);

            var hosts = response.readEntity(new GenericType<List<Host>>() {
            });

            var foundHost = first(hosts);
            assertThat(foundHost)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt", "updatedAt")
                    .isEqualTo(host);

            verify(HOST_DAO).findHostsByEnvId(1L, 1L);
            verifyNoMoreInteractions(HOST_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class CreateHost {

        @Test
        void shouldAddTheGivenHost() {
            when(HOST_DAO.insertHost(any(Host.class), anyString())).thenReturn(1L);

            var host = Host.builder()
                    .hostname("localhost")
                    .tags(List.of("foo"))
                    .deployableSystemId(1L)
                    .build();

            var response = APP.client().target("/host")
                    .request()
                    .post(json(host));

            assertAcceptedResponse(response);

            verify(HOST_DAO).insertHost(any(Host.class), anyString());

            verifyAuditRecorded(Host.class, Action.CREATED);

            verifyNoMoreInteractions(HOST_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldAddTheGivenHostWithTheCurrentSystemWhenNotProvided() {
            when(HOST_DAO.insertHost(any(Host.class), anyString())).thenReturn(1L);

            var host = Host.builder()
                    .hostname("localhost")
                    .tags(List.of("foo"))
                    .build();

            var response = APP.client().target("/host")
                    .request()
                    .post(json(host));

            assertAcceptedResponse(response);

            verify(HOST_DAO).insertHost(any(Host.class), anyString());

            verifyAuditRecorded(Host.class, Action.CREATED);

            verifyNoMoreInteractions(HOST_DAO, AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class UpdateHost {

        @Test
        void shouldUpdateGivenHost() {
            when(HOST_DAO.updateHost("foo", "tag1,tag2", 1L)).thenReturn(1);

            var response = APP.client().target("/host")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .put(json(Map.of("hostname", "foo", "tags", List.of("tag1", "tag2"), "id", 1L)));

            assertAcceptedResponse(response);

            verify(HOST_DAO).updateHost("foo", "tag1,tag2", 1L);

            verifyAuditRecorded(Host.class, Action.UPDATED);

            verifyNoMoreInteractions(HOST_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldNotAuditUpdateIfDoesNotChangeAnything() {
            when(HOST_DAO.updateHost("foo", "tag1,tag2", 1L)).thenReturn(0);

            var response = APP.client().target("/host")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .put(json(Map.of("hostname", "foo", "tags", List.of("tag1", "tag2"), "id", 1L)));

            assertAcceptedResponse(response);

            verify(HOST_DAO).updateHost("foo", "tag1,tag2", 1L);
            verifyNoMoreInteractions(HOST_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class DeleteHost {

        @Test
        void shouldDeleteGivenHost() {
            when(HOST_DAO.deleteHost(1L)).thenReturn(1);

            var response = APP.client().target("/host")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .delete();

            assertAcceptedResponse(response);

            verify(HOST_DAO).deleteHost(1L);

            verifyAuditRecorded(Host.class, Action.DELETED);

            verifyNoMoreInteractions(HOST_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldNotAuditDeleteIfDoesNotChangeAnything() {
            when(HOST_DAO.deleteHost(1L)).thenReturn(0);

            var response = APP.client().target("/host")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .delete();

            assertAcceptedResponse(response);

            verify(HOST_DAO).deleteHost(1L);
            verifyNoMoreInteractions(HOST_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    private void verifyAuditRecorded(Class<?> auditRecordClass, Action action) {
        var argCapture = ArgumentCaptor.forClass(AuditRecord.class);
        verify(AUDIT_RECORD_DAO).insertAuditRecord(argCapture.capture());

        var audit = argCapture.getValue();

        assertThat(audit.getRecordId()).isEqualTo(1);
        assertThat(audit.getRecordType()).isEqualTo(auditRecordClass.getSimpleName());
        assertThat(audit.getAction()).isEqualTo(action);
    }

    @Nested
    class ListComponents {

        @Test
        void shouldReturnListOfComponents() {
            var component = Component.builder()
                    .id(2L)
                    .componentName("foo-service")
                    .tag("core")
                    .build();

            when(COMPONENT_DAO.findComponentsForSystem(1L)).thenReturn(List.of(component));

            var response = APP.client().target("/host/components")
                    .request()
                    .get();

            assertOkResponse(response);

            var components = response.readEntity(new GenericType<List<Component>>() {
            });

            var foundComponent = first(components);
            assertThat(foundComponent)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt", "updatedAt")
                    .isEqualTo(component);

            verify(COMPONENT_DAO).findComponentsForSystem(1L);
            verifyNoMoreInteractions(HOST_DAO, COMPONENT_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class ListComponentsForHost {

        @Test
        void shouldReturnListOfComponentsForHost() {
            var host = Host.builder()
                    .id(1L)
                    .hostname("localhost")
                    .tags(List.of("core"))
                    .build();

            when(HOST_DAO.findById(1L)).thenReturn(Optional.of(host));

            var component = Component.builder()
                    .id(2L)
                    .componentName("foo-service")
                    .tag("core")
                    .build();

            when(COMPONENT_DAO.findComponentsByHostTags(List.of("core"))).thenReturn(List.of(component));

            var response = APP.client().target("/host/{hostId}/components")
                    .resolveTemplate("hostId", 1L)
                    .request()
                    .get();

            assertOkResponse(response);

            var components = response.readEntity(new GenericType<List<Component>>() {
            });

            var foundComponent = first(components);
            assertThat(foundComponent)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt", "updatedAt")
                    .isEqualTo(component);

            verify(HOST_DAO).findById(1L);
            verify(COMPONENT_DAO).findComponentsByHostTags(List.of("core"));
            verifyNoMoreInteractions(HOST_DAO, COMPONENT_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }

        @Test
        void shouldReturn404WhenHostNotFound() {
            when(HOST_DAO.findById(1L)).thenReturn(Optional.empty());

            var response = APP.client().target("/host/{hostId}/components")
                    .resolveTemplate("hostId", 1L)
                    .request()
                    .get();

            assertNotFoundResponse(response);
        }
    }

    @Nested
    class CreateComponent {

        @Test
        void shouldAddTheGivenComponent() {
            when(COMPONENT_DAO.insertComponent(any(Component.class))).thenReturn(1L);

            var component = Component.builder()
                    .componentName("foo-service")
                    .tag("core")
                    .deployableSystemId(1L)
                    .build();

            var response = APP.client().target("/host/component")
                    .request()
                    .post(json(component));

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).insertComponent(any(Component.class));

            verifyAuditRecorded(Component.class, Action.CREATED);

            verifyNoMoreInteractions(COMPONENT_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldAddTheGivenComponentWithTheCurrentSystemWhenNotProvided() {
            when(COMPONENT_DAO.insertComponent(any(Component.class))).thenReturn(1L);

            var component = Component.builder()
                    .componentName("foo-service")
                    .tag("core")
                    .build();

            var response = APP.client().target("/host/component")
                    .request()
                    .post(json(component));

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).insertComponent(any(Component.class));

            verifyAuditRecorded(Component.class, Action.CREATED);

            verifyNoMoreInteractions(COMPONENT_DAO, AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class UpdateComponent {

        @Test
        void shouldUpdateGivenComponent() {
            when(COMPONENT_DAO.updateComponent("foo", "tag1", 1L)).thenReturn(1);

            var response = APP.client().target("/host/component")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .put(json(Map.of("componentName", "foo", "tag", "tag1", "id", 1L)));

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).updateComponent("foo", "tag1", 1L);

            verifyAuditRecorded(Component.class, Action.UPDATED);

            verifyNoMoreInteractions(COMPONENT_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldNotAuditUpdateIfDoesNotChangeAnything() {
            when(COMPONENT_DAO.updateComponent("foo", "tag1", 1L)).thenReturn(0);

            var response = APP.client().target("/host/component")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .put(json(Map.of("componentName", "foo", "tag", "tag1", "id", 1L)));

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).updateComponent("foo", "tag1", 1L);
            verifyNoMoreInteractions(COMPONENT_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }

    @Nested
    class DeleteComponent {

        @Test
        void shouldDeleteGivenComponent() {
            when(COMPONENT_DAO.deleteComponent(1L)).thenReturn(1);

            var response = APP.client().target("/host/component")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .delete();

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).deleteComponent(1L);

            verifyAuditRecorded(Component.class, Action.DELETED);

            verifyNoMoreInteractions(COMPONENT_DAO, AUDIT_RECORD_DAO);
        }

        @Test
        void shouldNotAuditDeleteIfDoesNotChangeAnything() {
            when(COMPONENT_DAO.deleteComponent(1L)).thenReturn(0);

            var response = APP.client().target("/host/component")
                    .path("{id}")
                    .resolveTemplate("id", 1)
                    .request()
                    .delete();

            assertAcceptedResponse(response);

            verify(COMPONENT_DAO).deleteComponent(1L);
            verifyNoMoreInteractions(COMPONENT_DAO);
            verifyNoInteractions(AUDIT_RECORD_DAO);
        }
    }
}

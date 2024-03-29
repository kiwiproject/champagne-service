package org.kiwiproject.champagne;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.dropwizard.app.DropwizardAppTests.healthCheckNamesOf;
import static org.kiwiproject.test.dropwizard.app.DropwizardAppTests.registeredResourceClassesOf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.champagne.config.AppConfig;
import org.kiwiproject.champagne.resource.AuditRecordResource;
import org.kiwiproject.champagne.resource.AuthResource;
import org.kiwiproject.champagne.resource.BuildResource;
import org.kiwiproject.champagne.resource.DeploymentEnvironmentResource;
import org.kiwiproject.champagne.resource.HostConfigurationResource;
import org.kiwiproject.champagne.resource.TaskResource;
import org.kiwiproject.champagne.resource.UserResource;
import org.kiwiproject.test.dropwizard.app.PostgresAppTestExtension;

import io.dropwizard.testing.junit5.DropwizardAppExtension;

class AppTest {

    @RegisterExtension
    static final PostgresAppTestExtension<AppConfig> POSTGRES_APP_TEST_EXTENSION =
        new PostgresAppTestExtension<>("migrations.xml", "config-unit-test.yml", App.class);

    private static final DropwizardAppExtension<AppConfig> APP = POSTGRES_APP_TEST_EXTENSION.getApp();

    @Test
    void shouldRegisterResources() {
        assertThat(registeredResourceClassesOf(APP)).contains(
            AuthResource.class,
            AuditRecordResource.class,
            BuildResource.class,
            DeploymentEnvironmentResource.class,
            HostConfigurationResource.class,
            TaskResource.class,
            UserResource.class
        );
    }

    @Test
    void shouldRegisterHealthChecks() {
        assertThat(healthCheckNamesOf(APP)).contains(
                "database",
                "deadlocks",
                "Job: Clean Out Audits",
                "Unknown JSON Properties"
        );
    }
}

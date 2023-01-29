package org.kiwiproject.champagne.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.champagne.util.TestObjects.insertDeploymentEnvironmentRecord;
import static org.kiwiproject.champagne.util.TestObjects.insertUserRecord;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.champagne.model.DeploymentEnvironment;
import org.kiwiproject.champagne.dao.mappers.DeploymentEnvironmentMapper;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;
import org.kiwiproject.test.junit.jupiter.PostgresLiquibaseTestExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@DisplayName("DeploymentEnvironmentDao")
@ExtendWith(SoftAssertionsExtension.class)
class DeploymentEnvironmentDaoTest {
    
    @RegisterExtension
    static final PostgresLiquibaseTestExtension POSTGRES = new PostgresLiquibaseTestExtension("migrations.xml");

    @RegisterExtension
    final Jdbi3DaoExtension<DeploymentEnvironmentDao> daoExtension = Jdbi3DaoExtension.<DeploymentEnvironmentDao>builder()
            .daoType(DeploymentEnvironmentDao.class)
            .dataSource(POSTGRES.getTestDataSource())
            .build();

    private DeploymentEnvironmentDao dao;
    private Handle handle;

    private long testUserId;

    @BeforeEach
    void setUp() {
        dao = daoExtension.getDao();
        handle = daoExtension.getHandle();

        testUserId = insertUserRecord(handle, "jdoe");
    }

    @Nested
    class InsertEnvironment {

        @Test
        void shouldInsertUserSuccessfully() {
            var beforeInsert = ZonedDateTime.now();

            var envToInsert = DeploymentEnvironment.builder()
                    .name("PRODUCTION")
                    .build();

            var id = dao.insertEnvironment(envToInsert);

            var envs = handle.select("select * from deployment_environments where id = ?", id)
                .map(new DeploymentEnvironmentMapper())
                .list();

            assertThat(envs).hasSize(1);

            var env = first(envs);
            assertThat(env.getId()).isEqualTo(id);

            assertTimeDifferenceWithinTolerance("createdAt", beforeInsert, env.getCreatedAt().atZone(ZoneOffset.UTC), 1000L);
            assertTimeDifferenceWithinTolerance("updatedAt", beforeInsert, env.getUpdatedAt().atZone(ZoneOffset.UTC), 1000L);

            assertThat(env.getName()).isEqualTo("PRODUCTION");
        }
    }

    @Nested
    class UpdateUser {

        @Test
        void shouldUpdateDeploymentEnvironmentSuccessfully() {
            long envId = insertDeploymentEnvironmentRecord(handle, "TEST", testUserId);

            var envToUpdate = DeploymentEnvironment.builder()
                    .id(envId)
                    .name("PRODUCTION")
                    .build();

            dao.updateEnvironment(envToUpdate);

            var envs = handle.select("select * from deployment_environments where id = ?", envId)
                .map(new DeploymentEnvironmentMapper())
                .list();

            assertThat(envs).hasSize(1);

            var user = first(envs);
            assertThat(user.getName()).isEqualTo("PRODUCTION");
        }
    }

    @Nested
    class FindAllEnvironments {

        @Test
        void shouldReturnListOfDeploymentEnvironments() {
            insertDeploymentEnvironmentRecord(handle, "DEV", testUserId);

            var environments = dao.findAllEnvironments();
            assertThat(environments)
                .extracting("name")
                .contains("DEV");
        }

        @Test
        void shouldReturnEmptyListWhenNoDeploymentEnvironmentsFound() {
            var environments = dao.findAllEnvironments();
            assertThat(environments).isEmpty();
        }
    }

    @Nested
    class HardDeleteById {

        @Test
        void shouldDeleteDeploymentEnvironmentSuccessfully() {
            long envId = insertDeploymentEnvironmentRecord(handle, "TRAINING", testUserId);

            dao.hardDeleteById(envId);

            var envs = handle.select("select * from deployment_environments where id = ?", envId).mapToMap().list();
            assertThat(envs).isEmpty();
        }

    }

    @Nested
    class SoftDeleteById {

        @Test
        void shouldSoftDeleteDeploymentEnvironmentSuccessfully(SoftAssertions softly) {
            var id = insertDeploymentEnvironmentRecord(handle, "TEST", testUserId);

            dao.softDeleteById(id);

            var envs = handle.select("select * from deployment_environments where id = ?", id)
                .map(new DeploymentEnvironmentMapper())
                .list();

            assertThat(envs).hasSize(1);

            var user = first(envs);
            softly.assertThat(user.getId()).isEqualTo(id);
            softly.assertThat(user.isDeleted()).isEqualTo(true);
        }

    }

    @Nested
    class UnDeleteById {

        @Test
        void shouldUnDeleteDeploymentEnvironmentSuccessfully(SoftAssertions softly) {
            var id = insertDeploymentEnvironmentRecord(handle, "TEST", testUserId);
            handle.execute("update deployment_environments set deleted=true where id = ?", id);

            dao.unSoftDeleteById(id);

            var envs = handle.select("select * from deployment_environments where id = ?", id)
                .map(new DeploymentEnvironmentMapper())
                .list();

            assertThat(envs).hasSize(1);

            var user = first(envs);
            softly.assertThat(user.getId()).isEqualTo(id);
            softly.assertThat(user.isDeleted()).isEqualTo(false);
        }

    }

}
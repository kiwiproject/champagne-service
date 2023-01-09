package org.kiwiproject.champagne.jdbi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.champagne.core.User;
import org.kiwiproject.champagne.jdbi.mappers.UserMapper;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;
import org.kiwiproject.test.junit.jupiter.PostgresLiquibaseTestExtension;

@DisplayName("UserDao")
@ExtendWith(SoftAssertionsExtension.class)
class UserDaoTest {
    
    @RegisterExtension
    static final PostgresLiquibaseTestExtension POSTGRES = new PostgresLiquibaseTestExtension("migrations.xml");

    @RegisterExtension
    final Jdbi3DaoExtension<UserDao> daoExtension = Jdbi3DaoExtension.<UserDao>builder()
            .daoType(UserDao.class)
            .dataSource(POSTGRES.getTestDataSource())
            .build();

    private UserDao dao;
    private Handle handle;

    @SuppressWarnings("SqlWithoutWhere")
    @BeforeEach
    void setUp() {
        dao = daoExtension.getDao();
        handle = daoExtension.getHandle();
    }

    @Nested
    class InsertUser {

        @Test
        void shouldInsertUserSuccessfully(SoftAssertions softly) {
            var beforeInsert = ZonedDateTime.now();

            var userToInsert = User.builder()
                .systemIdentifier("doej")
                .firstName("John")
                .lastName("Doe")
                .displayName("John Doe")
                .build();

            var id = dao.insertUser(userToInsert);

            var users = handle.select("select * from users where id = ?", id)
                .map(new UserMapper())
                .list();

            assertThat(users).hasSize(1);

            var user = first(users);
            softly.assertThat(user.getId()).isEqualTo(id);

            assertTimeDifferenceWithinTolerance(softly, "createdAt", beforeInsert, user.getCreatedAt().atZone(ZoneOffset.UTC), 1000L);
            assertTimeDifferenceWithinTolerance(softly, "updatedAt", beforeInsert, user.getUpdatedAt().atZone(ZoneOffset.UTC), 1000L);

            softly.assertThat(user.getSystemIdentifier()).isEqualTo("doej");
            softly.assertThat(user.getFirstName()).isEqualTo("John");
            softly.assertThat(user.getLastName()).isEqualTo("Doe");
            softly.assertThat(user.getDisplayName()).isEqualTo("John Doe");
            softly.assertThat(user.isDeleted()).isEqualTo(false);
        }
    }

    @Nested
    class UpdateUser {

        @Test
        void shouldUpdateUserSuccessfully(SoftAssertions softly) {
            saveTestUserRecord("jdoe", "John", "Doe");
            long userId = handle.select("select * from users where system_identifier = ?", "jdoe")
                .mapToMap()
                .findFirst()
                .map(row -> (long) row.get("id"))
                .orElseThrow();

            var beforeUpdate = ZonedDateTime.now();

            var userToUpdate =  User.builder()
                .id(userId)
                .systemIdentifier("doej")
                .firstName("Foo")
                .lastName("Doe")
                .displayName("Foo Doe")
                .build();

            dao.updateUser(userToUpdate);

            var users = handle.select("select * from users where id = ?", userId)
                .map(new UserMapper())
                .list();

            assertThat(users).hasSize(1);

            var user = first(users);
            softly.assertThat(user.getId()).isEqualTo(userId);

            assertTimeDifferenceWithinTolerance(softly, "updatedAt", beforeUpdate, user.getUpdatedAt().atZone(ZoneOffset.UTC), 1000L);

            softly.assertThat(user.getSystemIdentifier()).isEqualTo("doej");
            softly.assertThat(user.getFirstName()).isEqualTo("Foo");
            softly.assertThat(user.getLastName()).isEqualTo("Doe");
            softly.assertThat(user.getDisplayName()).isEqualTo("Foo Doe");
        }
    }

    @Nested
    class FindUserBySystemIdentifier {

        @Test
        void shouldReturnOptionalWithUserWhenFound() {
            saveTestUserRecord("doeja", "Jane", "Doe");

            var user = dao.findBySystemIdentifier("doeja");
            assertThat(user).isPresent();
        }

        @Test
        void shouldReturnEmptyOptionalWhenUserNotFound() {
            var user = dao.findBySystemIdentifier("doeja");
            assertThat(user).isEmpty();
        }
    }

    @Nested
    class FindPagedUsers {

        @Test
        void shouldReturnListOfUsers() {
            saveTestUserRecord("fooBar", "Foo", "Bar");

            var users = dao.findPagedUsers(0, 10);
            assertThat(users)
                .extracting("systemIdentifier", "firstName", "lastName", "deleted")
                .contains(tuple("fooBar", "Foo", "Bar", false));
        }

        @Test
        void shouldReturnEmptyListWhenNoUsersFound() {
            saveTestUserRecord("fooBar", "Foo", "Bar");

            var users = dao.findPagedUsers(10, 10);
            assertThat(users).isEmpty();
        }
    }

    @Nested
    class FindPagedUsersIncludingDeleted {

        @Test
        void shouldReturnListOfUsers() {
            saveTestUserRecord("fooBar", "Foo", "Bar", true);

            var users = dao.findPagedUsersIncludingDeleted(0, 10);
            assertThat(users)
                    .extracting("systemIdentifier", "firstName", "lastName", "deleted")
                    .contains(tuple("fooBar", "Foo", "Bar", true));
        }

        @Test
        void shouldReturnEmptyListWhenNoUsersFound() {
            saveTestUserRecord("fooBar", "Foo", "Bar");

            var users = dao.findPagedUsersIncludingDeleted(10, 10);
            assertThat(users).isEmpty();
        }
    }

    @Nested
    class CountUsers {

        @Test
        void shouldReturnCountOfUsers() {
            saveTestUserRecord("fooBar", "Foo", "Bar");

            var users = dao.countUsers();
            assertThat(users).isOne();
        }

        @Test
        void shouldReturnEmptyListWhenNoUsersFound() {
            var users = dao.countUsers();
            assertThat(users).isZero();
        }
    }

    @Nested
    class CountUsersIncludingDeleted {

        @Test
        void shouldReturnCountOfUsers() {
            saveTestUserRecord("fooBar", "Foo", "Bar", true);

            var users = dao.countUsersIncludingDeleted();
            assertThat(users).isOne();
        }

        @Test
        void shouldReturnEmptyListWhenNoUsersFound() {
            var users = dao.countUsersIncludingDeleted();
            assertThat(users).isZero();
        }
    }

    @Nested
    class DeleteUser {

        @Test
        void shouldDeleteUserSuccessfully(SoftAssertions softly) {
            saveTestUserRecord("jdoe", "John", "Doe");
            long userId = handle.select("select * from users where system_identifier = ?", "jdoe")
                .mapToMap()
                .findFirst()
                .map(row -> (long) row.get("id"))
                .orElseThrow();

            dao.deleteUser(userId);

            var users = handle.select("select * from users where id = ?", userId).map(new UserMapper()).list();
            assertThat(users).hasSize(1);

            var user = first(users);
            softly.assertThat(user.getId()).isEqualTo(userId);
            softly.assertThat(user.isDeleted()).isEqualTo(true);
        }

    }

    @Nested
    class ReactivateUser {

        @Test
        void shouldReactivateUserSuccessfully(SoftAssertions softly) {
            saveTestUserRecord("jdoe", "John", "Doe", true);
            long userId = handle.select("select * from users where system_identifier = ?", "jdoe")
                .mapToMap()
                .findFirst()
                .map(row -> (long) row.get("id"))
                .orElseThrow();

            dao.reactivateUser(userId);

            var users = handle.select("select * from users where id = ?", userId).map(new UserMapper()).list();
            assertThat(users).hasSize(1);

            var user = first(users);
            softly.assertThat(user.getId()).isEqualTo(userId);
            softly.assertThat(user.isDeleted()).isEqualTo(false);
        }

    }

    private void saveTestUserRecord(String systemIdentifier, String firstName, String lastName) {
        saveTestUserRecord(systemIdentifier, firstName, lastName, false);
    }

    private void saveTestUserRecord(String systemIdentifier, String firstName, String lastName, boolean deleted) {
        handle.execute("insert into users (first_name, last_name, display_name, system_identifier, deleted) values (?, ?, ?, ?, ?)", firstName, lastName, firstName + " " + lastName, systemIdentifier, deleted);
    }
}

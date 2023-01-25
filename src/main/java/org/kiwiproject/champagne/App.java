package org.kiwiproject.champagne;

import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi3.bundles.JdbiExceptionsBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.dhatim.dropwizard.jwt.cookie.authentication.JwtCookieAuthBundle;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.kiwiproject.champagne.config.AppConfig;
import org.kiwiproject.champagne.jdbi.DeploymentEnvironmentDao;
import org.kiwiproject.champagne.jdbi.ReleaseDao;
import org.kiwiproject.champagne.jdbi.ReleaseStatusDao;
import org.kiwiproject.champagne.jdbi.TaskDao;
import org.kiwiproject.champagne.jdbi.TaskStatusDao;
import org.kiwiproject.champagne.jdbi.UserDao;
import org.kiwiproject.champagne.resource.AuthResource;
import org.kiwiproject.champagne.resource.TaskResource;
import org.kiwiproject.champagne.resource.UserResource;
import org.kiwiproject.dropwizard.jdbi3.Jdbi3Builders;
import org.kiwiproject.dropwizard.util.config.JacksonConfig;
import org.kiwiproject.dropwizard.util.exception.StandardExceptionMappers;
import org.kiwiproject.dropwizard.util.jackson.StandardJacksonConfigurations;

import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

public class App extends Application<AppConfig> {

    public static void main(String[] args) throws Exception {
        new App().run(args);    
    }

    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        bootstrap.addBundle(new JdbiExceptionsBundle());
        bootstrap.addBundle(new MigrationsBundle<>() {
            @Override
            public DataSourceFactory getDataSourceFactory(AppConfig config) {
                return config.getDataSourceFactory();
            }
        });

        var authBundle = JwtCookieAuthBundle.<AppConfig>getDefault()
                .withConfigurationSupplier(AppConfig::getJwtCookieAuth);

        bootstrap.addBundle(authBundle);
    }

    @Override
    public void run(AppConfig configuration, Environment environment) {

        setupJsonProcessing(environment);
        StandardExceptionMappers.register(configuration.getServerFactory(), environment);

        var jdbi = Jdbi3Builders.buildManagedJdbi(environment, configuration.getDataSourceFactory(), new PostgresPlugin());
        
        var userDao = jdbi.onDemand(UserDao.class);
        var releaseDao = jdbi.onDemand(ReleaseDao.class);
        var releaseStatusDao = jdbi.onDemand(ReleaseStatusDao.class);
        var taskDao = jdbi.onDemand(TaskDao.class);
        var taskStatusDao = jdbi.onDemand(TaskStatusDao.class);
        var deploymentEnvironmentDao = jdbi.onDemand(DeploymentEnvironmentDao.class);

        environment.jersey().register(new AuthResource(userDao));
        environment.jersey().register(new TaskResource(releaseDao, releaseStatusDao, taskDao, taskStatusDao, deploymentEnvironmentDao));
        environment.jersey().register(new UserResource(userDao));

        configureCors(environment);
    }

    private static void setupJsonProcessing(Environment environment) {
        var jacksonConfig = JacksonConfig.builder()
            .ignoreButWarnForUnknownJsonProperties(true)
            .registerHealthCheckForUnknownJsonProperties(true)
            .readAndWriteDateTimestampsAsMillis(true)
            .writeNilJaxbElementsAsNull(true)
            .build();

            StandardJacksonConfigurations.registerAllStandardJacksonConfigurations(jacksonConfig, environment);
    }

    private static void configureCors(Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS params
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin,Authorization");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }

}

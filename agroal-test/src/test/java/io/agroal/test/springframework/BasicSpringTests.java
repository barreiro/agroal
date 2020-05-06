// Copyright (C) 2020 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.test.springframework;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.test.MockConnection;
import io.agroal.test.MockDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Properties;

import static io.agroal.test.AgroalTestGroup.SPRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@Tag( SPRING )
@RunWith( SpringRunner.class )
@JdbcTest
@ComponentScan( basePackageClasses = io.agroal.springframework.AgroalDataSource.class ) // tests do not pick META-INF/spring.factories
@TestPropertySource( properties = {"spring.datasource.url=jdbc:irrelevant",
        "spring.datasource.driver-class-name=io.agroal.test.springframework.BasicSpringTests$FakeDriver",
        "spring.datasource.agroal.min-size=" + BasicSpringTests.MIN_SIZE,
        "spring.datasource.agroal.initial-size=" + BasicSpringTests.INITIAL_SIZE,
        "spring.datasource.agroal.max-size=" + BasicSpringTests.MAX_SIZE
} )
@AutoConfigureTestDatabase( replace = AutoConfigureTestDatabase.Replace.NONE )
public class BasicSpringTests {

    public static final int MIN_SIZE = 13, INITIAL_SIZE = 7, MAX_SIZE = 37;

    @Autowired
    private DataSource dataSource;

    @org.junit.Test
    @DisplayName( "test deployment on Spring Boot container" )
    public void basicSpringConnectionAcquireTest() throws Exception {
        assertTrue( dataSource instanceof AgroalDataSource );
        AgroalConnectionPoolConfiguration poolConfiguration = ( (AgroalDataSource) dataSource ).getConfiguration().connectionPoolConfiguration();

        // Check that the configuration was injected
        assertEquals( MIN_SIZE, poolConfiguration.minSize() );
        assertEquals( INITIAL_SIZE, poolConfiguration.initialSize() );
        assertEquals( MAX_SIZE, poolConfiguration.maxSize() );

        try ( Connection c = dataSource.getConnection() ) {
            assertEquals( FakeDriver.FakeConnection.FAKE_SCHEMA, c.getSchema() );
        }
    }

    // --- //

    public static class FakeDriver implements MockDriver {

        @Override
        public Connection connect(String url, Properties info) {
            return new FakeDriver.FakeConnection();
        }

        private static class FakeConnection implements MockConnection {

            private static final String FAKE_SCHEMA = "skeema";

            @Override
            public String getSchema() {
                return FAKE_SCHEMA;
            }
        }
    }

    // --- //

    @SpringBootConfiguration
    public static class TestConfiguration {
    }
}


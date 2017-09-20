// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.test.basic;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.test.MockConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static io.agroal.test.AgroalTestGroup.FUNCTIONAL;
import static io.agroal.test.MockDriver.deregisterMockDriver;
import static io.agroal.test.MockDriver.registerMockDriver;
import static java.text.MessageFormat.format;
import static java.util.logging.Logger.getLogger;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@Tag( FUNCTIONAL )
public class ConnectionCloseTests {

    private static final Logger logger = getLogger( ConnectionCloseTests.class.getName() );

    private static final String FAKE_SCHEMA = "skeema";

    @BeforeAll
    public static void setupMockDriver() {
        registerMockDriver( FakeSchemaConnection.class );
    }

    @AfterAll
    public static void teardown() {
        deregisterMockDriver();
    }

    // --- //

    @Test
    @DisplayName( "Connection wrapper in closed state" )
    public void basicConnectionCloseTest() throws SQLException {
        try ( AgroalDataSource dataSource = AgroalDataSource.from( new AgroalDataSourceConfigurationSupplier() ) ) {
            Connection connection = dataSource.getConnection();

            assertAll( () -> {
                assertFalse( connection.isClosed(), "Expected open connection, but it's closed" );
                assertNotNull( connection.getSchema(), "Expected non null value" );
            } );

            connection.close();

            assertAll( () -> {
                assertThrows( SQLException.class, connection::getSchema );
                assertTrue( connection.isClosed(), "Expected closed connection, but it's open" );
            } );
        }
    }

    @Test
    @Disabled( "TODO: Create mock statements" ) // TODO: 
    @DisplayName( "Connection closes statements" )
    public void statementCloseTest() throws SQLException {
        try ( AgroalDataSource dataSource = AgroalDataSource.from( new AgroalDataSourceConfigurationSupplier() ) ) {
            Connection connection = dataSource.getConnection();
            logger.info( format( "Creating 2 statements on Connection {0}", connection ) );

            Statement statementOne = connection.createStatement();
            Statement statementTwo = connection.createStatement();
            statementTwo.close();

            assertAll( () -> {
                assertNotNull( statementOne.getResultSet(), "Expected non null value" );
                assertFalse( statementOne.isClosed(), "Expected open statement, but it's closed" );
                assertThrows( SQLException.class, statementTwo::getResultSet, "Expected SQLException on closed connection" );
                assertTrue( statementTwo.isClosed(), "Expected closed statement, but it's open" );
            } );

            connection.close();

            assertAll( () -> {
                assertThrows( SQLException.class, statementOne::getResultSet, "Expected SQLException on closed connection" );
                assertTrue( statementOne.isClosed(), "Expected closed statement, but it's open" );
            } );
        }
    }

    // --- //

    public static class FakeSchemaConnection implements MockConnection {
        @Override
        public String getSchema() {
            return FAKE_SCHEMA;
        }
    }
}

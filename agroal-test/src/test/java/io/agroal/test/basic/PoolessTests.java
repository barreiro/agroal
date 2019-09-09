// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.test.basic;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceListener;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static io.agroal.api.configuration.AgroalDataSourceConfiguration.DataSourceImplementation.AGROAL_POOLESS;
import static io.agroal.test.AgroalTestGroup.FUNCTIONAL;
import static io.agroal.test.MockDriver.deregisterMockDriver;
import static io.agroal.test.MockDriver.registerMockDriver;
import static java.text.MessageFormat.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Logger.getLogger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@Tag( FUNCTIONAL )
public class PoolessTests {

    private static final Logger logger = getLogger( PoolessTests.class.getName() );

    @BeforeAll
    public static void setupMockDriver() {
        registerMockDriver();
    }

    @AfterAll
    public static void teardown() {
        deregisterMockDriver();
    }

    // --- //

    @Test
    @DisplayName( "Pooless Test" )
    public void poolessTest() throws SQLException {
        int TIMEOUT_MS = 1000;

        AgroalDataSourceConfigurationSupplier configurationSupplier = new AgroalDataSourceConfigurationSupplier()
                .dataSourceImplementation( AGROAL_POOLESS )
                .metricsEnabled()
                .connectionPoolConfiguration( cp -> cp
                        .initialSize( 1 )
                        .minSize( 1 )
                        .maxSize( 2 )
                );

        CountDownLatch destroyLatch = new CountDownLatch( 1 );

        AgroalDataSourceListener listener = new AgroalDataSourceListener() {
            @Override
            public void onConnectionDestroy(Connection connection) {
                destroyLatch.countDown();
            }
        };

        try ( AgroalDataSource dataSource = AgroalDataSource.from( configurationSupplier, listener ) ) {
            try ( Connection c = dataSource.getConnection() ) {
                assertFalse( c.isClosed() );

                try ( Connection c1 = dataSource.getConnection() ) {
                    assertFalse( c1.isClosed() );

                    assertEquals( 2, dataSource.getMetrics().creationCount() );
                }
                logger.info( format( "Waiting for destruction of connection" ) );
                if ( !destroyLatch.await( TIMEOUT_MS, MILLISECONDS ) ) {
                    fail( format( "flushed connections not sent for destruction" ) );
                }

                // One connection flushed and another in use
                assertEquals( 1, dataSource.getMetrics().flushCount() );
                assertEquals( 0, dataSource.getMetrics().availableCount() );
            }

            // Assert min-size is zero
            assertEquals( 0, dataSource.getMetrics().activeCount() );
            assertEquals( 0, dataSource.getMetrics().availableCount() );
        } catch ( InterruptedException e ) {
            fail( "Test fail due to interrupt" );
        }
    }
}

// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.test.basic;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceListener;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.pool.util.PriorityScheduledExecutor;
import io.agroal.test.MockConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

import static io.agroal.test.AgroalTestGroup.FUNCTIONAL;
import static io.agroal.test.MockDriver.deregisterMockDriver;
import static io.agroal.test.MockDriver.registerMockDriver;
import static java.text.MessageFormat.format;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Logger.getLogger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@Tag( FUNCTIONAL )
public class LifetimeTests {

    private static final Logger logger = getLogger( LifetimeTests.class.getName() );

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
    @DisplayName( "Lifetime Test" )
    public void basicLifetimeTest() throws SQLException {
        int MIN_POOL_SIZE = 40, MAX_POOL_SIZE = 100, LIFETIME_MS = 1000;

        AgroalDataSourceConfigurationSupplier configurationSupplier = new AgroalDataSourceConfigurationSupplier()
                .metricsEnabled()
                .connectionPoolConfiguration( cp -> cp
                        .initialSize( MAX_POOL_SIZE )
                        .minSize( MIN_POOL_SIZE )
                        .maxSize( MAX_POOL_SIZE )
                        .lifetime( ofMillis( LIFETIME_MS ) )
                );

        CountDownLatch allLatch = new CountDownLatch( MAX_POOL_SIZE );
        CountDownLatch destroyLatch = new CountDownLatch( MAX_POOL_SIZE );
        LongAdder flushCount = new LongAdder();

        AgroalDataSourceListener listener = new LifetimeListener( allLatch, flushCount, destroyLatch );

        try ( AgroalDataSource dataSource = AgroalDataSource.from( configurationSupplier, listener ) ) {
            try {
                logger.info( format( "Awaiting creation of all the {0} connections on the pool", MAX_POOL_SIZE ) );
                if ( !allLatch.await( 3L * LIFETIME_MS, MILLISECONDS ) ) {
                    fail( format( "{0} connections not created for lifetime", allLatch.getCount() ) );
                }
                assertEquals( MAX_POOL_SIZE, dataSource.getMetrics().creationCount(), "Unexpected number of connections on the pool" );

                logger.info( format( "Waiting for removal of {0} connections ", MAX_POOL_SIZE ) );
                if ( !destroyLatch.await( 2L * LIFETIME_MS, MILLISECONDS ) ) {
                    fail( format( "{0} old connections not sent for destruction", destroyLatch.getCount() ) );
                }
                assertEquals( MAX_POOL_SIZE, flushCount.longValue(), "Unexpected number of old connections" );
            } catch ( InterruptedException e ) {
                fail( "Test fail due to interrupt" );
            }
        }
    }

    @Test
    @DisplayName( "Lifetime Test for connection in use" )
    public void lifetimeTest() throws SQLException {
        int LIFETIME_MS = 100;

        AgroalDataSourceConfigurationSupplier configurationSupplier = new AgroalDataSourceConfigurationSupplier()
                .metricsEnabled()
                .connectionPoolConfiguration( cp -> cp
                        .maxSize( 1 )
                        .lifetime( ofMillis( LIFETIME_MS ) )
                );

        CountDownLatch allLatch = new CountDownLatch( 1 );
        CountDownLatch destroyLatch = new CountDownLatch( 1 );
        LongAdder flushCount = new LongAdder();

        AgroalDataSourceListener listener = new LifetimeListener( allLatch, flushCount, destroyLatch );

        try ( AgroalDataSource dataSource = AgroalDataSource.from( configurationSupplier, listener ) ) {
            try ( Connection connection = dataSource.getConnection() ) {
                connection.getSchema();

                LockSupport.parkNanos( ofMillis( 2 * LIFETIME_MS ).toNanos() );

                assertEquals( 1, dataSource.getMetrics().creationCount(), "Unexpected number of connections on the pool" );
                assertEquals( 0, flushCount.longValue(), "Unexpected number of flushed connections" );
            }

            try {
                logger.info( format( "Waiting for removal of {0} connections ", 1 ) );
                if ( !destroyLatch.await( 2L * LIFETIME_MS, MILLISECONDS ) ) {
                    fail( format( "{0} old connections not sent for destruction", destroyLatch.getCount() ) );
                }
                assertEquals( 1, flushCount.longValue(), "Unexpected number of old connections" );
            } catch ( InterruptedException e ) {
                fail( "Test fail due to interrupt" );
            }
        }
    }

    @Test
    @DisplayName( "Lifetime Test for long lifetime" )
    public void longLifetimeTest() throws SQLException {
        int REAP_MS = 100, LIFETIME_MS = 1_000_000;

        AgroalDataSourceConfigurationSupplier configurationSupplier = new AgroalDataSourceConfigurationSupplier()
                .metricsEnabled()
                .connectionPoolConfiguration( cp -> cp
                        .maxSize( 1 )
                        .reapTimeout( ofMillis( REAP_MS ) )
                        .lifetime( ofMillis( LIFETIME_MS ) )
                );

        CountDownLatch allLatch = new CountDownLatch( 1 );
        CountDownLatch destroyLatch = new CountDownLatch( 1 );
        LongAdder flushCount = new LongAdder();

        AgroalDataSourceListener listener = new LifetimeListener( allLatch, flushCount, destroyLatch );

        try ( AgroalDataSource dataSource = AgroalDataSource.from( configurationSupplier, listener ) ) {
            // To check that the lifetime task has been canceled and references to the connection have been eliminated
            Field poolField = io.agroal.pool.DataSource.class.getDeclaredField( "connectionPool" );
            Field executorField = io.agroal.pool.ConnectionPool.class.getDeclaredField( "housekeepingExecutor" );
            poolField.setAccessible( true );
            executorField.setAccessible( true );
            PriorityScheduledExecutor executor = (PriorityScheduledExecutor) executorField.get( poolField.get( dataSource ) );

            try ( Connection connection = dataSource.getConnection() ) {
                connection.getSchema();

                assertEquals( 1, dataSource.getMetrics().creationCount(), "Unexpected number of connections on the pool" );
                assertEquals( 0, flushCount.longValue(), "Unexpected number of flushed connections" );
            }

            assertEquals( 2, executor.getQueue().size() ); // Lifetime and Reap tasks

            try {
                logger.info( format( "Waiting for removal of {0} connections ", 1 ) );
                if ( !destroyLatch.await( 2L * REAP_MS, MILLISECONDS ) ) {
                    fail( format( "{0} idle connections not sent for destruction", destroyLatch.getCount() ) );
                }
                assertEquals( 0, flushCount.longValue(), "Unexpected number of old connections" );
            } catch ( InterruptedException e ) {
                fail( "Test fail due to interrupt" );
            }

            assertEquals( 1, executor.getQueue().size() ); // Reap task only

        } catch ( NoSuchFieldException e ) {
            fail( "Test fail due to class changes", e );
        } catch ( IllegalAccessException e ) {
            fail( "Test fail due to issues", e );
        }
    }

    // --- //

    private static class LifetimeListener implements AgroalDataSourceListener {

        private final CountDownLatch allLatch;
        private final LongAdder flushCount;
        private final CountDownLatch destroyLatch;

        public LifetimeListener(CountDownLatch allLatch, LongAdder flushCount, CountDownLatch destroyLatch) {
            this.allLatch = allLatch;
            this.flushCount = flushCount;
            this.destroyLatch = destroyLatch;
        }

        @Override
        public void onConnectionCreation(Connection connection) {
            allLatch.countDown();
        }

        @Override
        public void onConnectionFlush(Connection connection) {
            flushCount.increment();
        }

        @Override
        public void beforeConnectionDestroy(Connection connection) {
            destroyLatch.countDown();
        }
    }

    // --- //

    public static class FakeSchemaConnection implements MockConnection {

        private boolean closed = false;

        @Override
        public String getSchema() throws SQLException {
            return FAKE_SCHEMA;
        }

        @Override
        public void close() throws SQLException {
            closed = true;
        }

        @Override
        public boolean isClosed() throws SQLException {
            return closed;
        }
    }
}

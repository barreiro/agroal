// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.pool;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceListener;
import io.agroal.api.AgroalDataSourceMetrics;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.transaction.TransactionIntegration;
import io.agroal.pool.util.ListenerHelper;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public final class PoolessDataSource implements AgroalDataSource {

    private static final long serialVersionUID = 6485903416474487024L;

    private final AgroalDataSourceConfiguration configuration;
    private final ConnectionPool connectionPool;

    public PoolessDataSource(AgroalDataSourceConfiguration dataSourceConfiguration, AgroalDataSourceListener... listeners) {
        configuration = dataSourceConfiguration;
        connectionPool = new ConnectionPool( new PoolessConnectionPoolConfiguration( configuration.connectionPoolConfiguration(), listeners ), listeners );
        dataSourceConfiguration.registerMetricsEnabledListener( connectionPool );
        connectionPool.onMetricsEnabled( dataSourceConfiguration.metricsEnabled() );
        connectionPool.init();
    }

    // --- //

    private static class PoolessConnectionPoolConfiguration  implements AgroalConnectionPoolConfiguration {

        private AgroalConnectionPoolConfiguration wrappedConfiguration;

        private PoolessConnectionPoolConfiguration(AgroalConnectionPoolConfiguration wrappedConfiguration, AgroalDataSourceListener... listeners) {
            if ( wrappedConfiguration.minSize() != 0 ) {
                ListenerHelper.fireOnWarning( listeners, "min-size not supported in pooless mode" );
            }


            this.wrappedConfiguration = wrappedConfiguration;
        }

        @Override
        public AgroalConnectionFactoryConfiguration connectionFactoryConfiguration() {
            return wrappedConfiguration.connectionFactoryConfiguration();
        }

        @Override
        public ConnectionValidator connectionValidator() {
            return wrappedConfiguration.connectionValidator();
        }

        @Override
        public ExceptionSorter exceptionSorter() {
            return wrappedConfiguration.exceptionSorter();
        }

        @Override
        public TransactionIntegration transactionIntegration() {
            return wrappedConfiguration.transactionIntegration();
        }

        @Override
        public Duration idleValidationTimeout() {
            return wrappedConfiguration.idleValidationTimeout();
        }

        @Override
        public Duration leakTimeout() {
            return wrappedConfiguration.leakTimeout();
        }

        @Override
        public Duration validationTimeout() {
            return wrappedConfiguration.validationTimeout();
        }

        @Override
        public Duration reapTimeout() {
            return wrappedConfiguration.reapTimeout();
        }

        @Override
        public Duration maxLifetime() {
            return wrappedConfiguration.maxLifetime();
        }

        @Override
        public boolean flushOnClose() {
            return true;
        }

        @Override
        public int initialSize() {
            return wrappedConfiguration.initialSize();
        }

        @Override
        public int minSize() {
            return 0;
        }

        @Override
        public void setMinSize(int size) {
            throw new UnsupportedOperationException( "Pooless datasource does not support min-size" );
        }

        @Override
        public int maxSize() {
            return wrappedConfiguration.maxSize();
        }

        @Override
        public void setMaxSize(int size) {
            wrappedConfiguration.setMaxSize( size );
        }

        @Override
        public Duration acquisitionTimeout() {
            return wrappedConfiguration.acquisitionTimeout();
        }

        @Override
        public void setAcquisitionTimeout(Duration timeout) {
            wrappedConfiguration.setAcquisitionTimeout( timeout );
        }
    }

    // --- AgroalDataSource methods //

    @Override
    public AgroalDataSourceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public AgroalDataSourceMetrics getMetrics() {
        return connectionPool.getMetrics();
    }

    @Override
    public void flush(FlushMode mode) {
        connectionPool.flush(mode);
    }

    @Override
    public void close() {
        connectionPool.close();
    }

    // --- DataSource methods //

    @Override
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException( "username and password combination invalid on a pooled data source!" );
    }

    // --- Wrapper methods //

    @Override
    public <T> T unwrap(Class<T> target) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> target) throws SQLException {
        return false;
    }

    // --- CommonDataSource methods //

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        // no-op
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        // no-op
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException( "Not Supported" );
    }
}

// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.pool.util;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Disguises a non-XA connection as an XAConnection. Useful to keep the same logic for pooling both XA and non-XA connections
 *
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class XAConnectionAdaptor implements XAConnection {

    private final Connection connection;

    private final List<ConnectionEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public XAConnectionAdaptor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        ConnectionEvent event = new ConnectionEvent( this );
        for ( ConnectionEventListener listener : eventListeners ) {
            listener.connectionClosed( event );
        }
        connection.close();
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.add( listener );
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.remove( listener );
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        throw new IllegalArgumentException( "no StatementEventListener on non-XA connection" );
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        throw new IllegalArgumentException( "no StatementEventListener on non-XA connection" );
    }

    @Override
    public XAResource getXAResource() throws SQLException {
        return null;
    }
}

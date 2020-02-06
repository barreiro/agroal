// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.api;

import java.sql.Connection;

/**
 * Callback interface for pool actions.
 * <p>
 * These differ from the Listener in a few different ways:
 * They do not have access to the raw Connection
 * The invoke order is dependent on the operation (incoming / outgoing)
 * Consistent with the transaction.
 *
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public interface AgroalPoolInterceptor {

    /**
     * This callback is invoked when a connection is successfully acquired.
     * When in a transactional environment this is invoked only once for multiple acquire calls within the same transaction, before the connection is associated.
     */
    default void onConnectionAcquire(Connection connection) {
    }

    /**
     * This callback is invoked before a connection is returned to the pool.
     * When in a transactional environment this is invoked only once for each transaction, after commit / rollback.
     * This callback runs after reset, allowing connections to be in the pool in a different state than the described on the configuration.
     */
    default void onConnectionReturn(Connection connection) {
    }
}

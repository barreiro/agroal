// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.pool;

import io.agroal.api.AgroalDataSourceListener;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public class AutoReapDataSource extends DataSource {

    private static final long serialVersionUID = 8752018417278196778L;

    public AutoReapDataSource(AgroalDataSourceConfiguration dataSourceConfiguration, AgroalDataSourceListener... listeners) {
        super( dataSourceConfiguration, new ConnectionPool( dataSourceConfiguration.connectionPoolConfiguration(), false, listeners ) );
    }
}

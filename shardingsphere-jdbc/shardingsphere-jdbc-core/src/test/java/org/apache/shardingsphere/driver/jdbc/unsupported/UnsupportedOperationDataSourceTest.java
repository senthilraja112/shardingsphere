/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.driver.jdbc.unsupported;

import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.infra.mode.config.ModeConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;

public final class UnsupportedOperationDataSourceTest {
    
    private ShardingSphereDataSource shardingSphereDataSource;
    
    @Before
    public void setUp() throws SQLException {
        shardingSphereDataSource = new ShardingSphereDataSource(
                DefaultSchema.LOGIC_NAME, new ModeConfiguration("Memory", null, true), Collections.emptyMap(), Collections.emptyList(), new Properties());
    }
    
    @Test(expected = SQLFeatureNotSupportedException.class)
    public void assertGetLoginTimeout() throws SQLException {
        shardingSphereDataSource.getLoginTimeout();
    }
    
    @Test(expected = SQLFeatureNotSupportedException.class)
    public void assertSetLoginTimeout() throws SQLException {
        shardingSphereDataSource.setLoginTimeout(0);
    }
}

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

package org.apache.shardingsphere.driver.governance.internal.datasource;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.driver.governance.api.yaml.YamlGovernanceShardingSphereDataSourceFactory;
import org.apache.shardingsphere.governance.context.metadata.GovernanceMetaDataContexts;
import org.apache.shardingsphere.governance.core.registry.config.event.datasource.DataSourceChangedEvent;
import org.apache.shardingsphere.governance.core.registry.config.event.props.PropertiesChangedEvent;
import org.apache.shardingsphere.governance.core.registry.config.event.rule.RuleConfigurationsChangedEvent;
import org.apache.shardingsphere.governance.core.registry.state.event.DisabledStateChangedEvent;
import org.apache.shardingsphere.governance.core.schema.GovernanceSchema;
import org.apache.shardingsphere.governance.repository.api.config.RegistryCenterConfiguration;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.datasource.DataSourceConfiguration;
import org.apache.shardingsphere.infra.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.database.DefaultSchema;
import org.apache.shardingsphere.infra.mode.config.ModeConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.rule.ReadwriteSplittingDataSourceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class GovernanceShardingSphereDataSourceTest {
    
    private static GovernanceMetaDataContexts metaDataContexts;
    
    @BeforeClass
    public static void setUp() throws SQLException, IOException, URISyntaxException {
        metaDataContexts = (GovernanceMetaDataContexts) getGovernanceShardingSphereDataSource().getMetaDataContexts();
    }
    
    private static GovernanceShardingSphereDataSource getGovernanceShardingSphereDataSource() throws IOException, SQLException, URISyntaxException {
        File yamlFile = new File(Objects.requireNonNull(GovernanceShardingSphereDataSourceTest.class.getResource("/yaml/unit/sharding.yaml")).toURI());
        return (GovernanceShardingSphereDataSource) YamlGovernanceShardingSphereDataSourceFactory.createDataSource(yamlFile);
    }
    
    private static ModeConfiguration getModeConfiguration() {
        return new ModeConfiguration("Cluster", getRegistryCenterConfiguration(), true);
    }
    
    private static RegistryCenterConfiguration getRegistryCenterConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("overwrite", "true");
        return new RegistryCenterConfiguration("GOV_TEST", "test_name", "localhost:3181", properties);
    }
    
    @Test
    public void assertInitializeGovernanceShardingSphereDataSource() throws SQLException {
        assertThat(new GovernanceShardingSphereDataSource(DefaultSchema.LOGIC_NAME, getModeConfiguration()).getConnection(), instanceOf(Connection.class));
    }
    
    @Test
    public void assertRenewRules() throws SQLException {
        metaDataContexts.renew(new RuleConfigurationsChangedEvent(DefaultSchema.LOGIC_NAME, Arrays.asList(getShardingRuleConfiguration(), getReadwriteSplittingRuleConfiguration())));
        Optional<ShardingRule> rule = metaDataContexts.getMetaData(DefaultSchema.LOGIC_NAME).getRuleMetaData().getRules().stream()
                .filter(each -> each instanceof ShardingRule).map(each -> (ShardingRule) each).findFirst();
        assertTrue(rule.isPresent());
        assertThat(rule.get().getTableRules().size(), is(1));
    }
    
    private ShardingRuleConfiguration getShardingRuleConfiguration() {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        result.getTables().add(new ShardingTableRuleConfiguration("logic_table", "pr_ds.table_${0..1}"));
        return result;
    }
    
    private ReadwriteSplittingRuleConfiguration getReadwriteSplittingRuleConfiguration() {
        ReadwriteSplittingDataSourceRuleConfiguration dataSourceConfig
                = new ReadwriteSplittingDataSourceRuleConfiguration("pr_ds", "", "write_ds", Collections.singletonList("read_ds"), "roundRobin");
        return new ReadwriteSplittingRuleConfiguration(
                Collections.singleton(dataSourceConfig), ImmutableMap.of("roundRobin", new ShardingSphereAlgorithmConfiguration("ROUND_ROBIN", new Properties())));
    }
    
    @Test
    public void assertRenewDataSource() throws SQLException {
        metaDataContexts.renew(new DataSourceChangedEvent(DefaultSchema.LOGIC_NAME, getDataSourceConfigurations()));
        assertThat(metaDataContexts.getMetaData(DefaultSchema.LOGIC_NAME).getResource().getDataSources().size(), is(3));
    }
    
    private Map<String, DataSourceConfiguration> getDataSourceConfigurations() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Map<String, DataSourceConfiguration> result = new LinkedHashMap<>(3, 1);
        result.put("write_ds", DataSourceConfiguration.getDataSourceConfiguration(dataSource));
        result.put("read_ds", DataSourceConfiguration.getDataSourceConfiguration(dataSource));
        result.put("ds_0", DataSourceConfiguration.getDataSourceConfiguration(dataSource));
        return result;
    }
    
    @Test
    public void assertRenewProperties() {
        metaDataContexts.renew(createPropertiesChangedEvent());
        assertThat(metaDataContexts.getProps().getProps().getProperty(ConfigurationPropertyKey.SQL_SHOW.getKey()), is(Boolean.TRUE.toString()));
    }
    
    private PropertiesChangedEvent createPropertiesChangedEvent() {
        Properties props = new Properties();
        props.setProperty(ConfigurationPropertyKey.SQL_SHOW.getKey(), Boolean.TRUE.toString());
        return new PropertiesChangedEvent(props);
    }
    
    @Test
    public void assertRenewDisabledState() {
        metaDataContexts.renew(new DisabledStateChangedEvent(new GovernanceSchema("logic_db.replica_ds"), true));
    }
}

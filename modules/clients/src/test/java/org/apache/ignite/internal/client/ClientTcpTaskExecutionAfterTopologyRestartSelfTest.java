/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.client;

import java.util.Collections;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 * Ensures
 */
public class ClientTcpTaskExecutionAfterTopologyRestartSelfTest extends GridCommonAbstractTest {
    /** Port. */
    private static final int PORT = 11211;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg =  super.getConfiguration(igniteInstanceName);

        cfg.setLocalHost("127.0.0.1");

        assert cfg.getConnectorConfiguration() == null;

        ConnectorConfiguration clientCfg = new ConnectorConfiguration();

        clientCfg.setPort(PORT);

        cfg.setConnectorConfiguration(clientCfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testTaskAfterRestart() throws Exception {
        startGrids(1);

        GridClientConfiguration cfg = new GridClientConfiguration();

        cfg.setProtocol(GridClientProtocol.TCP);
        cfg.setServers(Collections.singleton("127.0.0.1:" + PORT));

        GridClient cli = GridClientFactory.start(cfg);

        cli.compute().execute(ClientTcpTask.class.getName(), Collections.singletonList("arg"));

        stopAllGrids();

        startGrid();

        cli.compute().execute(ClientTcpTask.class.getName(), Collections.singletonList("arg"));
    }
}

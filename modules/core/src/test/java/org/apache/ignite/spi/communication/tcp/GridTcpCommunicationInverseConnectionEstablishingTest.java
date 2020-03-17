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

package org.apache.ignite.spi.communication.tcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.EnvironmentType;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.util.nio.GridCommunicationClient;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;

/**
 *
 */
public class GridTcpCommunicationInverseConnectionEstablishingTest extends GridCommonAbstractTest {
    /** */
    private static final IgnitePredicate<ClusterNode> CLIENT_PRED = c -> c.isClient();

    /** */
    private static final String UNREACHABLE_IP = "172.31.30.132";

    /** */
    private static final String UNRESOLVED_HOST = "unresolvedHost";

    /** */
    private static final String CACHE_NAME = "cache-0";

    /** */
    private boolean clientMode;

    /** */
    private EnvironmentType envType = EnvironmentType.STAND_ALONE;

    /** */
    private CacheConfiguration ccfg;

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setFailureDetectionTimeout(8_000);

        cfg.setActiveOnStart(true);

        cfg.setCommunicationSpi(new TestCommunicationSpi());

        if (ccfg != null) {
            cfg.setCacheConfiguration(ccfg);

            ccfg = null;
        }

        if (clientMode) {
            cfg.setEnvironmentType(envType);
            cfg.setClientMode(true);
        }

        return cfg;
    }

    /**
     * Verifies that server successfully connects to "unreachable" client with {@code EnvironmentType.VIRTUALIZED} hint.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testUnreachableClientInVirtualizedEnvironment() throws Exception {
        executeCacheTestWithUnreachableClient(EnvironmentType.VIRTUALIZED);
    }

    /**
     * Verifies that server successfully connects to "unreachable" client with {@code EnvironmentType.STAND_ALONE} hint.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testUnreachableClientInStandAloneEnvironment() throws Exception {
        executeCacheTestWithUnreachableClient(EnvironmentType.STAND_ALONE);
    }

    /**
     * Executes cache test with "unreachable" client.
     *
     * @param envType {@code EnvironmentType} hint.
     * @throws Exception If failed.
     */
    private void executeCacheTestWithUnreachableClient(EnvironmentType envType) throws Exception {
        int srvs = 3;

        for (int i = 0; i < srvs; i++) {
            ccfg = cacheConfiguration(CACHE_NAME, ATOMIC);

            startGrid(i);
        }

        clientMode = true;
        this.envType = envType;

        startGrid(srvs);

        putAndCheckKey();
    }

    /**
     * @param name Cache name.
     * @param atomicityMode Atomicity mode.
     * @return Cache configuration.
     */
    protected final CacheConfiguration cacheConfiguration(String name, CacheAtomicityMode atomicityMode) {
        CacheConfiguration ccfg = new CacheConfiguration(name);

        ccfg.setWriteSynchronizationMode(FULL_SYNC);
        ccfg.setAtomicityMode(atomicityMode);
        ccfg.setBackups(1);

        return ccfg;
    }

    /**
     * Puts a key to a server that is backup for the key and doesn't have an open communication connection to client.
     * This forces the server to establish a connection to "unreachable" client.
     */
    private void putAndCheckKey() {
        int key = 0;
        IgniteEx srv2 = grid(2);

        for (int i = 0; i < 1_000; i++) {
            if (srv2.affinity(CACHE_NAME).isBackup(srv2.localNode(), i)) {
                key = i;

                break;
            }
        }

        IgniteEx cl0 = grid(3);

        IgniteCache<Object, Object> cache = cl0.cache(CACHE_NAME);

        cache.put(key, key);
        assertEquals(key, cache.get(key));
    }

    /** */
    private static class TestCommunicationSpi extends TcpCommunicationSpi {
        /** {@inheritDoc} */
        @Override protected GridCommunicationClient createTcpClient(ClusterNode node, int connIdx) throws IgniteCheckedException {
            if (CLIENT_PRED.apply(node)) {
                Map<String, Object> attrs = new HashMap<>(node.attributes());

                attrs.put(createAttributeName(ATTR_ADDRS), Collections.singleton(UNREACHABLE_IP));
                attrs.put(createAttributeName(ATTR_PORT), 47200);
                attrs.put(createAttributeName(ATTR_EXT_ADDRS), Collections.emptyList());
                attrs.put(createAttributeName(ATTR_HOST_NAMES), Collections.emptyList());

                ((TcpDiscoveryNode)(node)).setAttributes(attrs);
            }

            return super.createTcpClient(node, connIdx);
        }

        /**
         * @param name Name.
         */
        private String createAttributeName(String name) {
            return getClass().getSimpleName() + '.' + name;
        }
    }
}

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

package org.apache.ignite.internal.processors.cache;

import java.io.Serializable;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static org.apache.ignite.cache.CacheMode.REPLICATED;
import static org.apache.ignite.cache.CacheRebalanceMode.SYNC;
import static org.apache.ignite.internal.IgniteNodeAttributes.ATTR_DAEMON;

/**
 *
 */
public class IgniteDaemonNodeMarshallerCacheTest extends GridCommonAbstractTest {
    /** */
    private boolean daemon;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setDaemon(daemon);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testMarshalOnDaemonNode1() throws Exception {
        marshalOnDaemonNode(true);
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testMarshalOnDaemonNode2() throws Exception {
        marshalOnDaemonNode(false);
    }

    /**
     * @param startFirst If {@code true} daemon node is started first.
     * @throws Exception If failed.
     */
    private void marshalOnDaemonNode(boolean startFirst) throws Exception {
        int nodeIdx = 0;

        if (!startFirst) {
            Ignite ignite1 = startGrid(nodeIdx++);

            assertFalse("true".equals(ignite1.cluster().localNode().attribute(ATTR_DAEMON)));
        }

        daemon = true;

        Ignite daemonNode = startGrid(nodeIdx++);

        assertTrue(daemonNode.cluster().localNode().isDaemon());
        assertEquals("true", daemonNode.cluster().localNode().attribute(ATTR_DAEMON));

        daemon = false;

        if (startFirst) {
            Ignite ignite1 = startGrid(nodeIdx++);

            assertFalse("true".equals(ignite1.cluster().localNode().attribute(ATTR_DAEMON)));
        }

        awaitPartitionMapExchange();

        TestClass1 res1 = daemonNode.compute(daemonNode.cluster().forRemotes()).call(new TestCallable1());

        assertNotNull(res1);
        assertEquals(111, res1.val);

        Ignite ignite2 = startGrid(nodeIdx);

        CacheConfiguration<Object, Object> ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

        ccfg.setRebalanceMode(SYNC);
        ccfg.setCacheMode(REPLICATED);

        IgniteCache<Object, Object> cache = ignite2.getOrCreateCache(ccfg);

        awaitPartitionMapExchange();

        cache.put(1, new TestClass1(1));
        cache.put(2, new TestClass2(2));

        TestClass2 res2 = daemonNode.compute(daemonNode.cluster().forRemotes()).call(new TestCallable2());

        assertNotNull(res2);
        assertEquals(222, res2.val);
    }

    /**
     *
     */
    private static class TestCallable1 implements IgniteCallable<TestClass1> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public TestClass1 call() throws Exception {
            assertFalse("true".equals(ignite.cluster().localNode().attribute(ATTR_DAEMON)));

            return new TestClass1(111);
        }
    }

    /**
     *
     */
    private static class TestCallable2 implements IgniteCallable<TestClass2> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public TestClass2 call() throws Exception {
            assertFalse("true".equals(ignite.cluster().localNode().attribute(ATTR_DAEMON)));

            return new TestClass2(222);
        }
    }

    /**
     *
     */
    private static class TestClass1 implements Serializable {
        /** */
        public int val;

        /**
         * @param val Value.
         */
        public TestClass1(int val) {
            this.val = val;
        }
    }

    /**
     *
     */
    private static class TestClass2 implements Serializable {
        /** */
        public int val;

        /**
         * @param val Value.
         */
        public TestClass2(int val) {
            this.val = val;
        }
    }
}

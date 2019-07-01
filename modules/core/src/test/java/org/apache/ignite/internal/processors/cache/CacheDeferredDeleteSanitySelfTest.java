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

import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;
import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL_SNAPSHOT;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.cache.CacheMode.REPLICATED;

/**
 * Sanity tests of deferred delete for different cache configurations.
 */
public class CacheDeferredDeleteSanitySelfTest extends GridCommonAbstractTest {
    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGrid(0);
    }

    /**
     * @throws Exception If fails.
     */
    @Test
    public void testDeferredDelete() throws Exception {
        testDeferredDelete(PARTITIONED, ATOMIC, false, true);
        testDeferredDelete(PARTITIONED, TRANSACTIONAL, false, true);

        testDeferredDelete(REPLICATED, ATOMIC, false, true);
        testDeferredDelete(REPLICATED, TRANSACTIONAL, false, true);

        // Near
        testDeferredDelete(PARTITIONED, ATOMIC, true, true);
        testDeferredDelete(PARTITIONED, TRANSACTIONAL, true, false);

        testDeferredDelete(REPLICATED, ATOMIC, true, true);
        testDeferredDelete(REPLICATED, TRANSACTIONAL, true, true);
    }

    /**
     * @throws Exception If fails.
     */
    @Test
    public void testDeferredDeleteMvcc() throws Exception {
        testDeferredDelete(PARTITIONED, TRANSACTIONAL_SNAPSHOT, false, true);
        testDeferredDelete(REPLICATED, TRANSACTIONAL_SNAPSHOT, false, true);
    }

    /**
     * @throws Exception If fails.
     */
    @Ignore("https://issues.apache.org/jira/browse/IGNITE-7187")
    @Test
    public void testDeferredDeleteMvccNear() throws Exception {
        testDeferredDelete(PARTITIONED, TRANSACTIONAL_SNAPSHOT, true, false);
        testDeferredDelete(REPLICATED, TRANSACTIONAL_SNAPSHOT, true, true);
    }

    /**
     * @param mode Mode.
     * @param atomicityMode Atomicity mode.
     * @param near Near cache enabled.
     * @param expVal Expected deferred delete value.
     */
    @SuppressWarnings("unchecked")
    private void testDeferredDelete(CacheMode mode, CacheAtomicityMode atomicityMode, boolean near, boolean expVal) {
        CacheConfiguration ccfg = new CacheConfiguration(DEFAULT_CACHE_NAME)
            .setCacheMode(mode)
            .setAtomicityMode(atomicityMode);

        if (near)
            ccfg.setNearConfiguration(new NearCacheConfiguration());

        IgniteCache cache = null;

        try {
            cache = grid(0).getOrCreateCache(ccfg);

            assertEquals(expVal, ((IgniteCacheProxy)grid(0).cache(DEFAULT_CACHE_NAME)).context().deferredDelete());
        }
        finally {
            if (cache != null)
                cache.destroy();
        }
    }
}

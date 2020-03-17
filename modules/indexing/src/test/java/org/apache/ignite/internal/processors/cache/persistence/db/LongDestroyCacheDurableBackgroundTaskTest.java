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

package org.apache.ignite.internal.processors.cache.persistence.db;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.pendingtask.DurableBackgroundTask;
import org.apache.ignite.internal.visor.VisorTaskArgument;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceJobResult;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTask;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTaskArg;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTaskResult;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;
import static org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager.IGNITE_PDS_SKIP_CHECKPOINT_ON_NODE_STOP;

/**
 * Class for testing to clear contents of cache(after destroy) through
 * {@link DurableBackgroundTask}.
 */
@WithSystemProperty(key = IGNITE_PDS_SKIP_CHECKPOINT_ON_NODE_STOP, value = "true")
public class LongDestroyCacheDurableBackgroundTaskTest extends GridCommonAbstractTest {
    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        return super.getConfiguration(igniteInstanceName)
            .setDataStorageConfiguration(
                new DataStorageConfiguration().setDefaultDataRegionConfiguration(
                    new DataRegionConfiguration()
                        .setPersistenceEnabled(true)
                        .setMaxSize(50 * 1024 * 1024)
                )
            );
    }

    /**
     * First test.
     *
     * @throws Exception If failed.
     */
    @Test
    public void test0() throws Exception {
        IgniteEx crd = startGrids(1);

        String cacheName = DEFAULT_CACHE_NAME;
        String grpName = "group";

        CacheConfiguration<Object, Object> cacheCfg0 = new CacheConfiguration<>(cacheName).setGroupName(grpName);
        CacheConfiguration<Object, Object> cacheCfg1 = new CacheConfiguration<>(cacheName + "1").setGroupName(grpName);

        createAndPopulateCaches(crd, cacheCfg0, cacheCfg1);

        crd.destroyCache(cacheName);
        assertNull(crd.cachex(cacheName));

        int stopNodeIdx = 0;
        stopGrid(stopNodeIdx);
        crd = startGrid(stopNodeIdx);

        assertNull(crd.cachex(cacheName));

        VisorFindAndDeleteGarbageInPersistenceJobResult findGarbageRes = findGarbage(crd, grpName);
        assertTrue(findGarbageRes.hasGarbage());

        // TODO: 01.03.2020 wait clear through DurableBackgroundTask

        findGarbageRes = findGarbage(crd, grpName);
        assertFalse(findGarbageRes.hasGarbage());
    }

    /**
     * Create and populate caches.
     *
     * @param node      Node.
     * @param cacheCfgs Cache configurations.
     */
    private void createAndPopulateCaches(IgniteEx node, CacheConfiguration<Object, Object>... cacheCfgs) {
        requireNonNull(node);
        requireNonNull(cacheCfgs);

        node.cluster().active(true);

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (CacheConfiguration<Object, Object> cacheCfg : cacheCfgs) {
            IgniteCache<Object, Object> cache = node.getOrCreateCache(cacheCfg);

            try (IgniteDataStreamer<Object, Object> streamer = node.dataStreamer(cache.getName())) {
                range(0, 5_000).boxed().forEach(i -> streamer.addData(i, rand.nextLong()));
                streamer.flush();
            }
        }
    }

    /**
     * Search for garbage in an existing cache group.
     *
     * @param node    Node.
     * @param grpName Cache group name.
     * @return Garbage search result.
     */
    private VisorFindAndDeleteGarbageInPersistenceJobResult findGarbage(IgniteEx node, String grpName) {
        requireNonNull(node);
        requireNonNull(grpName);

        UUID nodeId = node.localNode().id();

        VisorFindAndDeleteGarbageInPersistenceTaskResult findGarbageTaskRes = node.compute().execute(
            VisorFindAndDeleteGarbageInPersistenceTask.class,
            new VisorTaskArgument<>(
                nodeId,
                new VisorFindAndDeleteGarbageInPersistenceTaskArg(singleton(grpName), false, null),
                true
            )
        );

        assertTrue(findGarbageTaskRes.exceptions().isEmpty());
        return findGarbageTaskRes.result().get(nodeId);
    }
}

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
package org.apache.ignite.internal.processors.cache.persistence;

import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManager;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.pendingtask.DurableBackgroundTask;

public class RemoveCacheDataDurableBackgroundTask implements DurableBackgroundTask {
    private final int cacheId;

    private final int groupId;

    public RemoveCacheDataDurableBackgroundTask(int cacheId, int groupId) {
        this.cacheId = cacheId;
        this.groupId = groupId;
    }

    @Override public String shortName() {
        return null;
    }

    @Override public void execute(GridKernalContext ctx) {
        /*CacheGroupContext grp = ctx.cache().cacheGroup(groupId);

        IgniteCacheOffheapManager.CacheDataStore store = new RemovingCacheDataStore();

        store.clear(cacheId);
    }

    private class RemovingCacheDataStore extends GridCacheOffheapManager.GridCacheDataStore {
        public RemovingCacheDataStore() {
            super();
        }*/
    }
}

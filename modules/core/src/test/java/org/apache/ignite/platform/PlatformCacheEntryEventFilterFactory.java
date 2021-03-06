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

package org.apache.ignite.platform;

import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheEntryEventSerializableFilter;
import org.apache.ignite.resources.IgniteInstanceResource;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import java.io.Serializable;

/**
 * Test filter factory
 */
public class PlatformCacheEntryEventFilterFactory implements Serializable,
    PlatformJavaObjectFactory<CacheEntryEventSerializableFilter> {
    /** Property to be set from platform. */
    private String startsWith = "-";

    /** Injected instance. */
    @IgniteInstanceResource
    private Ignite ignite;

    /** {@inheritDoc} */
    @Override public CacheEntryEventSerializableFilter create() {
        assert ignite != null;

        return new CacheEntryEventSerializableFilter() {
            @Override public boolean evaluate(CacheEntryEvent event) throws CacheEntryListenerException {
                Object value = event.getValue();

                if (value instanceof String)
                    return ((String)value).startsWith(startsWith);

                assert value instanceof BinaryObject;

                return ((String)((BinaryObject)value).field("String")).startsWith(startsWith);
            }
        };
    }
}

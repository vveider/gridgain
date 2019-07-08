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

package org.apache.ignite.internal.processors.query.h2;

/**
 * Memory tracker.
 */
public abstract class H2MemoryTracker implements AutoCloseable {
    /**
     * Check allocated size is less than query memory pool threshold.
     *
     * @param size Allocated size in bytes.
     * @return {@code True} if memory limit is not exceeded. {@code False} otherwise.
     */
    public abstract boolean reserved(long size);

    /**
     * Memory release callback.
     *
     * @param size Released memory size in bytes.
     */
    public abstract void released(long size);

    /**
     * @return Reserved memory size.
     */
    public abstract long memoryReserved();

    /**
     * @return Max memory limit.
     */
    public abstract long memoryLimit();
}

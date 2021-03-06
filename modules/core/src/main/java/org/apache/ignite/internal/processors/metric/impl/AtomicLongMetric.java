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

package org.apache.ignite.internal.processors.metric.impl;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.apache.ignite.internal.processors.metric.AbstractMetric;
import org.apache.ignite.spi.metric.LongMetric;
import org.jetbrains.annotations.Nullable;

/**
 * Long metric implementation.
 */
public class AtomicLongMetric extends AbstractMetric implements LongMetric {
    /** Field updater. */
    static final AtomicLongFieldUpdater<AtomicLongMetric> updater =
        AtomicLongFieldUpdater.newUpdater(AtomicLongMetric.class, "val");

    /** Field value. */
    private volatile long val;

    /**
     * @param name Name.
     * @param desc Description.
     */
    public AtomicLongMetric(String name, @Nullable String desc) {
        super(name, desc);
    }

    /**
     * Adds x to the metric.
     *
     * @param x Value to be added.
     */
    public void add(long x) {
        updater.getAndAdd(this, x);
    }

    /** Adds 1 to the metric. */
    public void increment() {
        add(1);
    }

    /** Adds -1 to the metric. */
    public void decrement() {
        add(-1);
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        updater.set(this, 0);
    }

    /** {@inheritDoc} */
    @Override public long value() {
        return val;
    }

    /**
     * Sets value.
     *
     * @param val Value.
     */
    public void value(long val) {
        this.val = val;
    }
}

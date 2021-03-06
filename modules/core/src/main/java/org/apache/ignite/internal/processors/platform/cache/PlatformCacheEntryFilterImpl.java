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

package org.apache.ignite.internal.processors.platform.cache;

import org.apache.ignite.Ignite;
import org.apache.ignite.internal.binary.BinaryRawWriterEx;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.platform.PlatformAbstractPredicate;
import org.apache.ignite.internal.processors.platform.PlatformContext;
import org.apache.ignite.internal.processors.platform.memory.PlatformMemory;
import org.apache.ignite.internal.processors.platform.memory.PlatformOutputStream;
import org.apache.ignite.internal.processors.platform.utils.PlatformUtils;
import org.apache.ignite.resources.IgniteInstanceResource;

/**
 * Interop filter. Delegates apply to native platform.
 */
public class PlatformCacheEntryFilterImpl extends PlatformAbstractPredicate implements PlatformCacheEntryFilter {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private transient boolean platfromNearEnabled;

    /**
     * {@link java.io.Externalizable} support.
     */
    public PlatformCacheEntryFilterImpl() {
        //noinspection UnnecessaryCallToSuper
        super();
    }

    /**
     * Constructor.
     *
     * @param pred .Net binary predicate.
     * @param ptr Pointer to predicate in the native platform.
     * @param ctx Kernal context.
     */
    public PlatformCacheEntryFilterImpl(Object pred, long ptr, PlatformContext ctx) {
        super(pred, ptr, ctx);

        assert pred != null;
    }

    /** {@inheritDoc} */
    @Override public boolean apply(Object k, Object v) {
        assert ptr != 0;

        try (PlatformMemory mem = ctx.memory().allocate()) {
            PlatformOutputStream out = mem.output();

            BinaryRawWriterEx writer = ctx.writer(out);

            writer.writeLong(ptr);

            writer.writeObject(k);

            try {
                if (platfromNearEnabled) {
                    // Normally, platform near cache already has the value.
                    // Put value to platform thread local so it can be requested when missing.
                    writer.writeBoolean(false);
                    ctx.kernalContext().platform().setThreadLocal(v);
                } else {
                    writer.writeBoolean(true);
                    writer.writeObject(v);
                }

                out.synchronize();

                return ctx.gateway().cacheEntryFilterApply(mem.pointer()) != 0;
            }
            finally {
                if (platfromNearEnabled) {
                    ctx.kernalContext().platform().setThreadLocal(null);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void onClose() {
        if (ptr == 0)
            return;

        assert ctx != null;

        ctx.gateway().cacheEntryFilterDestroy(ptr);

        ptr = 0;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    @Override public void cacheContext(GridCacheContext cctx) {
        // This initializer is called for Scan Query filters, which can use Platform Near cache.
        if (ptr != 0)
            return;

        ctx = cctx.kernalContext().platform().context();

        platfromNearEnabled = cctx.config().getPlatformNearConfiguration() != null &&
                ctx.isNativeNearCacheSupported();

        init(platfromNearEnabled ? cctx.cacheId() : null);
    }

    /**
     * @param ignite Ignite instance.
     */
    @IgniteInstanceResource
    public void setIgniteInstance(Ignite ignite) {
        // This initializer is called for Cache Store filters, which can not use Platform Near Cache.
        if (ptr != 0)
            return;

        ctx = PlatformUtils.platformContext(ignite);

        init(null);
    }

    /**
     * Initializes this instance.
     *
     * @param cacheId Optional cache id for Platform Near cache.
     */
    private void init(Integer cacheId) {
        try (PlatformMemory mem = ctx.memory().allocate()) {
            PlatformOutputStream out = mem.output();

            BinaryRawWriterEx writer = ctx.writer(out);

            writer.writeObject(pred);

            if (cacheId != null) {
                writer.writeBoolean(true);
                writer.writeInt(cacheId);
            } else {
                writer.writeBoolean(false);
            }

            out.synchronize();

            ptr = ctx.gateway().cacheEntryFilterCreate(mem.pointer());
        }
    }
}

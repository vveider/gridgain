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

package org.apache.ignite.internal.processors.cache.distributed.dht.preloader;

import java.nio.ByteBuffer;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;

/**
 * Supply message with supplier error transfer support.
 */
public class GridDhtPartitionSupplyMessageV3 extends GridDhtPartitionSupplyMessageV2 {
    /** Message type. */
    public static final short DIRECT_TYPE = 175;

    /** */
    private static final long serialVersionUID = 0L;

    /** Flag indicates that all historical partitions were processed. */
    private boolean allHistPartsDone;

    /**
     * Default constructor.
     */
    public GridDhtPartitionSupplyMessageV3() {
    }

    /**
     * @param rebalanceId Rebalance id.
     * @param grpId Group id.
     * @param topVer Topology version.
     * @param addDepInfo Add dep info.
     * @param err Supply process error.
     * @param allHistPartsDone Flag indicates that all historical partitions were processed.
     */
    public GridDhtPartitionSupplyMessageV3(
        long rebalanceId,
        int grpId,
        AffinityTopologyVersion topVer,
        boolean addDepInfo,
        Throwable err,
        boolean allHistPartsDone
    ) {
        super(rebalanceId, grpId, topVer, addDepInfo, err);

        this.allHistPartsDone = allHistPartsDone;
    }

    /**
     * @return {@code true} if all historical partitions were processed.
     */
    public boolean allHistoricalPartitionsDone() {
        return allHistPartsDone;
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 14:
                if (!writer.writeBoolean("allHistPartsDone", allHistPartsDone))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        if (!super.readFrom(buf, reader))
            return false;

        switch (reader.state()) {
            case 14:
                allHistPartsDone = reader.readBoolean("allHistPartsDone");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridDhtPartitionSupplyMessageV3.class);
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return DIRECT_TYPE;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 15;
    }
}

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

package org.apache.ignite.spi.discovery.tcp.messages;

import java.io.Serializable;
import java.util.UUID;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Ping request.
 */
public class TcpDiscoveryTopPriorityClientMessage extends TcpDiscoveryAbstractMessage {
    /** */
    private static final long serialVersionUID = 0L;

    private final UUID clientNodeId;

    private final Serializable payload;

    public TcpDiscoveryTopPriorityClientMessage(
        UUID creatorNodeId, Serializable payload, UUID clientNodeId
    ) {
        super(creatorNodeId);
        this.clientNodeId = clientNodeId;
        this.payload = payload;
    }

    public UUID clientNodeId() {
        return clientNodeId;
    }

    public Serializable payload() {
        return payload;
    }

    @Override public boolean highPriority() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(TcpDiscoveryTopPriorityClientMessage.class, this, "super", super.toString());
    }
}
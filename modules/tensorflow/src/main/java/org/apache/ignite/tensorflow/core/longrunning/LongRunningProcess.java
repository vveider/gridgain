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

package org.apache.ignite.tensorflow.core.longrunning;

import java.io.Serializable;
import java.util.UUID;
import org.apache.ignite.lang.IgniteRunnable;

/**
 * Long running process specification that contains identifier of a node where the process should be running on and the
 * task to be run.
 */
public class LongRunningProcess implements Serializable {
    /** */
    private static final long serialVersionUID = 6039507725567997183L;

    /** Node identifier. */
    private final UUID nodeId;

    /** Task to be run. */
    private final IgniteRunnable task;

    /**
     * Constructs a new instance of long running process specification.
     *
     * @param nodeId Node identifier.
     * @param task Task to be run.
     */
    public LongRunningProcess(UUID nodeId, IgniteRunnable task) {
        assert nodeId != null : "Node identifier should not be null";
        assert task != null : "Task should not be null";

        this.nodeId = nodeId;
        this.task = task;
    }

    /** */
    public UUID getNodeId() {
        return nodeId;
    }

    /** */
    public IgniteRunnable getTask() {
        return task;
    }
}

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

package org.apache.ignite.gridify;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskName;
import org.apache.ignite.compute.ComputeTaskSplitAdapter;
import org.apache.ignite.compute.gridify.GridifyArgument;

/**
 * Test gridify task.
 */
@ComputeTaskName(TestGridifyTask.TASK_NAME)
public class TestGridifyTask extends ComputeTaskSplitAdapter<GridifyArgument, Object> {
    /** */
    public static final String TASK_NAME = "org.apache.ignite.gridify.TestGridifyTask";

    /** {@inheritDoc} */
    @Override public Collection<? extends ComputeJob> split(int gridSize, GridifyArgument arg) {
        assert arg.getMethodParameters().length == 1;

        return Collections.singletonList(new TestGridifyJob((String)arg.getMethodParameters()[0]));
    }

    /** {@inheritDoc} */
    @Override public Object reduce(List<ComputeJobResult> results) {
        assert results.size() == 1;

        return results.get(0).getData();
    }
}
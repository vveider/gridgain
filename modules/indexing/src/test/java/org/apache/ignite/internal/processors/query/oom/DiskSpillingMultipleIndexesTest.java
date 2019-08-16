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
package org.apache.ignite.internal.processors.query.oom;

/**
 * TODO: Add class description.
 */
public class DiskSpillingMultipleIndexesTest extends DiskSpillingQueriesTest {

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        runDdlDml("CREATE INDEX pers_name ON person(name)");
        runDdlDml("CREATE INDEX pers_weight ON person(weight)");
        runDdlDml("CREATE INDEX pers_code ON person(code)");
        runDdlDml("CREATE INDEX pers_depId ON person(depId)");
    }

    /** {@inheritDoc} */
    @Override protected int queryParallelism() {
        return 4;
    }

    /** {@inheritDoc} */
    @Override protected int nodeCount() {
        return 4;
    }
}

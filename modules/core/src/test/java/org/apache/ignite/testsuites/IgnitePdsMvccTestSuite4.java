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
package org.apache.ignite.testsuites;

import java.util.HashSet;
import java.util.Set;
import junit.framework.TestSuite;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsContinuousRestartTestWithSharedGroupAndIndexes;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsTaskCancelingTest;
import org.apache.ignite.internal.processors.cache.persistence.db.IgnitePdsPartitionPreloadTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.PageLockTrackerManagerTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.SharedPageLockTrackerTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.dumpprocessors.ToFileDumpProcessorTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.log.HeapArrayLockLogTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.log.OffHeapLockLogTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.stack.HeapArrayLockStackTest;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.stack.OffHeapLockStackTest;
import org.apache.ignite.internal.processors.cache.persistence.file.FileDownloaderTest;

/**
 * Mvcc variant of {@link IgnitePdsTestSuite4}.
 */
public class IgnitePdsMvccTestSuite4  extends TestSuite {
    /**
     * @return Suite.
     */
    public static TestSuite suite() {
        System.setProperty(IgniteSystemProperties.IGNITE_FORCE_MVCC_MODE_IN_TESTS, "true");

        TestSuite suite = new TestSuite("Ignite persistent Store Mvcc Test Suite 4");

        Set<Class> ignoredTests = new HashSet<>();

        // Skip classes that already contains Mvcc tests
        ignoredTests.add(IgnitePdsPartitionPreloadTest.class);

        // Skip irrelevant test
        ignoredTests.add(FileDownloaderTest.class);
        ignoredTests.add(IgnitePdsTaskCancelingTest.class);

        // TODO https://issues.apache.org/jira/browse/IGNITE-11937
        ignoredTests.add(IgnitePdsContinuousRestartTestWithSharedGroupAndIndexes.class);

        // Skip page lock tracker tests for MVCC suite.
        ignoredTests.add(PageLockTrackerManagerTest.class);
        ignoredTests.add(SharedPageLockTrackerTest.class);
        ignoredTests.add(ToFileDumpProcessorTest.class);
        ignoredTests.add(HeapArrayLockLogTest.class);
        ignoredTests.add(HeapArrayLockStackTest.class);
        ignoredTests.add(OffHeapLockLogTest.class);
        ignoredTests.add(OffHeapLockStackTest.class);

        suite.addTest(IgnitePdsTestSuite4.suite(ignoredTests));

        return suite;
    }
}
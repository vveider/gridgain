/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.testsuites;

import junit.framework.TestSuite;
import org.apache.ignite.internal.processors.authentication.Authentication1kUsersNodeRestartTest;
import org.apache.ignite.internal.processors.authentication.AuthenticationConfigurationClusterTest;
import org.apache.ignite.internal.processors.authentication.AuthenticationOnNotActiveClusterTest;
import org.apache.ignite.internal.processors.authentication.AuthenticationProcessorNPEOnStartTest;
import org.apache.ignite.internal.processors.authentication.AuthenticationProcessorNodeRestartTest;
import org.apache.ignite.internal.processors.authentication.AuthenticationProcessorSelfTest;
import org.apache.ignite.internal.processors.cache.CacheDataRegionConfigurationTest;
import org.apache.ignite.internal.processors.cache.CacheGroupMetricsMBeanTest;
import org.apache.ignite.internal.processors.cache.CacheMetricsManageTest;
import org.apache.ignite.internal.processors.cache.GridTransactionsSystemUserTimeMetricsTest;
import org.apache.ignite.internal.processors.cache.IgniteDynamicCacheStartFailWithPersistenceTest;
import org.apache.ignite.internal.processors.cache.SafeLogTxFinishErrorTest;
import org.apache.ignite.internal.processors.cache.WalModeChangeAdvancedSelfTest;
import org.apache.ignite.internal.processors.cache.WalModeChangeCoordinatorNotAffinityNodeSelfTest;
import org.apache.ignite.internal.processors.cache.WalModeChangeSelfTest;
import org.apache.ignite.internal.processors.cache.distributed.Cache64kPartitionsTest;
import org.apache.ignite.internal.processors.cache.distributed.CachePageWriteLockUnlockTest;
import org.apache.ignite.internal.processors.cache.distributed.CachePartitionLossDetectionOnNodeLeftTest;
import org.apache.ignite.internal.processors.cache.distributed.CachePartitionLostWhileClearingTest;
import org.apache.ignite.internal.processors.cache.distributed.CacheRebalanceThreadPoolTest;
import org.apache.ignite.internal.processors.cache.distributed.CacheRentingStateRepairTest;
import org.apache.ignite.internal.processors.cache.distributed.dht.IgniteCacheStartWithLoadTest;
import org.apache.ignite.internal.processors.cache.distributed.CacheDataLossOnPartitionMoveTest;
import org.apache.ignite.internal.processors.cache.distributed.IgniteCachePartitionLossPolicySelfTest;
import org.apache.ignite.internal.processors.cache.distributed.rebalancing.GridCacheRebalancingPartitionCountersTest;
import org.apache.ignite.internal.processors.cache.distributed.rebalancing.GridCacheRebalancingWithAsyncClearingTest;
import org.apache.ignite.internal.processors.cache.eviction.paged.PageEvictionMultinodeMixedRegionsTest;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsCacheAssignmentNodeRestartsTest;
import org.apache.ignite.internal.processors.cache.persistence.db.CheckpointBufferDeadlockTest;
import org.apache.ignite.internal.processors.cache.transactions.TransactionIntegrityWithPrimaryIndexCorruptionTest;
import org.apache.ignite.internal.processors.cache.transactions.TxCrossCacheMapOnInvalidTopologyTest;
import org.apache.ignite.internal.processors.cache.transactions.TxCrossCachePartitionConsistencyTest;
import org.apache.ignite.internal.processors.cache.transactions.TxCrossCacheRemoteMultiplePartitionReservationTest;
import org.apache.ignite.internal.processors.cache.transactions.TxRecoveryWithConcurrentRollbackTest;
import org.apache.ignite.internal.processors.cache.transactions.TxRollbackAsyncWithPersistenceTest;
import org.apache.ignite.internal.processors.cache.transactions.TxWithSmallTimeoutAndContentionOneKeyTest;

import java.util.Set;

/**
 * Test suite.
 */
public class IgniteCacheTestSuite7 extends TestSuite {
    /**
     * @return IgniteCache test suite.
     * @throws Exception Thrown in case of the failure.
     */
    public static TestSuite suite() throws Exception {
        return suite(null);
    }

    /**
     * @param ignoredTests Tests to ignore.
     * @return Test suite.
     * @throws Exception Thrown in case of the failure.
     */
    public static TestSuite suite(Set<Class> ignoredTests) throws Exception {
        TestSuite suite = new TestSuite("IgniteCache With Persistence Test Suite");

        suite.addTestSuite(CheckpointBufferDeadlockTest.class);
        suite.addTestSuite(IgniteCacheStartWithLoadTest.class);
        suite.addTestSuite(IgniteCachePartitionLossPolicySelfTest.class);

        suite.addTestSuite(AuthenticationConfigurationClusterTest.class);
        suite.addTestSuite(AuthenticationProcessorSelfTest.class);
        suite.addTestSuite(AuthenticationOnNotActiveClusterTest.class);
        suite.addTestSuite(AuthenticationProcessorNodeRestartTest.class);
        suite.addTestSuite(AuthenticationProcessorNPEOnStartTest.class);
        suite.addTestSuite(Authentication1kUsersNodeRestartTest.class);

        suite.addTestSuite(CacheDataRegionConfigurationTest.class);

        suite.addTestSuite(WalModeChangeAdvancedSelfTest.class);
        suite.addTestSuite(WalModeChangeSelfTest.class);
        suite.addTestSuite(WalModeChangeCoordinatorNotAffinityNodeSelfTest.class);

        suite.addTestSuite(Cache64kPartitionsTest.class);
        suite.addTestSuite(GridCacheRebalancingPartitionCountersTest.class);
        suite.addTestSuite(GridCacheRebalancingWithAsyncClearingTest.class);

        suite.addTestSuite(IgnitePdsCacheAssignmentNodeRestartsTest.class);
        suite.addTestSuite(TxRollbackAsyncWithPersistenceTest.class);

        suite.addTestSuite(CacheGroupMetricsMBeanTest.class);
        suite.addTestSuite(CacheMetricsManageTest.class);
        suite.addTestSuite(PageEvictionMultinodeMixedRegionsTest.class);

        suite.addTestSuite(IgniteDynamicCacheStartFailWithPersistenceTest.class);

        suite.addTestSuite(TxWithSmallTimeoutAndContentionOneKeyTest.class);

        suite.addTestSuite(CacheRentingStateRepairTest.class);

        suite.addTestSuite(TransactionIntegrityWithPrimaryIndexCorruptionTest.class);
        suite.addTestSuite(CacheDataLossOnPartitionMoveTest.class);

        suite.addTestSuite(CachePageWriteLockUnlockTest.class);

        suite.addTestSuite(CachePartitionLossDetectionOnNodeLeftTest.class);
        suite.addTestSuite(CachePartitionLostWhileClearingTest.class);

        suite.addTestSuite(TxCrossCacheMapOnInvalidTopologyTest.class);

        suite.addTestSuite(GridTransactionsSystemUserTimeMetricsTest.class);

        suite.addTestSuite(SafeLogTxFinishErrorTest.class);

        suite.addTestSuite(TxCrossCachePartitionConsistencyTest.class);

        suite.addTestSuite(CacheRebalanceThreadPoolTest.class);

        suite.addTestSuite(TxCrossCacheRemoteMultiplePartitionReservationTest.class);

        suite.addTestSuite(TxRecoveryWithConcurrentRollbackTest.class);

        return suite;
    }
}

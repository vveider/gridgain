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
package org.apache.ignite.internal.visor.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.CacheObjectUtils;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.cache.persistence.DbCheckpointListener;
import org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.IgniteCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreManager;
import org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree;
import org.apache.ignite.internal.processors.cache.persistence.tree.CorruptedTreeException;
import org.apache.ignite.internal.processors.cache.verify.GridNotIdleException;
import org.apache.ignite.internal.processors.cache.verify.IdleVerifyUtility;
import org.apache.ignite.internal.processors.cache.verify.PartitionKey;
import org.apache.ignite.internal.processors.query.GridQueryProcessor;
import org.apache.ignite.internal.processors.query.GridQueryTypeDescriptor;
import org.apache.ignite.internal.processors.query.QueryTypeDescriptorImpl;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.processors.query.h2.database.H2TreeIndexBase;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2RowDescriptor;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Table;
import org.apache.ignite.internal.processors.query.h2.opt.H2CacheRow;
import org.apache.ignite.internal.util.lang.GridIterator;
import org.apache.ignite.internal.util.lang.GridTuple3;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.internal.util.typedef.T3;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.resources.LoggerResource;
import org.h2.engine.Session;
import org.h2.index.Cursor;
import org.h2.index.Index;
import org.h2.message.DbException;
import org.jetbrains.annotations.Nullable;

import static java.util.Collections.emptyMap;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.shuffle;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.FLAG_IDX;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.INDEX_PARTITION;
import static org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionState.OWNING;
import static org.apache.ignite.internal.util.IgniteUtils.error;

/**
 * Closure that locally validates indexes of given caches.
 * Validation consists of four checks:
 * 1. If entry is present in cache data tree, it's reachable from all cache SQL indexes
 * 2. If entry is present in cache SQL index, it can be dereferenced with link from index
 * 3. If entry is present in cache SQL index, it's present in cache data tree
 * 4. If size of cache and index on same table are not same
 */
public class ValidateIndexesClosure implements IgniteCallable<VisorValidateIndexesJobResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Ignite. */
    @IgniteInstanceResource
    private transient IgniteEx ignite;

    /** Injected logger. */
    @LoggerResource
    private IgniteLogger log;

    /** Cache names. */
    private final Set<String> cacheNames;

    /** If provided only first K elements will be validated. */
    private final int checkFirst;

    /** If provided only each Kth element will be validated. */
    private final int checkThrough;

    /** Check CRC. */
    private final boolean checkCrc;

    /** Check that index size and cache size are same. */
    private final boolean checkSizes;

    /** Counter of processed partitions. */
    private final AtomicInteger processedPartitions = new AtomicInteger(0);

    /** Total partitions. */
    private volatile int totalPartitions;

    /** Counter of processed indexes. */
    private final AtomicInteger processedIndexes = new AtomicInteger(0);

    /** Counter of integrity checked indexes. */
    private final AtomicInteger integrityCheckedIndexes = new AtomicInteger(0);

    /** Counter of calculated sizes of caches per partitions. */
    private final AtomicInteger processedCacheSizePartitions = new AtomicInteger(0);

    /** Counter of calculated index sizes. */
    private final AtomicInteger processedIdxSizes = new AtomicInteger(0);

    /** Total partitions. */
    private volatile int totalIndexes;

    /** Total cache groups. */
    private volatile int totalCacheGrps;

    /** Last progress print timestamp. */
    private final AtomicLong lastProgressPrintTs = new AtomicLong(0);

    /** Calculation executor. */
    private volatile ExecutorService calcExecutor;

    /** Group cache ids when calculating cache size was an error. */
    private final Set<Integer> failCalcCacheSizeGrpIds = newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Constructor.
     *
     * @param cacheNames Cache names.
     * @param checkFirst If positive only first K elements will be validated.
     * @param checkThrough If positive only each Kth element will be validated.
     * @param checkCrc Check CRC sum on stored pages on disk.
     * @param checkSizes Check that index size and cache size are same.
     */
    public ValidateIndexesClosure(Set<String> cacheNames, int checkFirst, int checkThrough, boolean checkCrc,
        boolean checkSizes) {
        this.cacheNames = cacheNames;
        this.checkFirst = checkFirst;
        this.checkThrough = checkThrough;
        this.checkCrc = checkCrc;
        this.checkSizes = checkSizes;
    }

    /** {@inheritDoc} */
    @Override public VisorValidateIndexesJobResult call() throws Exception {
        calcExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try {
            return call0();
        }
        finally {
            calcExecutor.shutdown();
        }
    }

    /**
     *
     */
    private VisorValidateIndexesJobResult call0() {
        Set<Integer> grpIds = getGroupIds();

        totalCacheGrps = grpIds.size();

        Map<Integer, IndexIntegrityCheckIssue> integrityCheckResults = integrityCheckIndexesPartitions(grpIds);

        List<T2<CacheGroupContext, GridDhtLocalPartition>> partArgs = new ArrayList<>();
        List<T2<GridCacheContext, H2TreeIndexBase>> idxArgs = new ArrayList<>();

        getAndPreparePartitionsAndIndexesArguments(grpIds, integrityCheckResults, partArgs, idxArgs);

        totalPartitions = partArgs.size();
        totalIndexes = idxArgs.size();

        List<Future<Map<PartitionKey, ValidateIndexesPartitionResult>>> procPartFutures = processPartitionsAsync(partArgs);
        List<Future<Map<String, ValidateIndexesPartitionResult>>> procIdxFutures = processIndexesAsync(idxArgs);
        List<T3<CacheGroupContext, GridDhtLocalPartition, Future<CacheSize>>> cacheSizeFutures = new ArrayList<>();
        List<T3<GridCacheContext, Index, Future<T2<Throwable, Long>>>> idxSizeFutures = new ArrayList<>();

        processCheckSizesAsync(partArgs, idxArgs, cacheSizeFutures, idxSizeFutures);

        try {
            Map<String, Integer> inlineSizes = getInlineSizes(idxArgs);
            Map<PartitionKey, ValidateIndexesPartitionResult> partResults = getResults(procPartFutures);
            Map<String, ValidateIndexesPartitionResult> idxResults = getResults(procIdxFutures);

            waitAllFutures(cacheSizeFutures, GridTuple3::get3);
            waitAllFutures(idxSizeFutures, GridTuple3::get3);

            Map<String, ValidateIndexesCheckSizeResult> checkSizeResults = checkSizes(cacheSizeFutures, idxSizeFutures);

            log.warning("ValidateIndexesClosure finished: processed " + totalPartitions + " partitions and "
                + totalIndexes + " indexes.");

            return new VisorValidateIndexesJobResult(
                partResults,
                idxResults,
                integrityCheckResults.values(),
                checkSizeResults,
                inlineSizes
            );
        }
        catch (InterruptedException | ExecutionException e) {
            cancelIncompletedFutures(procPartFutures, i -> i);
            cancelIncompletedFutures(procIdxFutures, i -> i);
            cancelIncompletedFutures(cacheSizeFutures, GridTuple3::get3);
            cancelIncompletedFutures(idxSizeFutures, GridTuple3::get3);

            throw unwrapFutureException(e);
        }
    }

    /**
     * Waits completion of all futures from collection {@code coll}. The elemenent of the collection {@code coll} must
     * be transformed to {@link Future} by {@code transformer}.
     *
     * @param coll Collection with elements.
     * @param transformer Transformer for converting colletion element to {@link Future}.
     * @param <T> Type of elements.
     * @throws ExecutionException If the computation threw an exception.
     * @throws InterruptedException If the current thread was interrupted while waiting.
     */
    private static <T> void waitAllFutures(
        Collection<T> coll,
        Function<T, Future> transformer
    ) throws ExecutionException, InterruptedException {
        for (T e : coll)
            transformer.apply(e).get();
    }


    /**
     * Cancels all incompleted futures from collection {@code coll}. The elemenent of the collection {@code coll} must be
     * transformed to {@link Future} by {@code transformer}.
     *
     * @param coll Collection with elements.
     * @param transformer Transformer for converting colletion element to {@link Future}.
     * @param <T> Type of elements.
     */
    private static <T> void cancelIncompletedFutures(Collection<T> coll, Function<T, Future> transformer) {
        for (T e : coll) {
            Future f = transformer.apply(e);

            if (!f.isDone())
                f.cancel(false);
        }
    }

    /** */
    private static <T> Map<T, ValidateIndexesPartitionResult> getResults(
        Collection<Future<Map<T, ValidateIndexesPartitionResult>>> futs
    ) throws ExecutionException, InterruptedException {
        Map<T, ValidateIndexesPartitionResult> res = new HashMap<>();

        for (Future<Map<T, ValidateIndexesPartitionResult>> fut : futs) {
            Map<T, ValidateIndexesPartitionResult> partRes = fut.get();

            if (!partRes.isEmpty() && partRes.entrySet().stream().anyMatch(e -> !e.getValue().issues().isEmpty()))
                res.putAll(partRes);
        }

        return res;
    }

    /** */
    private static Map<String, Integer> getInlineSizes(List<T2<GridCacheContext, H2TreeIndexBase>> idxArgs) {
        Map<String, Integer> res = new HashMap<>();

        for (T2<GridCacheContext, H2TreeIndexBase> t : idxArgs) {
            H2TreeIndexBase idx = t.get2();

            String tblName = idx.getTable().rowDescriptor().tableDescriptor().fullTableName();

            res.put("table: " + tblName + " index: " + idx.getName(), idx.inlineSize());
        }

        return res;
    }

    /** */
    private void processCheckSizesAsync(
        List<T2<CacheGroupContext, GridDhtLocalPartition>> partArgs,
        List<T2<GridCacheContext, H2TreeIndexBase>> idxArgs,
        List<T3<CacheGroupContext, GridDhtLocalPartition, Future<CacheSize>>> cacheSizeFutures,
        List<T3<GridCacheContext, Index, Future<T2<Throwable, Long>>>> idxSizeFutures
    ) {
        if (checkSizes) {
            for (T2<CacheGroupContext, GridDhtLocalPartition> partArg : partArgs) {
                CacheGroupContext cacheGrpCtx = partArg.get1();
                GridDhtLocalPartition locPart = partArg.get2();

                cacheSizeFutures.add(new T3<>(cacheGrpCtx, locPart, calcCacheSizeAsync(cacheGrpCtx, locPart)));
            }

            for (T2<GridCacheContext, H2TreeIndexBase> idxArg : idxArgs) {
                GridCacheContext cacheCtx = idxArg.get1();
                Index idx = idxArg.get2();

                idxSizeFutures.add(new T3<>(cacheCtx, idx, calcIndexSizeAsync(cacheCtx, idx)));
            }
        }
    }

    /** */
    private List<Future<Map<String, ValidateIndexesPartitionResult>>> processIndexesAsync(
        List<T2<GridCacheContext, H2TreeIndexBase>> idxArgs
    ) {
        List<Future<Map<String, ValidateIndexesPartitionResult>>> res = new ArrayList<>();

        for (T2<GridCacheContext, H2TreeIndexBase> t2 : idxArgs) {
            res.add(calcExecutor.submit(() -> {
                BPlusTree.suspendFailureDiagnostic.set(true);

                try {
                    return processIndex(t2.get1(), t2.get2());
                }
                finally {
                    BPlusTree.suspendFailureDiagnostic.set(false);
                }
            }));
        }

        return res;
    }

    /** */
    private List<Future<Map<PartitionKey, ValidateIndexesPartitionResult>>> processPartitionsAsync(
        List<T2<CacheGroupContext, GridDhtLocalPartition>> partArgs
    ) {
        List<Future<Map<PartitionKey, ValidateIndexesPartitionResult>>> res = new ArrayList<>();

        for (T2<CacheGroupContext, GridDhtLocalPartition> t2 : partArgs)
            res.add(calcExecutor.submit(() -> processPartition(t2.get1(), t2.get2())));

        return res;
    }

    /**
     * Find out local partitons and indexes for {@code grpIds}. Results will be passed to {@code partArgs} and
     * {@code idxArgs}.
     *
     * @param grpIds Set of cachegroup ids.
     * @param integrityCheckResults Results of partitions integrity check.
     * @param partArgs Collection for accumulate local partitions.
     * @param idxArgs Collection for accumulate indexes.
     */
    private void getAndPreparePartitionsAndIndexesArguments(
        Set<Integer> grpIds,
        Map<Integer, IndexIntegrityCheckIssue> integrityCheckResults,
        List<T2<CacheGroupContext, GridDhtLocalPartition>> partArgs,
        List<T2<GridCacheContext, H2TreeIndexBase>> idxArgs
    ) {
        GridQueryProcessor qryProcessor = ignite.context().query();
        IgniteH2Indexing h2Indexing = (IgniteH2Indexing)qryProcessor.getIndexing();

        for (Integer grpId : grpIds) {
            CacheGroupContext grpCtx = ignite.context().cache().cacheGroup(grpId);

            if (isNull(grpCtx) || integrityCheckResults.containsKey(grpId))
                continue;

            for (GridDhtLocalPartition part : grpCtx.topology().localPartitions())
                partArgs.add(new T2<>(grpCtx, part));

            for (GridCacheContext ctx : grpCtx.caches()) {
                String cacheName = ctx.name();

                if (cacheNames == null || cacheNames.contains(cacheName)) {
                    Collection<GridQueryTypeDescriptor> types = qryProcessor.types(cacheName);

                    if (F.isEmpty(types))
                        continue;

                    for (GridQueryTypeDescriptor type : types) {
                        GridH2Table gridH2Tbl = h2Indexing.schemaManager().dataTable(cacheName, type.tableName());

                        if (isNull(gridH2Tbl))
                            continue;

                        for (Index idx : gridH2Tbl.getIndexes()) {
                            if (idx instanceof H2TreeIndexBase)
                                idxArgs.add(new T2<>(ctx, (H2TreeIndexBase)idx));
                        }
                    }
                }
            }
        }

        // To decrease contention on same indexes.
        shuffle(partArgs);
        shuffle(idxArgs);
    }

    /**
     * Gets cachegroup id according to given {@link #cacheNames}.
     *
     * @return Set of cachegroup ids.
     */
    private Set<Integer> getGroupIds() {
        Set<Integer> grpIds = new HashSet<>();

        Set<String> missingCaches = new HashSet<>();

        if (cacheNames != null) {
            for (String cacheName : cacheNames) {
                DynamicCacheDescriptor desc = ignite.context().cache().cacheDescriptor(cacheName);

                if (desc == null) {
                    missingCaches.add(cacheName);

                    continue;
                }

                grpIds.add(desc.groupId());
            }

            if (!missingCaches.isEmpty()) {
                StringBuilder strBuilder = new StringBuilder("The following caches do not exist: ");

                for (String name : missingCaches)
                    strBuilder.append(name).append(", ");

                strBuilder.delete(strBuilder.length() - 2, strBuilder.length());

                throw new IgniteException(strBuilder.toString());
            }
        }
        else {
            Collection<CacheGroupContext> groups = ignite.context().cache().cacheGroups();

            for (CacheGroupContext grp : groups) {
                if (!grp.systemCache() && !grp.isLocal())
                    grpIds.add(grp.groupId());
            }
        }

        return grpIds;
    }

    /**
     * @param grpIds Group ids.
     */
    private Map<Integer, IndexIntegrityCheckIssue> integrityCheckIndexesPartitions(Set<Integer> grpIds) {
        List<Future<T2<Integer, IndexIntegrityCheckIssue>>> integrityCheckFutures = new ArrayList<>(grpIds.size());

        Map<Integer, IndexIntegrityCheckIssue> integrityCheckResults = new HashMap<>();

        int curFut = 0;

        IgniteCacheDatabaseSharedManager db = ignite.context().cache().context().database();

        DbCheckpointListener lsnr = null;

        try {
            AtomicBoolean cpFlag = new AtomicBoolean();

            if (db instanceof GridCacheDatabaseSharedManager) {
                lsnr = new DbCheckpointListener() {
                    @Override public void onMarkCheckpointBegin(Context ctx) {
                        /* No-op. */
                    }

                    @Override public void onCheckpointBegin(Context ctx) {
                        if (ctx.hasUserPages())
                            cpFlag.set(true);
                    }

                    @Override public void beforeCheckpointBegin(Context ctx) throws IgniteCheckedException {
                        /* No-op. */
                    }
                };

                ((GridCacheDatabaseSharedManager)db).addCheckpointListener(lsnr);

                if (IdleVerifyUtility.isCheckpointNow(db))
                    throw new GridNotIdleException(IdleVerifyUtility.CLUSTER_NOT_IDLE_MSG);
            }

            if (checkCrc) {
                for (Integer grpId : grpIds) {
                    final CacheGroupContext grpCtx = ignite.context().cache().cacheGroup(grpId);

                    if (grpCtx == null || !grpCtx.persistenceEnabled()) {
                        integrityCheckedIndexes.incrementAndGet();

                        continue;
                    }

                    Future<T2<Integer, IndexIntegrityCheckIssue>> checkFut =
                        calcExecutor.submit(new Callable<T2<Integer, IndexIntegrityCheckIssue>>() {
                            @Override public T2<Integer, IndexIntegrityCheckIssue> call() throws Exception {
                                IndexIntegrityCheckIssue issue = integrityCheckIndexPartition(grpCtx, cpFlag);

                                return new T2<>(grpCtx.groupId(), issue);
                            }
                        });

                    integrityCheckFutures.add(checkFut);
                }
            }

            for (Future<T2<Integer, IndexIntegrityCheckIssue>> fut : integrityCheckFutures) {
                T2<Integer, IndexIntegrityCheckIssue> res = fut.get();

                if (res.getValue() != null)
                    integrityCheckResults.put(res.getKey(), res.getValue());
            }
        }
        catch (InterruptedException | ExecutionException e) {
            for (int j = curFut; j < integrityCheckFutures.size(); j++)
                integrityCheckFutures.get(j).cancel(false);

            throw unwrapFutureException(e);
        }
        finally {
            if (db instanceof GridCacheDatabaseSharedManager && lsnr != null)
                ((GridCacheDatabaseSharedManager)db).removeCheckpointListener(lsnr);
        }

        return integrityCheckResults;
    }

    /**
     * @param gctx Cache group context.
     * @param cpFlag Checkpoint status flag.
     */
    private IndexIntegrityCheckIssue integrityCheckIndexPartition(CacheGroupContext gctx, AtomicBoolean cpFlag) {
        GridKernalContext ctx = ignite.context();
        GridCacheSharedContext cctx = ctx.cache().context();

        try {
            FilePageStoreManager pageStoreMgr = (FilePageStoreManager)cctx.pageStore();

            IdleVerifyUtility.checkPartitionsPageCrcSum(pageStoreMgr, gctx, INDEX_PARTITION, FLAG_IDX, cpFlag);

            return null;
        }
        catch (Throwable t) {
            if (cpFlag.get())
                throw new GridNotIdleException("Checkpoint with dirty pages started! Cluster not idle!", t);

            log.error("Integrity check of index partition of cache group " + gctx.cacheOrGroupName() + " failed", t);

            return new IndexIntegrityCheckIssue(gctx.cacheOrGroupName(), t);
        }
        finally {
            integrityCheckedIndexes.incrementAndGet();

            printProgressIfNeeded(() -> "Current progress of ValidateIndexesClosure: checked integrity of "
                + integrityCheckedIndexes.get() + " index partitions of " + totalCacheGrps + " cache groups");
        }
    }

    /**
     * @param grpCtx Group context.
     * @param part Local partition.
     */
    private Map<PartitionKey, ValidateIndexesPartitionResult> processPartition(
        CacheGroupContext grpCtx,
        GridDhtLocalPartition part
    ) {
        if (!part.reserve())
            return emptyMap();

        ValidateIndexesPartitionResult partRes;

        try {
            if (part.state() != OWNING)
                return emptyMap();

            long updateCntrBefore = part.updateCounter();

            long partSize = part.dataStore().fullSize();

            GridIterator<CacheDataRow> it = grpCtx.offheap().partitionIterator(part.id());

            Object consId = ignite.context().discovery().localNode().consistentId();

            boolean isPrimary = part.primary(grpCtx.topology().readyTopologyVersion());

            partRes = new ValidateIndexesPartitionResult(updateCntrBefore, partSize, isPrimary, consId, null);

            boolean enoughIssues = false;

            GridQueryProcessor qryProcessor = ignite.context().query();

            final boolean skipConditions = checkFirst > 0 || checkThrough > 0;
            final boolean bothSkipConditions = checkFirst > 0 && checkThrough > 0;

            long current = 0;
            long processedNumber = 0;

            while (it.hasNextX()) {
                if (enoughIssues)
                    break;

                CacheDataRow row = it.nextX();

                if (skipConditions) {
                    if (bothSkipConditions) {
                        if (processedNumber > checkFirst)
                            break;
                        else if (current++ % checkThrough > 0)
                            continue;
                        else
                            processedNumber++;
                    }
                    else {
                        if (checkFirst > 0) {
                            if (current++ > checkFirst)
                                break;
                        }
                        else {
                            if (current++ % checkThrough > 0)
                                continue;
                        }
                    }
                }

                int cacheId = row.cacheId() == 0 ? grpCtx.groupId() : row.cacheId();

                GridCacheContext cacheCtx = row.cacheId() == 0 ?
                    grpCtx.singleCacheContext() : grpCtx.shared().cacheContext(row.cacheId());

                if (cacheCtx == null)
                    throw new IgniteException("Unknown cacheId of CacheDataRow: " + cacheId);

                if (row.link() == 0L) {
                    String errMsg = "Invalid partition row, possibly deleted";

                    log.error(errMsg);

                    IndexValidationIssue is = new IndexValidationIssue(null, cacheCtx.name(), null,
                        new IgniteCheckedException(errMsg));

                    enoughIssues |= partRes.reportIssue(is);

                    continue;
                }

                QueryTypeDescriptorImpl res = qryProcessor.typeByValue(
                    cacheCtx.name(),
                    cacheCtx.cacheObjectContext(),
                    row.key(),
                    row.value(),
                    true
                );

                if (res == null)
                    continue; // Tolerate - (k, v) is just not indexed.

                IgniteH2Indexing indexing = (IgniteH2Indexing)qryProcessor.getIndexing();

                GridH2Table gridH2Tbl = indexing.schemaManager().dataTable(cacheCtx.name(), res.tableName());

                if (gridH2Tbl == null)
                    continue; // Tolerate - (k, v) is just not indexed.

                GridH2RowDescriptor gridH2RowDesc = gridH2Tbl.rowDescriptor();

                H2CacheRow h2Row = gridH2RowDesc.createRow(row);

                ArrayList<Index> indexes = gridH2Tbl.getIndexes();

                for (Index idx : indexes) {
                    if (!(idx instanceof H2TreeIndexBase))
                        continue;

                    try {
                        Cursor cursor = idx.find((Session)null, h2Row, h2Row);

                        if (cursor == null || !cursor.next())
                            throw new IgniteCheckedException("Key is present in CacheDataTree, but can't be found in SQL index.");
                    }
                    catch (Throwable t) {
                        Object o = CacheObjectUtils.unwrapBinaryIfNeeded(
                            grpCtx.cacheObjectContext(), row.key(), true, true);

                        IndexValidationIssue is = new IndexValidationIssue(
                            o.toString(), cacheCtx.name(), idx.getName(), t);

                        log.error("Failed to lookup key: " + is.toString(), t);

                        enoughIssues |= partRes.reportIssue(is);
                    }
                }
            }
        }
        catch (IgniteCheckedException e) {
            error(log, "Failed to process partition [grpId=" + grpCtx.groupId() +
                ", partId=" + part.id() + "]", e);

            return emptyMap();
        }
        finally {
            part.release();

            printProgressOfIndexValidationIfNeeded();
        }

        PartitionKey partKey = new PartitionKey(grpCtx.groupId(), part.id(), grpCtx.cacheOrGroupName());

        processedPartitions.incrementAndGet();

        return Collections.singletonMap(partKey, partRes);
    }

    /**
     *
     */
    private void printProgressOfIndexValidationIfNeeded() {
        printProgressIfNeeded(() -> "Current progress of ValidateIndexesClosure: processed " +
            processedPartitions.get() + " of " + totalPartitions + " partitions, " +
            processedIndexes.get() + " of " + totalIndexes + " SQL indexes" +
            (checkSizes ? ", " + processedCacheSizePartitions.get() + " of " + totalPartitions +
                " calculate cache size per partitions, " + processedIdxSizes.get() + " of " + totalIndexes +
                "calculate index size" : ""));
    }

    /**
     *
     */
    private void printProgressIfNeeded(Supplier<String> msgSup) {
        long curTs = U.currentTimeMillis();
        long lastTs = lastProgressPrintTs.get();

        if (curTs - lastTs >= 60_000 && lastProgressPrintTs.compareAndSet(lastTs, curTs))
            log.warning(msgSup.get());
    }

    /**
     * @param ctx Context.
     * @param idx Index.
     */
    private Map<String, ValidateIndexesPartitionResult> processIndex(GridCacheContext ctx, Index idx) {
        Object consId = ignite.context().discovery().localNode().consistentId();

        ValidateIndexesPartitionResult idxValidationRes = new ValidateIndexesPartitionResult(
            -1, -1, true, consId, idx.getName());

        boolean enoughIssues = false;

        Cursor cursor = null;

        try {
            cursor = idx.find((Session)null, null, null);

            if (cursor == null)
                throw new IgniteCheckedException("Can't iterate through index: " + idx);
        }
        catch (Throwable t) {
            IndexValidationIssue is = new IndexValidationIssue(null, ctx.name(), idx.getName(), t);

            log.error("Find in index failed: " + is.toString());

            idxValidationRes.reportIssue(is);

            enoughIssues = true;
        }

        final boolean skipConditions = checkFirst > 0 || checkThrough > 0;
        final boolean bothSkipConditions = checkFirst > 0 && checkThrough > 0;

        long current = 0;
        long processedNumber = 0;

        KeyCacheObject previousKey = null;

        while (!enoughIssues) {
            KeyCacheObject h2key = null;

            try {
                try {
                    if (!cursor.next())
                        break;
                }
                catch (DbException e) {
                    if (X.hasCause(e, CorruptedTreeException.class))
                        throw new IgniteCheckedException("Key is present in SQL index, but is missing in corresponding " +
                            "data page. Previous successfully read key: " +
                            CacheObjectUtils.unwrapBinaryIfNeeded(ctx.cacheObjectContext(), previousKey, true, true),
                            X.cause(e, CorruptedTreeException.class)
                        );

                    throw e;
                }

                H2CacheRow h2Row = (H2CacheRow)cursor.get();

                if (skipConditions) {
                    if (bothSkipConditions) {
                        if (processedNumber > checkFirst)
                            break;
                        else if (current++ % checkThrough > 0)
                            continue;
                        else
                            processedNumber++;
                    }
                    else {
                        if (checkFirst > 0) {
                            if (current++ > checkFirst)
                                break;
                        }
                        else {
                            if (current++ % checkThrough > 0)
                                continue;
                        }
                    }
                }

                h2key = (KeyCacheObject)h2Row.key();

                if (h2Row.link() != 0L) {
                    CacheDataRow cacheDataStoreRow = ctx.group().offheap().read(ctx, h2key);

                    if (cacheDataStoreRow == null)
                        throw new IgniteCheckedException("Key is present in SQL index, but can't be found in CacheDataTree.");
                }
                else
                    throw new IgniteCheckedException("Invalid index row, possibly deleted " + h2Row);
            }
            catch (Throwable t) {
                Object o = CacheObjectUtils.unwrapBinaryIfNeeded(
                    ctx.cacheObjectContext(), h2key, true, true);

                IndexValidationIssue is = new IndexValidationIssue(
                    String.valueOf(o), ctx.name(), idx.getName(), t);

                log.error("Failed to lookup key: " + is.toString());

                enoughIssues |= idxValidationRes.reportIssue(is);
            }
            finally {
                previousKey = h2key;
            }
        }

        CacheGroupContext group = ctx.group();

        String uniqueIdxName = String.format(
            "[cacheGroup=%s, cacheGroupId=%s, cache=%s, cacheId=%s, idx=%s]",
            group.name(),
            group.groupId(),
            ctx.name(),
            ctx.cacheId(),
            idx.getName()
        );

        processedIndexes.incrementAndGet();

        printProgressOfIndexValidationIfNeeded();

        return Collections.singletonMap(uniqueIdxName, idxValidationRes);
    }

    /**
     * @param e Future result exception.
     * @return Unwrapped exception.
     */
    private IgniteException unwrapFutureException(Exception e) {
        assert e instanceof InterruptedException || e instanceof ExecutionException : "Expecting either InterruptedException " +
            "or ExecutionException";

        if (e instanceof InterruptedException)
            return new IgniteInterruptedException((InterruptedException)e);
        else if (e.getCause() instanceof IgniteException)
            return (IgniteException)e.getCause();
        else
            return new IgniteException(e.getCause());
    }

    /**
     * Asynchronous calculation of caches size with divided by tables.
     *
     * @param grpCtx Cache group context.
     * @param locPart Local partition.
     * @return Future with cache sizes.
     */
    private Future<CacheSize> calcCacheSizeAsync(
        CacheGroupContext grpCtx,
        GridDhtLocalPartition locPart
    ) {
        return calcExecutor.submit(() -> {
            try {
                int grpId = grpCtx.groupId();

                if (failCalcCacheSizeGrpIds.contains(grpId))
                    return new CacheSize(null, null);

                boolean reserve = false;

                int partId = locPart.id();

                try {
                    if (!(reserve = locPart.reserve()))
                        throw new IgniteException("Can't reserve partition");

                    if (locPart.state() != OWNING)
                        throw new IgniteException("Partition not in state " + OWNING);

                    Map<Integer, Map<String, AtomicLong>> cacheSizeByTbl = new HashMap<>();

                    GridIterator<CacheDataRow> partIter = grpCtx.offheap().partitionIterator(partId);

                    GridQueryProcessor qryProcessor = ignite.context().query();
                    IgniteH2Indexing h2Indexing = (IgniteH2Indexing)qryProcessor.getIndexing();

                    while (partIter.hasNextX() && !failCalcCacheSizeGrpIds.contains(grpId)) {
                        CacheDataRow cacheDataRow = partIter.nextX();

                        int cacheId = cacheDataRow.cacheId();

                        GridCacheContext cacheCtx = cacheId == 0 ?
                            grpCtx.singleCacheContext() : grpCtx.shared().cacheContext(cacheId);

                        if (cacheCtx == null)
                            throw new IgniteException("Unknown cacheId of CacheDataRow: " + cacheId);

                        if (cacheDataRow.link() == 0L)
                            throw new IgniteException("Contains invalid partition row, possibly deleted");

                        String cacheName = cacheCtx.name();

                        QueryTypeDescriptorImpl qryTypeDesc = qryProcessor.typeByValue(
                            cacheName,
                            cacheCtx.cacheObjectContext(),
                            cacheDataRow.key(),
                            cacheDataRow.value(),
                            true
                        );

                        if (isNull(qryTypeDesc))
                            continue; // Tolerate - (k, v) is just not indexed.

                        String tableName = qryTypeDesc.tableName();

                        GridH2Table gridH2Tbl = h2Indexing.schemaManager().dataTable(cacheName, tableName);

                        if (isNull(gridH2Tbl))
                            continue; // Tolerate - (k, v) is just not indexed.

                        cacheSizeByTbl.computeIfAbsent(cacheCtx.cacheId(), i -> new HashMap<>())
                            .computeIfAbsent(tableName, s -> new AtomicLong()).incrementAndGet();
                    }

                    return new CacheSize(null, cacheSizeByTbl);
                }
                catch (Throwable t) {
                    IgniteException cacheSizeErr = new IgniteException("Cache size calculation error [" +
                        cacheGrpInfo(grpCtx) + ", locParId=" + partId + ", err=" + t.getMessage() + "]", t);

                    error(log, cacheSizeErr);

                    failCalcCacheSizeGrpIds.add(grpId);

                    return new CacheSize(cacheSizeErr, null);
                }
                finally {
                    if (reserve)
                        locPart.release();
                }
            }
            finally {
                processedCacheSizePartitions.incrementAndGet();

                printProgressOfIndexValidationIfNeeded();
            }
        });
    }

    /**
     * Asynchronous calculation of the index size for cache.
     *
     * @param cacheCtx Cache context.
     * @param idx      Index.
     * @return Future with index size.
     */
    private Future<T2<Throwable, Long>> calcIndexSizeAsync(
        GridCacheContext cacheCtx,
        Index idx
    ) {
        return calcExecutor.submit(() -> {
            try {
                if (failCalcCacheSizeGrpIds.contains(cacheCtx.groupId()))
                    return new T2<>(null, 0L);

                String cacheName = cacheCtx.name();
                String tblName = idx.getTable().getName();
                String idxName = idx.getName();

                try {
                    long indexSize = ignite.context().query().getIndexing().indexSize(cacheName, tblName, idxName);
                    return new T2<>(null, indexSize);
                }
                catch (Throwable t) {
                    Throwable idxSizeErr = new IgniteException("Index size calculation error [" +
                        cacheGrpInfo(cacheCtx.group()) + ", " + cacheInfo(cacheCtx) + ", tableName=" +
                        tblName + ", idxName=" + idxName + ", err=" + t.getMessage() + "]", t);

                    error(log, idxSizeErr);

                    return new T2<>(idxSizeErr, 0L);
                }
            }
            finally {
                processedIdxSizes.incrementAndGet();

                printProgressOfIndexValidationIfNeeded();
            }
        });
    }

    /**
     * Return cache group info string.
     *
     * @param cacheGrpCtx Cache group context.
     * @return Cache group info string.
     */
    private String cacheGrpInfo(CacheGroupContext cacheGrpCtx) {
        return "cacheGrpName=" + cacheGrpCtx.name() + ", cacheGrpId=" + cacheGrpCtx.groupId();
    }

    /**
     * Return cache info string.
     *
     * @param cacheCtx Cache context.
     * @return Cache info string.
     */
    private String cacheInfo(GridCacheContext cacheCtx) {
        return "cacheName=" + cacheCtx.name() + ", cacheId=" + cacheCtx.cacheId();
    }

    /**
     * Checking size of records in cache and indexes are same.
     *
     * @param cacheSizesFutures Futures calculating size of records in caches.
     * @param idxSizeFutures Futures calculating size of indexes of caches.
     * @return Information about problem records (cache size differ at index size).
     */
    private Map<String, ValidateIndexesCheckSizeResult> checkSizes(
        List<T3<CacheGroupContext, GridDhtLocalPartition, Future<CacheSize>>> cacheSizesFutures,
        List<T3<GridCacheContext, Index, Future<T2<Throwable, Long>>>> idxSizeFutures
    ) throws ExecutionException, InterruptedException {
        Map<String, ValidateIndexesCheckSizeResult> checkSizeRes = new HashMap<>();

        if (!checkSizes)
            return checkSizeRes;

        Map<Integer, CacheSize> cacheSizeTotal = new HashMap<>();

        for (T3<CacheGroupContext, GridDhtLocalPartition, Future<CacheSize>> cacheSizeFut : cacheSizesFutures) {
            CacheGroupContext cacheGrpCtx = cacheSizeFut.get1();
            CacheSize cacheSize = cacheSizeFut.get3().get();

            Throwable cacheSizeErr = cacheSize.err;

            int grpId = cacheGrpCtx.groupId();

            if (failCalcCacheSizeGrpIds.contains(grpId) && nonNull(cacheSizeErr)) {
                checkSizeRes.computeIfAbsent(
                    cacheGrpInfo(cacheGrpCtx),
                    s -> new ValidateIndexesCheckSizeResult(0, new ArrayList<>())
                ).issues().add(new ValidateIndexesCheckSizeIssue(null, 0, cacheSizeErr));
            }
            else {
                cacheSizeTotal.computeIfAbsent(grpId, i -> new CacheSize(null, new HashMap<>()))
                    .merge(cacheSize.cacheSizePerTbl);
            }
        }

        for (T3<GridCacheContext, Index, Future<T2<Throwable, Long>>> idxSizeFut : idxSizeFutures) {
            GridCacheContext cacheCtx = idxSizeFut.get1();

            int grpId = cacheCtx.groupId();

            if (failCalcCacheSizeGrpIds.contains(grpId))
                continue;

            Index idx = idxSizeFut.get2();
            String tblName = idx.getTable().getName();

            AtomicLong cacheSizeObj = cacheSizeTotal.get(grpId).cacheSizePerTbl
                .getOrDefault(cacheCtx.cacheId(), emptyMap()).get(tblName);

            long cacheSizeByTbl = isNull(cacheSizeObj) ? 0L : cacheSizeObj.get();

            T2<Throwable, Long> idxSizeRes = idxSizeFut.get3().get();

            Throwable err = idxSizeRes.get1();
            long idxSize = idxSizeRes.get2();

            if (isNull(err) && idxSize != cacheSizeByTbl)
                err = new IgniteException("Cache and index size not same.");

            if (nonNull(err)) {
                checkSizeRes.computeIfAbsent(
                    "[" + cacheGrpInfo(cacheCtx.group()) + ", " + cacheInfo(cacheCtx) + ", tableName=" + tblName + "]",
                    s -> new ValidateIndexesCheckSizeResult(cacheSizeByTbl, new ArrayList<>()))
                    .issues().add(new ValidateIndexesCheckSizeIssue(idx.getName(), idxSize, err));
            }
        }

        return checkSizeRes;
    }

    /**
     * Container class for calculating the size of cache, divided by tables.
     */
    private static class CacheSize {
        /** Error calculating size of the cache. */
        final Throwable err;

        /** Table split cache size, {@code Map<CacheId, Map<TableName, Size>>}. */
        final Map<Integer, Map<String, AtomicLong>> cacheSizePerTbl;

        /**
         * Constructor.
         *
         * @param err Error calculating size of the cache.
         * @param cacheSizePerTbl Table split cache size.
         */
        public CacheSize(
            @Nullable Throwable err,
            @Nullable Map<Integer, Map<String, AtomicLong>> cacheSizePerTbl
        ) {
            this.err = err;
            this.cacheSizePerTbl = cacheSizePerTbl;
        }

        /**
         * Merging cache sizes separated by tables.
         *
         * @param other Other table split cache size.
         */
        void merge(Map<Integer, Map<String, AtomicLong>> other) {
            assert nonNull(cacheSizePerTbl);

            for (Entry<Integer, Map<String, AtomicLong>> cacheEntry : other.entrySet()) {
                for (Entry<String, AtomicLong> tableEntry : cacheEntry.getValue().entrySet()) {
                    cacheSizePerTbl.computeIfAbsent(cacheEntry.getKey(), i -> new HashMap<>())
                        .computeIfAbsent(tableEntry.getKey(), s -> new AtomicLong())
                        .addAndGet(tableEntry.getValue().get());
                }
            }
        }
    }
}

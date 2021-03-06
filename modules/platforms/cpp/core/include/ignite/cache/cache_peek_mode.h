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

/**
 * @file
 * Declares ignite::cache::CachePeekMode enum.
 */

#ifndef _IGNITE_CACHE_CACHE_PEEK_MODE
#define _IGNITE_CACHE_CACHE_PEEK_MODE

namespace ignite
{
    namespace cache
    {
        /**
         * Enumeration of all supported cache peek modes.
         */
        struct CachePeekMode
        {
            enum Type
            {
                /**
                 * Peeks into all available cache storages.
                 */
                ALL = 0x01,

                /**
                 * Peek into near cache only (don't peek into partitioned cache).
                 * In case of LOCAL cache, behaves as CachePeekMode::ALL mode.
                 */
                NEAR_CACHE = 0x02,

                /**
                 * Peek value from primary copy of partitioned cache only (skip near cache).
                 * In case of LOCAL cache, behaves as CachePeekMode::ALL mode.
                 */
                PRIMARY = 0x04,

                /**
                 * Peek value from backup copies of partitioned cache only (skip near cache).
                 * In case of LOCAL cache, behaves as CachePeekMode::ALL mode.
                 */
                BACKUP = 0x08,

                /**
                 * Peeks value from the on-heap storage only.
                 */
                ONHEAP = 0x10,

                /**
                 * Peeks value from the off-heap storage only, without loading off-heap value into cache.
                 */
                OFFHEAP = 0x20,

                /**
                 * Peeks value from the swap storage only, without loading swapped value into cache.
                 */
                SWAP = 0x40
            };
        };
    }
}

#endif //_IGNITE_CACHE_CACHE_PEEK_MODE
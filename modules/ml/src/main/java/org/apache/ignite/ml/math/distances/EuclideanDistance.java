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
package org.apache.ignite.ml.math.distances;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.apache.ignite.ml.math.exceptions.CardinalityException;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.util.MatrixUtil;

/**
 * Calculates the L<sub>2</sub> (Euclidean) distance between two points.
 */
public class EuclideanDistance implements DistanceMeasure {
    /** Serializable version identifier. */
    private static final long serialVersionUID = 1717556319784040040L;

    /** {@inheritDoc} */
    @Override public double compute(Vector a, Vector b)
        throws CardinalityException {
        return MatrixUtil.localCopyOf(a).minus(b).kNorm(2.0);
    }

    /** {@inheritDoc} */
    @Override public double compute(Vector a, double[] b) throws CardinalityException {
        double res = 0.0;

        for (int i = 0; i < b.length; i++)
            res += Math.pow(Math.abs(b[i] - a.get(i)), 2.0);

        return Math.sqrt(res);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        // No-op
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // No-op
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;

        return obj != null && getClass() == obj.getClass();
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return getClass().hashCode();
    }
}

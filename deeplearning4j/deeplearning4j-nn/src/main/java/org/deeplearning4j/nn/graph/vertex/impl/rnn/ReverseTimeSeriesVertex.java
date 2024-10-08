/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

package org.deeplearning4j.nn.graph.vertex.impl.rnn;

import lombok.val;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.BaseGraphVertex;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.common.primitives.Pair;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

public class ReverseTimeSeriesVertex extends BaseGraphVertex {
    private final String inputName;
    private final int inputIdx;

    public ReverseTimeSeriesVertex(ComputationGraph graph, String name, int vertexIndex, String inputName, DataType dataType) {
        super(graph, name, vertexIndex, null, null, dataType);
        this.inputName = inputName;


        if (inputName == null) {
            // Don't use masks
            this.inputIdx = - 1;
        } else {
            // Find the given input
            this.inputIdx = graph.getConfiguration().getNetworkInputs().indexOf(inputName);
            if (inputIdx == -1)
                throw new IllegalArgumentException("Invalid input name: \"" + inputName + "\" not found in list "
                        + "of network inputs (" + graph.getConfiguration().getNetworkInputs() + ")");
        }
    }

    @Override
    public boolean hasLayer() {
        return false;
    }

    @Override
    public boolean isOutputVertex() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return null;
    }

    @Override
    public INDArray doForward(boolean training, LayerWorkspaceMgr workspaceMgr) {
        // Get the mask arrays for the given input, if any
        final INDArray mask = getMask();

        // Store the input
        final INDArray input = inputs[0];

        // Compute the output
        return workspaceMgr.leverageTo(ArrayType.ACTIVATIONS,revertTimeSeries(input, mask, workspaceMgr, ArrayType.INPUT));
    }

    @Override
    public Pair<Gradient, INDArray[]> doBackward(boolean tbptt, LayerWorkspaceMgr workspaceMgr) {

        // Get the mask arrays for the given input, if any
        INDArray mask = getMask();

        // Backpropagate the output error (epsilon) to the input variables:
        //      Just undo the revert (which can be done by another revert)
        INDArray epsilonsOut = revertTimeSeries(epsilon, mask, workspaceMgr, ArrayType.ACTIVATION_GRAD);

        return new Pair<>(null, new INDArray[] {epsilonsOut});
    }

    /**
     * Gets the current mask array from the provided input
     * @return The mask or null, if no input was provided
     */
    private INDArray getMask() {
        // If no input is provided, no mask is used and null is returned
        if (inputIdx < 0) {
            return null;
        }

        final INDArray[] inputMaskArrays = graph.getInputMaskArrays();
        return (inputMaskArrays != null ? inputMaskArrays[inputIdx] : null);
    }

    /**
     * Reverts the element order of a tensor along the 3rd axis (time series axis).
     * A masking tensor is used to restrict the revert to meaningful elements and keep the padding in place.
     *
     * This method is self-inverse in the following sense:
     * {@code revertTensor( revertTensor (input, mask), mask )}
     * equals
     * {@code input}
     * @param input The input tensor
     * @param mask The masking tensor (1 for meaningful entries, 0 for padding)
     * @return The reverted mask.
     */
    private static INDArray revertTimeSeries(INDArray input, INDArray mask, LayerWorkspaceMgr workspaceMgr, ArrayType type) {
        // Get number of samples
        val n = input.size(0);

        // Get maximal length of a time series
        val m = input.size(2);

        // Create empty output
        INDArray out = workspaceMgr.create(type, input.dataType(), input.shape(), 'f');

        // Iterate over all samples
        for (int s = 0; s < n; s++) {
            long t1 = 0;       // Original time step
            long t2 = m - 1;   // Destination time step

            // Revert Sample: Copy from origin (t1) to destination (t2)
            while (t1 < m && t2 >= 0) {

                // If mask is set: ignore padding
                if (mask != null) {
                    // Origin: find next time step
                    while (t1 < m && mask.getDouble(s, t1) == 0) {
                        t1++;
                    }
                    // Destination: find next time step
                    while (t2 >= 0 && mask.getDouble(s, t2) == 0) {
                        t2--;
                    }
                }

                // Get the feature vector for the given sample and origin time step
                // The vector contains features (forward pass) or errors (backward pass)
                INDArray vec = input.get(
                        NDArrayIndex.point(s),
                        NDArrayIndex.all(),
                        NDArrayIndex.point(t1)
                );

                // Put the feature vector to the given destination in the output
                out.put(new INDArrayIndex[] {
                                NDArrayIndex.point(s),
                                NDArrayIndex.all(),
                                NDArrayIndex.point(t2)
                        },
                        vec
                );

                // Move on
                t1++;
                t2--;
            }
        }

        // Return the output
        return out;
    }

    public void setBackpropGradientsViewArray(INDArray backpropGradientsViewArray) {
        if (backpropGradientsViewArray != null)
            throw new RuntimeException("Vertex does not have gradients; gradients view array cannot be set here");
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArrays(INDArray[] maskArrays, MaskState currentMaskState,
                                                           int minibatchSize){
        if (maskArrays.length > 1) {
            throw new IllegalArgumentException("This vertex can only handle one input and hence only one mask");
        }

        // The mask does not change.
        return new Pair<>(maskArrays[0], currentMaskState);
    }

    @Override
    public String toString() {
        final String paramStr = (inputName == null) ? "" : "inputName=" + inputName;
        return "ReverseTimeSeriesVertex(" + paramStr + ")";
    }


}

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

package org.eclipse.deeplearning4j.dl4jcore.gradientcheck;

import org.deeplearning4j.BaseDL4JTest;
import org.eclipse.deeplearning4j.dl4jcore.TestUtils;
import org.deeplearning4j.gradientcheck.GradientCheckUtil;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(TagNames.NDARRAY_ETL)
@Tag(TagNames.TRAINING)
@Tag(TagNames.DL4J_OLD_API)
@NativeTag
public class NoBiasGradientCheckTests extends BaseDL4JTest {

    private static final boolean PRINT_RESULTS = true;
    private static final boolean RETURN_ON_FIRST_FAILURE = false;
    private static final double DEFAULT_EPS = 1e-6;
    private static final double DEFAULT_MAX_REL_ERROR = 1e-3;
    private static final double DEFAULT_MIN_ABS_ERROR = 1e-8;

    static {
        Nd4j.setDataType(DataType.DOUBLE);
    }

    @Override
    public long getTimeoutMilliseconds() {
        return 90000L;
    }

    @Test
    public void testGradientNoBiasDenseOutput() {

        int nIn = 5;
        int nOut = 3;
        int layerSize = 6;

        for (int minibatch : new int[]{1, 4}) {
            INDArray input = Nd4j.rand(minibatch, nIn);
            INDArray labels = Nd4j.zeros(minibatch, nOut);
            for (int i = 0; i < minibatch; i++) {
                labels.putScalar(i, i % nOut, 1.0);
            }

            for (boolean denseHasBias : new boolean[]{true, false}) {
                for (boolean outHasBias : new boolean[]{true, false}) {

                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                            .dataType(DataType.DOUBLE)
                            .updater(new NoOp())
                            .seed(12345L)
                            .list()
                            .layer(0, new DenseLayer.Builder().nIn(nIn).nOut(layerSize)

                                    .dist(new NormalDistribution(0, 1))
                                    .activation(Activation.TANH)
                                    .hasBias(true)  //Layer 0: Always have a bias
                                    .build())
                            .layer(1, new DenseLayer.Builder().nIn(layerSize).nOut(layerSize)

                                    .dist(new NormalDistribution(0, 1))
                                    .activation(Activation.TANH)
                                    .hasBias(denseHasBias)
                                    .build())
                            .layer(2, new OutputLayer.Builder(LossFunction.MCXENT)
                                    .activation(Activation.SOFTMAX).nIn(layerSize).nOut(nOut)

                                    .dist(new NormalDistribution(0, 1))
                                    .hasBias(outHasBias)
                                    .build())
                            .build();

                    MultiLayerNetwork mln = new MultiLayerNetwork(conf);
                    mln.init();

                    if (denseHasBias) {
                        assertEquals(layerSize * layerSize + layerSize, mln.getLayer(1).numParams());
                    } else {
                        assertEquals(layerSize * layerSize, mln.getLayer(1).numParams());
                    }

                    if (outHasBias) {
                        assertEquals(layerSize * nOut + nOut, mln.getLayer(2).numParams());
                    } else {
                        assertEquals(layerSize * nOut, mln.getLayer(2).numParams());
                    }

                    String msg = "testGradientNoBiasDenseOutput(), minibatch = " + minibatch + ", denseHasBias = "
                            + denseHasBias + ", outHasBias = " + outHasBias + ")";

                    if (PRINT_RESULTS) {
                        System.out.println(msg);
                    }

                    boolean gradOK = GradientCheckUtil.checkGradients(mln, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR,
                            DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                    assertTrue(gradOK, msg);

                    TestUtils.testModelSerialization(mln);
                }
            }
        }
    }

    @Test
    public void testGradientNoBiasRnnOutput() {

        int nIn = 5;
        int nOut = 3;
        int tsLength = 3;
        int layerSize = 6;

        for (int minibatch : new int[]{1, 4}) {
            INDArray input = Nd4j.rand(new int[]{minibatch, nIn, tsLength});
            INDArray labels = TestUtils.randomOneHotTimeSeries(minibatch, nOut, tsLength);

            for (boolean rnnOutHasBias : new boolean[]{true, false}) {

                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .dataType(DataType.DOUBLE)
                        .updater(new NoOp())
                        .seed(12345L)
                        .list()
                        .layer(0, new LSTM.Builder().nIn(nIn).nOut(layerSize)

                                .dist(new NormalDistribution(0, 1))
                                .activation(Activation.TANH)
                                .build())
                        .layer(1, new RnnOutputLayer.Builder(LossFunction.MCXENT)
                                .activation(Activation.SOFTMAX).nIn(layerSize).nOut(nOut)

                                .dist(new NormalDistribution(0, 1))
                                .hasBias(rnnOutHasBias)
                                .build())
                        .build();

                MultiLayerNetwork mln = new MultiLayerNetwork(conf);
                mln.init();

                if (rnnOutHasBias) {
                    assertEquals(layerSize * nOut + nOut, mln.getLayer(1).numParams());
                } else {
                    assertEquals(layerSize * nOut, mln.getLayer(1).numParams());
                }

                String msg = "testGradientNoBiasRnnOutput(), minibatch = " + minibatch + ", rnnOutHasBias = " + rnnOutHasBias + ")";

                if (PRINT_RESULTS) {
                    System.out.println(msg);
                }

                boolean gradOK = GradientCheckUtil.checkGradients(mln, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR,
                        DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                assertTrue(gradOK, msg);

                TestUtils.testModelSerialization(mln);
            }
        }
    }

    @Test
    public void testGradientNoBiasEmbedding() {

        int nIn = 5;
        int nOut = 3;
        int layerSize = 6;

        for (int minibatch : new int[]{1, 4}) {
            INDArray input = Nd4j.zeros(minibatch, 1);
            for (int i = 0; i < minibatch; i++) {
                input.putScalar(i, 0, i % layerSize);
            }
            INDArray labels = Nd4j.zeros(minibatch, nOut);
            for (int i = 0; i < minibatch; i++) {
                labels.putScalar(i, i % nOut, 1.0);
            }

            for (boolean embeddingHasBias : new boolean[]{true, false}) {

                MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .dataType(DataType.DOUBLE)
                        .updater(new NoOp())
                        .seed(12345L)
                        .list()
                        .layer(0, new EmbeddingLayer.Builder().nIn(nIn).nOut(layerSize)

                                .dist(new NormalDistribution(0, 1))
                                .activation(Activation.TANH)
                                .hasBias(embeddingHasBias)
                                .build())
                        .layer(1, new OutputLayer.Builder(LossFunction.MCXENT)
                                .activation(Activation.SOFTMAX).nIn(layerSize).nOut(nOut)

                                .dist(new NormalDistribution(0, 1))
                                .build())
                        .build();

                MultiLayerNetwork mln = new MultiLayerNetwork(conf);
                mln.init();

                if (embeddingHasBias) {
                    assertEquals(nIn * layerSize + layerSize, mln.getLayer(0).numParams());
                } else {
                    assertEquals(nIn * layerSize, mln.getLayer(0).numParams());
                }

                String msg = "testGradientNoBiasEmbedding(), minibatch = " + minibatch + ", embeddingHasBias = "
                        + embeddingHasBias + ")";

                if (PRINT_RESULTS) {
                    System.out.println(msg);
                }

                boolean gradOK = GradientCheckUtil.checkGradients(mln, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR,
                        DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);
                assertTrue(gradOK, msg);

                TestUtils.testModelSerialization(mln);
            }
        }
    }

    @Test
    public void testCnnWithSubsamplingNoBias() {
        int nOut = 4;

        int[] minibatchSizes = {1, 3};
        int width = 5;
        int height = 5;
        int inputDepth = 1;

        long[] kernel = {2, 2};
        long[] stride = {1, 1};
        long[] padding = {0, 0};
        int pNorm = 3;

        for (int minibatchSize : minibatchSizes) {
            INDArray input = Nd4j.rand(minibatchSize, width * height * inputDepth);
            INDArray labels = Nd4j.zeros(minibatchSize, nOut);
            for (int i = 0; i < minibatchSize; i++) {
                labels.putScalar(new int[]{i, i % nOut}, 1.0);
            }

            for(boolean cnnHasBias : new boolean[]{true, false}) {

                MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().updater(new NoOp())
                                .dataType(DataType.DOUBLE)
                                .dist(new NormalDistribution(0, 1))
                                .list()
                                .layer(new ConvolutionLayer.Builder(kernel,
                                        stride, padding).nIn(inputDepth)
                                        .hasBias(false)
                                        .nOut(3).build())//output: (5-2+0)/1+1 = 4
                                .layer(new SubsamplingLayer.Builder(PoolingType.MAX)
                                        .kernelSize(kernel).stride(stride).padding(padding)
                                        .pnorm(pNorm).build()) //output: (4-2+0)/1+1 =3 -> 3x3x3
                                .layer(new ConvolutionLayer.Builder(kernel, stride, padding)
                                        .hasBias(cnnHasBias)
                                        .nOut(2).build()) //Output: (3-2+0)/1+1 = 2
                                .layer(new OutputLayer.Builder(LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX)
                                        .nOut(4).build())
                                .setInputType(InputType.convolutionalFlat(height, width, inputDepth))
                                .build();

                MultiLayerNetwork net = new MultiLayerNetwork(conf);
                net.init();

                if(cnnHasBias){
                    assertEquals(3 * 2 * kernel[0] * kernel[1] + 2, net.getLayer(2).numParams());
                } else {
                    assertEquals(3 * 2 * kernel[0] * kernel[1], net.getLayer(2).numParams());
                }

                String msg = "testCnnWithSubsamplingNoBias(), minibatch = " + minibatchSize + ", cnnHasBias = " + cnnHasBias;
                System.out.println(msg);

                boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR,
                        DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);

                assertTrue(gradOK, msg);

                TestUtils.testModelSerialization(net);
            }
        }
    }

}

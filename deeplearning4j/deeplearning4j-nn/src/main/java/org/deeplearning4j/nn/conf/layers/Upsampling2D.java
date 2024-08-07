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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;
import org.deeplearning4j.nn.conf.CNN2DFormat;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.conf.serde.legacy.LegacyIntArrayDeserializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.ValidationUtils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.databind.annotation.JsonDeserialize;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Upsampling2D extends BaseUpsamplingLayer {

    @JsonDeserialize(using = LegacyIntArrayDeserializer.class)
    protected long[] size;
    protected CNN2DFormat format = CNN2DFormat.NCHW;

    protected Upsampling2D(UpsamplingBuilder builder) {
        super(builder);
        this.size = builder.size;
        this.format = ((Builder)builder).format;
    }

    @Override
    public Upsampling2D clone() {
        Upsampling2D clone = (Upsampling2D) super.clone();
        return clone;
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                                                       Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                                                       boolean initializeParams, DataType networkDataType) {
        org.deeplearning4j.nn.layers.convolution.upsampling.Upsampling2D ret =
                        new org.deeplearning4j.nn.layers.convolution.upsampling.Upsampling2D(conf, networkDataType);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input for Upsampling 2D layer (layer name=\"" + getLayerName()
                            + "\"): Expected CNN input, got " + inputType);
        }
        InputType.InputTypeConvolutional i = (InputType.InputTypeConvolutional) inputType;
        val inHeight = i.getHeight();
        val inWidth = i.getWidth();
        val inDepth = i.getChannels();

        return InputType.convolutional(size[0] * inHeight, size[1] * inWidth, inDepth, i.getFormat());
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input for Upsampling 2D layer (layer name=\"" + getLayerName()
                            + "\"): input is null");
        }
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
        InputType.InputTypeConvolutional outputType = (InputType.InputTypeConvolutional) getOutputType(-1, inputType);

        // During forward pass: im2col array + reduce. Reduce is counted as activations, so only im2col is working mem
        val im2colSizePerEx =
                        c.getChannels() * outputType.getHeight() * outputType.getWidth() * size[0] * size[1];

        // Current implementation does NOT cache im2col etc... which means: it's recalculated on each backward pass
        long trainingWorkingSizePerEx = im2colSizePerEx;
        if (getIDropout() != null) {
            //Dup on the input before dropout, but only for training
            trainingWorkingSizePerEx += inputType.arrayElementsPerExample();
        }

        return new LayerMemoryReport.Builder(layerName, Upsampling2D.class, inputType, outputType).standardMemory(0, 0) //No params
                        .workingMemory(0, im2colSizePerEx, 0, trainingWorkingSizePerEx)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input for Upsampling 2D layer (layer name=\"" + getLayerName()
                    + "\"): Expected CNN input, got " + inputType);
        }
        this.format = ((InputType.InputTypeConvolutional)inputType).getFormat();
    }

    @NoArgsConstructor
    public static class Builder extends UpsamplingBuilder<Builder> {

        protected CNN2DFormat format = CNN2DFormat.NCHW;

        public Builder(int size) {
            super(new int[] {size, size});
        }

        /**
         * Set the data format for the CNN activations - NCHW (channels first) or NHWC (channels last).
         * See {@link CNN2DFormat} for more details.<br>
         * Default: NCHW
         * @param format Format for activations (in and out)
         */
        public Builder dataFormat(CNN2DFormat format){
            this.format = format;
            return this;
        }

        /**
         * Upsampling size int, used for both height and width
         *
         * @param size upsampling size in height and width dimensions
         */
        public Builder size(int size) {

            this.setSize(size, size);
            return this;
        }


        /**
         * Upsampling size array
         *
         * @param size upsampling size in height and width dimensions
         */
        public Builder size(long[] size) {
            this.setSize(size);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Upsampling2D build() {
            return new Upsampling2D(this);
        }

        @Override
        public void setSize(long... size) {
            this.size = ValidationUtils.validate2NonNegativeLong(size, false, "size");
        }
    }

}

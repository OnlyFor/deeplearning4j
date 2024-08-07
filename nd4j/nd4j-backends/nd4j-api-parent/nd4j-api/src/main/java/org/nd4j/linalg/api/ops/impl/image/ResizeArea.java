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
package org.nd4j.linalg.api.ops.impl.image;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class ResizeArea extends DynamicCustomOp {
    protected boolean alignCorners = false;
    protected Integer height = null;
    protected Integer width = null;

    public ResizeArea(@NonNull SameDiff sd, @NonNull SDVariable image, int height, int width,
                          boolean alignCorners) {
        super(sd, image);
        this.alignCorners = alignCorners;
        this.height = height;
        this.width = width;
        addArgs();
    }

    public ResizeArea(@NonNull INDArray x, INDArray z, int height, int width,
                          boolean alignCorners) {
        super(new INDArray[]{x}, new INDArray[]{z});
        this.alignCorners = alignCorners;
        this.height = height;
        this.width = width;
        addArgs();
    }

    @Override
    public String opName() {
        return "resize_area";
    }

    @Override
    public String tensorflowName() {
        return "ResizeArea";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        throw new UnsupportedOperationException("Use the new Tensorflow Importer instead. This method is now removed.");

    }

    protected void addArgs() {
        iArguments.clear();
        if(height != null && width != null) {
            INDArray size = Nd4j.createFromArray(new int[]{height,width});
            addInputArgument(size);
        }
        addBArgument(alignCorners);
    }

    @Override
    public Map<String, Object> propertiesForFunction() {
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("alignCorners", alignCorners);
        ret.put("height", height);
        ret.put("width", width);
        return ret;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && (inputDataTypes.size() == 1 || inputDataTypes.size() == 2),
                "Expected 1 or 2 input datatypes for %s, got %s", getClass(), inputDataTypes);
        return Collections.singletonList(DataType.FLOAT);
    }
}


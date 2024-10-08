/* ******************************************************************************
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

//
// @author raver119@gmail.com
// @author Yurii Shyrma, created on 15.11.2018
//
#include <loops/special_kernels.h>


namespace sd {

////////////////////////////////////////////////////////////////////////
template <typename T>
SD_DEVICE void fillDimensionalIsMax(const void *vdX, void *vdZ, const LongType *zShapeInfo,
                                    const LongType *tadOnlyShapeInfo, LongType *dimension, LongType dimensionLength,
                                    const LongType *tadOffsets) {
  auto dX = reinterpret_cast<const LongType *>(vdX);
  auto dZ = reinterpret_cast<T *>(vdZ);

  __shared__ int tadLength;
  __shared__ int tadEWS;
  __shared__ int numTads;

  if (threadIdx.x == 0) {
    tadLength = shape::length(tadOnlyShapeInfo);
    tadEWS = shape::elementWiseStride(tadOnlyShapeInfo);
    numTads = shape::length(zShapeInfo) / tadLength;
  }
  __syncthreads();

  for (int r = blockIdx.x; r < numTads; r += gridDim.x) {
    auto tadOffsetForBlock = tadOffsets[r];
    auto highestElement = dX[r];

    if (dimensionLength > 1 || tadEWS < 1) {
      for (LongType e = threadIdx.x; e < tadLength; e += blockDim.x) {
        auto xOffset = tadOffsetForBlock + shape::getIndexOffset(e, tadOnlyShapeInfo);
        dZ[xOffset] = (e == highestElement ? (T)1 : (T)0);
      }
    } else {
      for (LongType e = threadIdx.x; e < tadLength; e += blockDim.x) {
        // so, we just set dZ[e] for each TAD. Sure, e should be replaced with
        auto idx = tadOffsetForBlock + (e * tadEWS);
        dZ[idx] = (e == highestElement ? (T)1 : (T)0);
      }
    }
  }
}

////////////////////////////////////////////////////////////////////////
template <typename T>
SD_KERNEL void execfillDimensionalIsMax(const void *dX, void *dZ, const LongType *zShapeInfo,
                                        const LongType *tadOnlyShapeInfo, LongType *dimension, LongType dimensionLength,
                                        const LongType *tadOffsets) {
  fillDimensionalIsMax<T>(dX, dZ, zShapeInfo, tadOnlyShapeInfo, dimension, dimensionLength, tadOffsets);
}

////////////////////////////////////////////////////////////////////////
template <typename T>
SD_HOST void fillDimensionalIsMaxGeneric(dim3 &launchDims, cudaStream_t *stream, const void *dX, void *dZ,
                                         const LongType *zShapeInfo, const LongType *tadOnlyShapeInfo,
                                         LongType *dimension, LongType dimensionLength, const LongType *tadOffsets) {
  execfillDimensionalIsMax<T><<<launchDims.x, launchDims.y, launchDims.z, *stream>>>(
      dX, dZ, zShapeInfo, tadOnlyShapeInfo, dimension, dimensionLength, tadOffsets);
  DebugHelper::checkErrorCode(stream, "fillDimensionalIsMax(...) failed");
}
BUILD_SINGLE_TEMPLATE(template void fillDimensionalIsMaxGeneric,
                      (dim3 & launchDims, cudaStream_t *stream, const void *dX, void *dZ,
                       const sd::LongType *zShapeInfo, const sd::LongType *tadOnlyShapeInfo, sd::LongType *dimension,
                       sd::LongType dimensionLength, const sd::LongType *tadOffsets),
                      SD_COMMON_TYPES);
}  // namespace sd

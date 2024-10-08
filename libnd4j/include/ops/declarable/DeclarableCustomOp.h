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
// Created by raver119 on 07.10.2017.
//

#ifndef LIBND4J_DECLARABLECUSTOMOP_H
#define LIBND4J_DECLARABLECUSTOMOP_H
#include <ops/declarable/DeclarableOp.h>

namespace sd {
namespace ops {
class SD_LIB_EXPORT DeclarableCustomOp : public DeclarableOp {
 protected:
  /**
   * This method executes this Op
   */
  Status validateAndExecute(sd::graph::Context& block) override = 0;

 public:
  DeclarableCustomOp(int numInputs, int numOutputs, const char* opName, bool allowsInplace, int tArgs, int iArgs);

  ShapeList* calculateOutputShape(ShapeList* inputShapes, Context& block) override = 0;
};
}  // namespace ops
}  // namespace sd

#endif  // LIBND4J_DECLARABLECUSTOMOP_H

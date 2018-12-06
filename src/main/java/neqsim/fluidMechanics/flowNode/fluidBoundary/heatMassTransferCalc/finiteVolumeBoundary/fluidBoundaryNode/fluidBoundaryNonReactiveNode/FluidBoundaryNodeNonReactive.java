/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * FluidBoundaryNodeReactive.java
 *
 * Created on 8. august 2001, 14:50
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.fluidBoundaryNonReactiveNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundaryNode.FluidBoundaryNode;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  esol
 * @version
 */
public class FluidBoundaryNodeNonReactive extends FluidBoundaryNode{

    private static final long serialVersionUID = 1000;
    
    /** Creates new FluidBoundaryNodeReactive */
    public FluidBoundaryNodeNonReactive() {
    }
    
    public FluidBoundaryNodeNonReactive(SystemInterface system) {
        super(system);
    }
    
}

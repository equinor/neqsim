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

package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem;

import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;


public abstract class OnePhaseFlowSystem extends FlowSystem
{

    private static final long serialVersionUID = 1000;
    
    //	public FluidMechanicsInterface[] flowNode;
    public PipeData pipe;
    
    
    public OnePhaseFlowSystem(){
    }
    
    public OnePhaseFlowSystem(SystemInterface system){
    }
    
    
    public static void main(String[] args)
    {
        System.out.println("Hei der!");
    }
}
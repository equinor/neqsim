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

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.equilibriumFluidBoundary;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode.AnnularFlow;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class EquilibriumFluidBoundary
        extends neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundary {

    private static final long serialVersionUID = 1000;

    public EquilibriumFluidBoundary() {
    }

    public EquilibriumFluidBoundary(SystemInterface system) {
        super(system);
        interphaseOps = new ThermodynamicOperations(interphaseSystem);
        // interphaseOps.TPflash();
    }

    public EquilibriumFluidBoundary(FlowNodeInterface flowNode) {
        super(flowNode);
        interphaseOps = new ThermodynamicOperations(interphaseSystem);
        // interphaseOps.TPflash();
    }

    public void init() {
        super.init();
    }

    public void solve() {
        getInterphaseOpertions().TPflash();
        getBulkSystemOpertions().TPflash();
    }

    public double[] calcFluxes() {
        for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
            nFlux.set(i, 0, 0);
        }
        return nFlux.getArray()[0];
    }

    public static void main(String[] args) {
        System.out.println("Starter.....");
        SystemSrkEos testSystem = new SystemSrkEos(295.3, 11.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        PipeData pipe1 = new PipeData(1.0, 0.55);

        testSystem.addComponent("methane", 100.152181, 1);
        testSystem.addComponent("water", 10.362204876, 0);
        testSystem.setMixingRule(2);

        FlowNodeInterface test = new AnnularFlow(testSystem, pipe1);
        test.init();

        EquilibriumFluidBoundary test2 = new EquilibriumFluidBoundary(test);
        test2.solve();

    }

}

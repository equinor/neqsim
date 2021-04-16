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
 * FlowSystemInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */
package neqsim.fluidMechanics.flowSystem;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowSolver.FlowSolverInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.util.fluidMechanicsDataHandeling.FileWriterInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualizationInterface;
import neqsim.fluidMechanics.util.timeSeries.TimeSeries;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface FlowSystemInterface {

    public void init();

    public void setNodes();

    public void solveTransient(int type);

    public TimeSeries getTimeSeries();

    public FlowSystemVisualizationInterface getDisplay();

    public FileWriterInterface getFileWriter(int i);

    public FlowSolverInterface getSolver();

    public double getInletTemperature();

    public double getInletPressure();

    public void setNumberOfLegs(int numberOfLegs);

    public int getNumberOfLegs();

    public void setNumberOfNodesInLeg(int numberOfNodesInLeg);

    public int getNumberOfNodesInLeg(int i);

    public void setLegHeights(double[] legHeights);

    public double[] getLegHeights();

    public void setLegPositions(double[] legPositions);

    public void createSystem();

    public FlowNodeInterface getNode(int i);

    public double getSystemLength();

    public void setLegOuterHeatTransferCoefficients(double[] coefs);

    public void setLegWallHeatTransferCoefficients(double[] coefs);

    public void setEquipmentGeometry(GeometryDefinitionInterface[] equipmentGeometry);

    public int getTotalNumberOfNodes();

    public void calcFluxes();

    public void setEndPressure(double inletPressure);

    public void setInletThermoSystem(SystemInterface thermoSystem);

    public void solveSteadyState(int type);

    public FlowNodeInterface[] getFlowNodes();

    public void print();

    public void setLegOuterTemperatures(double[] temps);

    public double getTotalMolarMassTransferRate(int component);

    public double getTotalMolarMassTransferRate(int component, int lastNode);

    public double getTotalPressureDrop();

    public double getTotalPressureDrop(int lastNode);

    public void setInitialFlowPattern(String flowPattern);

    public void setFlowPattern(String flowPattern);

    public void setEquilibriumMassTransfer(boolean test);

    public void setEquilibriumHeatTransfer(boolean test);
}

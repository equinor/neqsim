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

package neqsim.fluidMechanics.flowNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.util.DoubleCloneable;

public interface FlowNodeInterface extends Cloneable {
    
    public SystemInterface getBulkSystem();
    public FluidBoundaryInterface getFluidBoundary();
    public SystemInterface getInterphaseSystem();
    public void init();
    public double getVelocity();
    public void setInterphaseModelType(int i);
    public double getWallFrictionFactor();
    public double calcStantonNumber(double schmidtNumber, int phase);
    public double getHydraulicDiameter(int i);
    public double getWallFrictionFactor(int phase);
    public void write(String name, String filename, boolean newfile);
    public void setVelocity(int phase, double vel);
    public void increaseMolarRate(double moles);
    public double getSchmidtNumber(int phase, int component1, int component2);
    public double getEffectiveSchmidtNumber(int phase, int component);
    public double getReynoldsNumber(int i);
    public FlowNodeInterface getNextNode();
    public neqsim.thermodynamicOperations.ThermodynamicOperations getOperations();
    public double getDistanceToCenterOfNode();
    public void update();
    public void setEnhancementType(int type);
    public double getMolarMassTransferRate(int componentNumber);
    public void setVelocityOut(DoubleCloneable vel);
    public InterphaseTransportCoefficientInterface getInterphaseTransportCoefficient();
    public double getPhaseFraction(int phase);
    public double getInterphaseContactArea();
    public void setFrictionFactorType(int type);
    public void setPhaseFraction(int phase, double frac);
    public void setVelocityOut(int phase, double vel);
    public double getWallContactLength(int phase);
    public double getInterphaseContactLength(int phase);
    public void setVelocityIn(int phase, double vel);
    public double getMassFlowRate(int phase);
    public double getInterPhaseFrictionFactor();
    public void setVelocityIn(int phase, DoubleCloneable vel);
    public void setVelocityOut(int phase, DoubleCloneable vel);
    public void setDistanceToCenterOfNode(double lengthOfNode);
    public void setLengthOfNode(double lengthOfNode);
    public double getLengthOfNode();
    public double getVolumetricFlow();
    public DoubleCloneable getVelocityOut();
    public double getArea(int i);
    public void updateMolarFlow();
    public DoubleCloneable getVelocityIn();
    public double getPrandtlNumber(int phase);
    public void setVelocityIn(double vel);
    public void setVelocityIn(DoubleCloneable vel);
    public void initFlowCalc();
    public double getVelocity(int phase);
    public double calcSherwoodNumber(double schmidtNumber, int phase);
    public double calcNusseltNumber(double prandtlNumber, int phase);
    public void calcFluxes();
    public double getSuperficialVelocity(int i);
    public double getReynoldsNumber();
    public GeometryDefinitionInterface getGeometry();
    public void setGeometryDefinitionInterface(GeometryDefinitionInterface pipe);
    public void setFluxes(double dn[]);
    public void setInterphaseSystem(SystemInterface interphaseSystem);
    public void setBulkSystem(SystemInterface bulkSystem);
    public double getVerticalPositionOfNode();
    public int getFlowDirection(int i);
    public void setFlowDirection(int flowDirection, int i);
    public void setVerticalPositionOfNode(double position);
    public String getFlowNodeType();
    public DoubleCloneable getVelocityOut(int i);
    public void setVelocity(double vel);
    //  public double getVelocityOut();
    //  public double getVelocityIn();
    public void setVelocityOut(double vel);
    public DoubleCloneable getVelocityIn(int i);
    //   public double calcWallHeatTransferCoeffisient(int phase);
    //   public double calcWallMassTransferCoeffisient(double schmidtNumber, int phase);
    public double calcTotalHeatTransferCoefficient(int phase);
    // public double initVelocity();
    //   public double calcInterphaseMassTransferCoeffisient(double schmidtNumber, int phase);
    //   public double calcInterphaseHeatTransferCoeffisient(int phase);
    // public double calcdPdz();
    //  public double calcdTdz();
    //   public double calcdVoiddz();
    //	public double[] calcdxdz();
    public void initBulkSystem();
    public void display();
    public void display(String name);
}
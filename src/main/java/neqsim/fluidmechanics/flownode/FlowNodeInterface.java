package neqsim.fluidmechanics.flownode;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.InterphaseTransportCoefficientInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.util.DoubleCloneable;

/**
 * FlowNodeInterface interface.
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface FlowNodeInterface extends Cloneable {
  /**
   * getBulkSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBulkSystem();

  /**
   * getFluidBoundary.
   *
   * @return a {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface} object
   */
  public FluidBoundaryInterface getFluidBoundary();

  /**
   * getInterphaseSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getInterphaseSystem();

  /**
   * init.
   */
  public void init();

  /**
   * initBulkSystem.
   */
  public void initBulkSystem();

  /**
   * getVelocity.
   *
   * @return a double
   */
  public default double getVelocity() {
    return getVelocity(0);
  }

  /**
   * getVelocity.
   *
   * @param phase a int
   * @return a double
   */
  public double getVelocity(int phase);

  /**
   * getVelocityIn.
   *
   * @return a {@link neqsim.util.util.DoubleCloneable} object
   */
  public default DoubleCloneable getVelocityIn() {
    return getVelocityIn(0);
  }

  /**
   * getVelocityIn.
   *
   * @param i a int
   * @return a {@link neqsim.util.util.DoubleCloneable} object
   */
  public DoubleCloneable getVelocityIn(int i);

  /**
   * getVelocityOut.
   *
   * @return a {@link neqsim.util.util.DoubleCloneable} object
   */
  public default DoubleCloneable getVelocityOut() {
    return getVelocityOut(0);
  }

  /**
   * getVelocityOut.
   *
   * @param i a int
   * @return a {@link neqsim.util.util.DoubleCloneable} object
   */
  public DoubleCloneable getVelocityOut(int i);

  /**
   * setInterphaseModelType.
   *
   * @param i a int
   */
  public void setInterphaseModelType(int i);

  /**
   * getWallFrictionFactor.
   *
   * @return a double
   */
  public default double getWallFrictionFactor() {
    return getWallFrictionFactor(0);
  }

  /**
   * getWallFrictionFactor.
   *
   * @param phase a int
   * @return a double
   */
  public double getWallFrictionFactor(int phase);

  /**
   * calcStantonNumber.
   *
   * @param schmidtNumber a double
   * @param phase a int
   * @return a double
   */
  public double calcStantonNumber(double schmidtNumber, int phase);

  /**
   * getHydraulicDiameter.
   *
   * @param i a int
   * @return a double
   */
  public double getHydraulicDiameter(int i);

  /**
   * write.
   *
   * @param name a {@link java.lang.String} object
   * @param filename a {@link java.lang.String} object
   * @param newfile a boolean
   */
  public void write(String name, String filename, boolean newfile);

  /**
   * setVelocity.
   *
   * @param vel a double
   */
  public default void setVelocity(double vel) {
    setVelocity(0, vel);
  }

  /**
   * setVelocity.
   *
   * @param phase a int
   * @param vel a double
   */
  public void setVelocity(int phase, double vel);

  /**
   * increaseMolarRate.
   *
   * @param moles a double
   */
  public void increaseMolarRate(double moles);

  /**
   * getSchmidtNumber.
   *
   * @param phase a int
   * @param component1 a int
   * @param component2 a int
   * @return a double
   */
  public double getSchmidtNumber(int phase, int component1, int component2);

  /**
   * getEffectiveSchmidtNumber.
   *
   * @param phase a int
   * @param component a int
   * @return a double
   */
  public double getEffectiveSchmidtNumber(int phase, int component);

  /**
   * getReynoldsNumber.
   *
   * @return a double
   */
  public default double getReynoldsNumber() {
    return getReynoldsNumber(0);
  }

  /**
   * getReynoldsNumber.
   *
   * @param i a int
   * @return a double
   */
  public double getReynoldsNumber(int i);

  /**
   * getNextNode.
   *
   * @return a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public FlowNodeInterface getNextNode();

  /**
   * Getter for property operations.
   *
   * @return a {@link neqsim.thermodynamicoperations.ThermodynamicOperations} object
   */
  public neqsim.thermodynamicoperations.ThermodynamicOperations getOperations();

  /**
   * getDistanceToCenterOfNode.
   *
   * @return a double
   */
  public double getDistanceToCenterOfNode();

  /**
   * update.
   */
  public void update();

  /**
   * setEnhancementType.
   *
   * @param type a int
   */
  public void setEnhancementType(int type);

  /**
   * getMolarMassTransferRate.
   *
   * @param componentNumber a int
   * @return a double
   */
  public double getMolarMassTransferRate(int componentNumber);

  /**
   * setVelocityOut.
   *
   * @param vel a double
   */
  public default void setVelocityOut(double vel) {
    setVelocityOut(0, vel);
  }

  /**
   * setVelocityOut.
   *
   * @param phase a int
   * @param vel a double
   */
  public void setVelocityOut(int phase, double vel);

  /**
   * setVelocityOut.
   *
   * @param vel a {@link neqsim.util.util.DoubleCloneable} object
   */
  public default void setVelocityOut(DoubleCloneable vel) {
    setVelocityOut(0, vel);
  }

  /**
   * setVelocityOut.
   *
   * @param phase a int
   * @param vel a {@link neqsim.util.util.DoubleCloneable} object
   */
  public void setVelocityOut(int phase, DoubleCloneable vel);

  /**
   * getInterphaseTransportCoefficient.
   *
   * @return a
   * {@link neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.InterphaseTransportCoefficientInterface}
   * object
   */
  public InterphaseTransportCoefficientInterface getInterphaseTransportCoefficient();

  /**
   * getPhaseFraction.
   *
   * @param phase a int
   * @return a double
   */
  public double getPhaseFraction(int phase);

  /**
   * getInterphaseContactArea.
   *
   * @return a double
   */
  public double getInterphaseContactArea();

  /**
   * setFrictionFactorType.
   *
   * @param type a int
   */
  public void setFrictionFactorType(int type);

  /**
   * setPhaseFraction.
   *
   * @param phase a int
   * @param frac a double
   */
  public void setPhaseFraction(int phase, double frac);

  /**
   * getWallContactLength.
   *
   * @param phase a int
   * @return a double
   */
  public double getWallContactLength(int phase);

  /**
   * getInterphaseContactLength.
   *
   * @param phase a int
   * @return a double
   */
  public double getInterphaseContactLength(int phase);

  /**
   * getMassFlowRate.
   *
   * @param phase a int
   * @return a double
   */
  public double getMassFlowRate(int phase);

  /**
   * getInterPhaseFrictionFactor.
   *
   * @return a double
   */
  public double getInterPhaseFrictionFactor();

  /**
   * setDistanceToCenterOfNode.
   *
   * @param lengthOfNode a double
   */
  public void setDistanceToCenterOfNode(double lengthOfNode);

  /**
   * setLengthOfNode.
   *
   * @param lengthOfNode a double
   */
  public void setLengthOfNode(double lengthOfNode);

  /**
   * getLengthOfNode.
   *
   * @return a double
   */
  public double getLengthOfNode();

  /**
   * getVolumetricFlow.
   *
   * @return a double
   */
  public double getVolumetricFlow();

  /**
   * getArea.
   *
   * @param i a int
   * @return a double
   */
  public double getArea(int i);

  /**
   * updateMolarFlow.
   */
  public void updateMolarFlow();

  /**
   * getPrandtlNumber.
   *
   * @param phase a int
   * @return a double
   */
  public double getPrandtlNumber(int phase);

  /**
   * setVelocityIn.
   *
   * @param vel a double
   */
  public default void setVelocityIn(double vel) {
    setVelocityIn(0, vel);
  }

  /**
   * setVelocityIn.
   *
   * @param phase a int
   * @param vel a double
   */
  public void setVelocityIn(int phase, double vel);

  /**
   * setVelocityIn.
   *
   * @param vel a {@link neqsim.util.util.DoubleCloneable} object
   */
  public default void setVelocityIn(DoubleCloneable vel) {
    setVelocityIn(0, vel);
  }

  /**
   * setVelocityIn.
   *
   * @param phase a int
   * @param vel a {@link neqsim.util.util.DoubleCloneable} object
   */
  public void setVelocityIn(int phase, DoubleCloneable vel);

  /**
   * initFlowCalc.
   */
  public void initFlowCalc();

  /**
   * calcSherwoodNumber.
   *
   * @param schmidtNumber a double
   * @param phase a int
   * @return a double
   */
  public double calcSherwoodNumber(double schmidtNumber, int phase);

  /**
   * calcNusseltNumber.
   *
   * @param prandtlNumber a double
   * @param phase a int
   * @return a double
   */
  public double calcNusseltNumber(double prandtlNumber, int phase);

  /**
   * calcFluxes.
   */
  public void calcFluxes();

  /**
   * getSuperficialVelocity.
   *
   * @param i a int
   * @return a double
   */
  public double getSuperficialVelocity(int i);

  /**
   * getGeometry.
   *
   * @return a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} object
   */
  public GeometryDefinitionInterface getGeometry();

  /**
   * setGeometryDefinitionInterface.
   *
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface} object
   */
  public void setGeometryDefinitionInterface(GeometryDefinitionInterface pipe);

  /**
   * setFluxes.
   *
   * @param dn an array of type double
   */
  public void setFluxes(double[] dn);

  /**
   * setInterphaseSystem.
   *
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInterphaseSystem(SystemInterface interphaseSystem);

  /**
   * setBulkSystem.
   *
   * @param bulkSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setBulkSystem(SystemInterface bulkSystem);

  /**
   * getVerticalPositionOfNode.
   *
   * @return a double
   */
  public double getVerticalPositionOfNode();

  /**
   * Getter for property flowDirection.
   *
   * @param i a int
   * @return a int
   */
  public int getFlowDirection(int i);

  /**
   * Setter for property flowDirection.
   *
   * @param flowDirection a int
   * @param i a int
   */
  public void setFlowDirection(int flowDirection, int i);

  /**
   * setVerticalPositionOfNode.
   *
   * @param position a double
   */
  public void setVerticalPositionOfNode(double position);

  /**
   * getFlowNodeType.
   *
   * @return a {@link java.lang.String} object
   */
  public String getFlowNodeType();

  // public double calcWallHeatTransferCoefficient(int phase);

  // public double calcWallMassTransferCoefficient(double schmidtNumber, int phase);

  /**
   * calcTotalHeatTransferCoefficient.
   *
   * @param phase a int
   * @return a double
   */
  public double calcTotalHeatTransferCoefficient(int phase);

  // public double initVelocity();
  // public double calcInterphaseMassTransferCoefficient(double schmidtNumber, int
  // phase);
  // public double calcInterphaseHeatTransferCoefficient(int phase);
  // public double calcdPdz();
  // public double calcdTdz();
  // public double calcdVoiddz();
  // public double[] calcdxdz();

  /**
   * display.
   */
  public default void display() {
    display("");
  }

  /**
   * display.
   *
   * @param name a {@link java.lang.String} object
   */
  public void display(String name);

  /**
   * Specify wall friction factor for phase 0. Set to null to reset.
   *
   * @param frictionFactor Friction factor to use for phase 0 or null to reset.
   */
  public default void setWallFrictionFactor(double frictionFactor) {
    setWallFrictionFactor(0, frictionFactor);
  }

  /**
   * Specify wall friction factor for a given phase. Set to null to reset.
   *
   * @param phase Index to phase to set wall friction factor for.
   * @param frictionFactor Friction factor to use for a given phase or null to reset.
   */
  public void setWallFrictionFactor(int phase, double frictionFactor);
}

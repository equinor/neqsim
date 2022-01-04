package neqsim.fluidMechanics.flowNode;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.util.DoubleCloneable;

/**
 * <p>
 * FlowNodeInterface interface.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface FlowNodeInterface extends Cloneable {
    /**
     * <p>
     * getBulkSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getBulkSystem();

    /**
     * <p>
     * getFluidBoundary.
     * </p>
     *
     * @return a
     *         {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface}
     *         object
     */
    public FluidBoundaryInterface getFluidBoundary();

    /**
     * <p>
     * getInterphaseSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getInterphaseSystem();

    /**
     * <p>
     * init.
     * </p>
     */
    public void init();

    /**
     * <p>
     * getVelocity.
     * </p>
     *
     * @return a double
     */
    public double getVelocity();

    /**
     * <p>
     * setInterphaseModelType.
     * </p>
     *
     * @param i a int
     */
    public void setInterphaseModelType(int i);

    /**
     * <p>
     * getWallFrictionFactor.
     * </p>
     *
     * @return a double
     */
    public double getWallFrictionFactor();

    /**
     * <p>
     * calcStantonNumber.
     * </p>
     *
     * @param schmidtNumber a double
     * @param phase a int
     * @return a double
     */
    public double calcStantonNumber(double schmidtNumber, int phase);

    /**
     * <p>
     * getHydraulicDiameter.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getHydraulicDiameter(int i);

    /**
     * <p>
     * getWallFrictionFactor.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getWallFrictionFactor(int phase);

    /**
     * <p>
     * write.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param filename a {@link java.lang.String} object
     * @param newfile a boolean
     */
    public void write(String name, String filename, boolean newfile);

    /**
     * <p>
     * setVelocity.
     * </p>
     *
     * @param phase a int
     * @param vel a double
     */
    public void setVelocity(int phase, double vel);

    /**
     * <p>
     * increaseMolarRate.
     * </p>
     *
     * @param moles a double
     */
    public void increaseMolarRate(double moles);

    /**
     * <p>
     * getSchmidtNumber.
     * </p>
     *
     * @param phase a int
     * @param component1 a int
     * @param component2 a int
     * @return a double
     */
    public double getSchmidtNumber(int phase, int component1, int component2);

    /**
     * <p>
     * getEffectiveSchmidtNumber.
     * </p>
     *
     * @param phase a int
     * @param component a int
     * @return a double
     */
    public double getEffectiveSchmidtNumber(int phase, int component);

    /**
     * <p>
     * getReynoldsNumber.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getReynoldsNumber(int i);

    /**
     * <p>
     * getNextNode.
     * </p>
     *
     * @return a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public FlowNodeInterface getNextNode();

    /**
     * <p>
     * getOperations.
     * </p>
     *
     * @return a {@link neqsim.thermodynamicOperations.ThermodynamicOperations} object
     */
    public neqsim.thermodynamicOperations.ThermodynamicOperations getOperations();

    /**
     * <p>
     * getDistanceToCenterOfNode.
     * </p>
     *
     * @return a double
     */
    public double getDistanceToCenterOfNode();

    /**
     * <p>
     * update.
     * </p>
     */
    public void update();

    /**
     * <p>
     * setEnhancementType.
     * </p>
     *
     * @param type a int
     */
    public void setEnhancementType(int type);

    /**
     * <p>
     * getMolarMassTransferRate.
     * </p>
     *
     * @param componentNumber a int
     * @return a double
     */
    public double getMolarMassTransferRate(int componentNumber);

    /**
     * <p>
     * setVelocityOut.
     * </p>
     *
     * @param vel a {@link neqsim.util.util.DoubleCloneable} object
     */
    public void setVelocityOut(DoubleCloneable vel);

    /**
     * <p>
     * getInterphaseTransportCoefficient.
     * </p>
     *
     * @return a
     *         {@link neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientInterface}
     *         object
     */
    public InterphaseTransportCoefficientInterface getInterphaseTransportCoefficient();

    /**
     * <p>
     * getPhaseFraction.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getPhaseFraction(int phase);

    /**
     * <p>
     * getInterphaseContactArea.
     * </p>
     *
     * @return a double
     */
    public double getInterphaseContactArea();

    /**
     * <p>
     * setFrictionFactorType.
     * </p>
     *
     * @param type a int
     */
    public void setFrictionFactorType(int type);

    /**
     * <p>
     * setPhaseFraction.
     * </p>
     *
     * @param phase a int
     * @param frac a double
     */
    public void setPhaseFraction(int phase, double frac);

    /**
     * <p>
     * setVelocityOut.
     * </p>
     *
     * @param phase a int
     * @param vel a double
     */
    public void setVelocityOut(int phase, double vel);

    /**
     * <p>
     * getWallContactLength.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getWallContactLength(int phase);

    /**
     * <p>
     * getInterphaseContactLength.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getInterphaseContactLength(int phase);

    /**
     * <p>
     * setVelocityIn.
     * </p>
     *
     * @param phase a int
     * @param vel a double
     */
    public void setVelocityIn(int phase, double vel);

    /**
     * <p>
     * getMassFlowRate.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getMassFlowRate(int phase);

    /**
     * <p>
     * getInterPhaseFrictionFactor.
     * </p>
     *
     * @return a double
     */
    public double getInterPhaseFrictionFactor();

    /**
     * <p>
     * setVelocityIn.
     * </p>
     *
     * @param phase a int
     * @param vel a {@link neqsim.util.util.DoubleCloneable} object
     */
    public void setVelocityIn(int phase, DoubleCloneable vel);

    /**
     * <p>
     * setVelocityOut.
     * </p>
     *
     * @param phase a int
     * @param vel a {@link neqsim.util.util.DoubleCloneable} object
     */
    public void setVelocityOut(int phase, DoubleCloneable vel);

    /**
     * <p>
     * setDistanceToCenterOfNode.
     * </p>
     *
     * @param lengthOfNode a double
     */
    public void setDistanceToCenterOfNode(double lengthOfNode);

    /**
     * <p>
     * setLengthOfNode.
     * </p>
     *
     * @param lengthOfNode a double
     */
    public void setLengthOfNode(double lengthOfNode);

    /**
     * <p>
     * getLengthOfNode.
     * </p>
     *
     * @return a double
     */
    public double getLengthOfNode();

    /**
     * <p>
     * getVolumetricFlow.
     * </p>
     *
     * @return a double
     */
    public double getVolumetricFlow();

    /**
     * <p>
     * getVelocityOut.
     * </p>
     *
     * @return a {@link neqsim.util.util.DoubleCloneable} object
     */
    public DoubleCloneable getVelocityOut();

    /**
     * <p>
     * getArea.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getArea(int i);

    /**
     * <p>
     * updateMolarFlow.
     * </p>
     */
    public void updateMolarFlow();

    /**
     * <p>
     * getVelocityIn.
     * </p>
     *
     * @return a {@link neqsim.util.util.DoubleCloneable} object
     */
    public DoubleCloneable getVelocityIn();

    /**
     * <p>
     * getPrandtlNumber.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getPrandtlNumber(int phase);

    /**
     * <p>
     * setVelocityIn.
     * </p>
     *
     * @param vel a double
     */
    public void setVelocityIn(double vel);

    /**
     * <p>
     * setVelocityIn.
     * </p>
     *
     * @param vel a {@link neqsim.util.util.DoubleCloneable} object
     */
    public void setVelocityIn(DoubleCloneable vel);

    /**
     * <p>
     * initFlowCalc.
     * </p>
     */
    public void initFlowCalc();

    /**
     * <p>
     * getVelocity.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getVelocity(int phase);

    /**
     * <p>
     * calcSherwoodNumber.
     * </p>
     *
     * @param schmidtNumber a double
     * @param phase a int
     * @return a double
     */
    public double calcSherwoodNumber(double schmidtNumber, int phase);

    /**
     * <p>
     * calcNusseltNumber.
     * </p>
     *
     * @param prandtlNumber a double
     * @param phase a int
     * @return a double
     */
    public double calcNusseltNumber(double prandtlNumber, int phase);

    /**
     * <p>
     * calcFluxes.
     * </p>
     */
    public void calcFluxes();

    /**
     * <p>
     * getSuperficialVelocity.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getSuperficialVelocity(int i);

    /**
     * <p>
     * getReynoldsNumber.
     * </p>
     *
     * @return a double
     */
    public double getReynoldsNumber();

    /**
     * <p>
     * getGeometry.
     * </p>
     *
     * @return a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
     *         object
     */
    public GeometryDefinitionInterface getGeometry();

    /**
     * <p>
     * setGeometryDefinitionInterface.
     * </p>
     *
     * @param pipe a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
     *        object
     */
    public void setGeometryDefinitionInterface(GeometryDefinitionInterface pipe);

    /**
     * <p>
     * setFluxes.
     * </p>
     *
     * @param dn an array of {@link double} objects
     */
    public void setFluxes(double dn[]);

    /**
     * <p>
     * setInterphaseSystem.
     * </p>
     *
     * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setInterphaseSystem(SystemInterface interphaseSystem);

    /**
     * <p>
     * setBulkSystem.
     * </p>
     *
     * @param bulkSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void setBulkSystem(SystemInterface bulkSystem);

    /**
     * <p>
     * getVerticalPositionOfNode.
     * </p>
     *
     * @return a double
     */
    public double getVerticalPositionOfNode();

    /**
     * <p>
     * getFlowDirection.
     * </p>
     *
     * @param i a int
     * @return a int
     */
    public int getFlowDirection(int i);

    /**
     * <p>
     * setFlowDirection.
     * </p>
     *
     * @param flowDirection a int
     * @param i a int
     */
    public void setFlowDirection(int flowDirection, int i);

    /**
     * <p>
     * setVerticalPositionOfNode.
     * </p>
     *
     * @param position a double
     */
    public void setVerticalPositionOfNode(double position);

    /**
     * <p>
     * getFlowNodeType.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getFlowNodeType();

    /**
     * <p>
     * getVelocityOut.
     * </p>
     *
     * @param i a int
     * @return a {@link neqsim.util.util.DoubleCloneable} object
     */
    public DoubleCloneable getVelocityOut(int i);

    /**
     * <p>
     * setVelocity.
     * </p>
     *
     * @param vel a double
     */
    public void setVelocity(double vel);

    // public double getVelocityOut();
    // public double getVelocityIn();
    /**
     * <p>
     * setVelocityOut.
     * </p>
     *
     * @param vel a double
     */
    public void setVelocityOut(double vel);

    /**
     * <p>
     * getVelocityIn.
     * </p>
     *
     * @param i a int
     * @return a {@link neqsim.util.util.DoubleCloneable} object
     */
    public DoubleCloneable getVelocityIn(int i);

    // public double calcWallHeatTransferCoeffisient(int phase);
    // public double calcWallMassTransferCoeffisient(double schmidtNumber, int
    // phase);
    /**
     * <p>
     * calcTotalHeatTransferCoefficient.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double calcTotalHeatTransferCoefficient(int phase);

    // public double initVelocity();
    // public double calcInterphaseMassTransferCoeffisient(double schmidtNumber, int
    // phase);
    // public double calcInterphaseHeatTransferCoeffisient(int phase);
    // public double calcdPdz();
    // public double calcdTdz();
    // public double calcdVoiddz();
    // public double[] calcdxdz();
    /**
     * <p>
     * initBulkSystem.
     * </p>
     */
    public void initBulkSystem();

    /**
     * <p>
     * display.
     * </p>
     */
    public void display();

    /**
     * <p>
     * display.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void display(String name);
}

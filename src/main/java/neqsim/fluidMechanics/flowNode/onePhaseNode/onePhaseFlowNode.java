package neqsim.fluidMechanics.flowNode.onePhaseNode;

import neqsim.fluidMechanics.flowNode.FlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract onePhaseFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class onePhaseFlowNode extends FlowNode {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for onePhaseFlowNode.
   * </p>
   */
  public onePhaseFlowNode() {}

  /**
   * <p>
   * Constructor for onePhaseFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public onePhaseFlowNode(SystemInterface system) {}

  /**
   * <p>
   * Constructor for onePhaseFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
   *        object
   */
  public onePhaseFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
  }

  /** {@inheritDoc} */
  @Override
  public onePhaseFlowNode clone() {
    onePhaseFlowNode clonedSystem = null;
    try {
      clonedSystem = (onePhaseFlowNode) super.clone();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void increaseMolarRate(double moles) {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      double diff = (getBulkSystem().getPhases()[0].getComponents()[i].getx()
          * (molarFlowRate[0] - getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()));
      getBulkSystem().addComponent(getBulkSystem().getPhase(0).getComponent(i).getComponentName(),
          diff);
    }
    getBulkSystem().init_x_y();
    initFlowCalc();
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    initBulkSystem();
    molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
    massFlowRate[0] = molarFlowRate[0] * getBulkSystem().getPhases()[0].getMolarMass();
    volumetricFlowRate[0] =
        massFlowRate[0] / getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
    superficialVelocity[0] = volumetricFlowRate[0] / pipe.getArea();
    velocity[0] = superficialVelocity[0];
    this.init();
  }

  /** {@inheritDoc} */
  @Override
  public void updateMolarFlow() {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      double diff = (getBulkSystem().getPhases()[0].getComponents()[i].getx()
          * (molarFlowRate[0] - getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()));
      getBulkSystem().addComponent(getBulkSystem().getPhase(0).getComponent(i).getComponentName(),
          diff);
    }
    getBulkSystem().init_x_y();
    getBulkSystem().init(3);
  }

  // public double initVelocity(){
  // initBulkSystem();
  // molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
  // massFlowRate[0] =
  // molarFlowRate[0]*getBulkSystem().getPhases()[0].getMolarMass();
  // volumetricFlowRate[0] =
  // massFlowRate[0]/getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
  // superficialVelocity[0] = volumetricFlowRate[0]/pipe.getArea();
  // velocity[0] = superficialVelocity[0];
  // return velocity[0];
  // }

  /**
   * <p>
   * calcReynoldsNumber.
   * </p>
   *
   * @return a double
   */
  public double calcReynoldsNumber() {
    reynoldsNumber[0] = getVelocity() * pipe.getDiameter()
        / getBulkSystem().getPhases()[0].getPhysicalProperties().getKinematicViscosity();
    return reynoldsNumber[0];
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    super.init();
    massFlowRate[0] = velocity[0]
        * getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity() * pipe.getArea();
    superficialVelocity[0] = velocity[0];
    molarFlowRate[0] = massFlowRate[0] / getBulkSystem().getPhases()[0].getMolarMass();
    volumetricFlowRate[0] = superficialVelocity[0] * pipe.getArea();
    this.updateMolarFlow();
    calcReynoldsNumber();
    //System.out.println("specifiedFrictionFactor " +specifiedFrictionFactor[0]);
    if (specifiedFrictionFactor[0] == null) {
      wallFrictionFactor[0] = interphaseTransportCoefficient.calcWallFrictionFactor(this);
    } else {
      wallFrictionFactor[0] = specifiedFrictionFactor[0];
    }
  }
}

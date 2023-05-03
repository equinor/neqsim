package neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseOnePhase.interphasePipeFlow.InterphasePipeFlow;
import neqsim.fluidMechanics.flowNode.onePhaseNode.onePhaseFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * onePhasePipeFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class onePhasePipeFlowNode extends onePhaseFlowNode {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for onePhasePipeFlowNode.
   * </p>
   */
  public onePhasePipeFlowNode() {}

  /**
   * <p>
   * Constructor for onePhasePipeFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface}
   *        object
   */
  public onePhasePipeFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.interphaseTransportCoefficient = new InterphasePipeFlow(this);
    phaseOps = new ThermodynamicOperations(this.getBulkSystem());
    phaseOps.TPflash();
    initBulkSystem();
  }

  /** {@inheritDoc} */
  @Override
  public onePhasePipeFlowNode clone() {
    onePhasePipeFlowNode clonedSystem = null;
    try {
      clonedSystem = (onePhasePipeFlowNode) super.clone();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public double calcReynoldsNumber() {
    reynoldsNumber[0] = getVelocity() * pipe.getDiameter()
        / getBulkSystem().getPhases()[0].getPhysicalProperties().getKinematicViscosity();
    return reynoldsNumber[0];
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    System.out.println("Starter.....");
    SystemSrkEos testSystem = new SystemSrkEos(300.3, 200.0);

    testSystem.addComponent("methane", 50000.0);
    testSystem.addComponent("ethane", 1.0);

    testSystem.init(0);
    testSystem.init(1);

    FlowNodeInterface[] test = new onePhasePipeFlowNode[100];

    GeometryDefinitionInterface pipe1 = new PipeData(1, 0.0025);
    test[0] = new onePhasePipeFlowNode(testSystem, pipe1);
    // test[0].setFrictionFactorType(0);

    // test[0].init()
    test[0].initFlowCalc();
    test[0].init();

    // test[0].getVolumetricFlow();
    System.out.println("flow: " + test[0].getVolumetricFlow() + " velocity: "
        + test[0].getVelocity() + " reynolds number " + test[0].getReynoldsNumber() + "friction : "
        + test[0].getWallFrictionFactor());
  }
}

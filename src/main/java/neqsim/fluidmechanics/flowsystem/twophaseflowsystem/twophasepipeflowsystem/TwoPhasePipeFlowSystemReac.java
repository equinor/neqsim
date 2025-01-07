package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TwoPhasePipeFlowSystemReac class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class TwoPhasePipeFlowSystemReac extends TwoPhasePipeFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TwoPhasePipeFlowSystemReac.
   * </p>
   */
  public TwoPhasePipeFlowSystemReac() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Initierer et nyt rorsystem
    neqsim.fluidmechanics.flowsystem.FlowSystemInterface pipe = new TwoPhasePipeFlowSystemReac();

    SystemInterface testSystem = new SystemFurstElectrolyteEos(295.3, 50.01325);
    testSystem.addComponent("methane", 50.11152187, "Nlitre/min", 0);
    testSystem.addComponent("CO2", 50.11152181, "Nlitre/min", 0);
    testSystem.addComponent("water", 0.5662204876, "kg/min", 1);
    testSystem.addComponent("MDEA", 0.5662204876, "kg/min", 1);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testSystem.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
    // testOps.TPflash();
    // testSystem.display();

    // testSystem.setNumericDerivatives(true);
    testSystem.initPhysicalProperties();

    pipe.setInletThermoSystem(testSystem); // setter termodyanmikken for rorsystemet
    pipe.setNumberOfLegs(3); // deler inn roret i et gitt antall legger
    pipe.setNumberOfNodesInLeg(10); // setter antall nodepunkter (beregningspunkter/grid) pr.
                                    // leg

    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 0.03, 0.07, 0.13, 2.5, 3.7};
    double[] outerTemperature =
        {278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 275.0, 275.0, 275.0, 275.0};

    pipe.setLegHeights(height); // setter inn hoyde for hver leg-ende
    pipe.setLegPositions(length); // setter avstand til hver leg-ende
    pipe.setLegOuterTemperatures(outerTemperature);

    neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface[] pipeGemometry =
        new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData[5]; // Deffinerer
                                                                        // geometrien
                                                                        // for
                                                                        // roret
    double[] pipeDiameter = {0.025, 0.025, 0.025, 0.025, 0.025};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGemometry[i] =
          new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData(pipeDiameter[i]);
    }
    pipe.setEquipmentGeometry(pipeGemometry); // setter inn rorgeometrien for hver leg
    // utforer beregninger
    pipe.createSystem();
    pipe.setEquilibriumMassTransfer(false);
    pipe.setEquilibriumHeatTransfer(false);
    pipe.init();

    pipe.solveSteadyState(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(pipe.getNode(2).getBulkSystem());
    testOps.TPflash();

    // pipe.calcFluxes();
    // pipe.print();
    // pipe.displayResult();
    // testOps.TPflash();
    // testOps.displayResult();
  }
}

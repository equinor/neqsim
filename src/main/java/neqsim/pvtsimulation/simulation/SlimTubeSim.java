package neqsim.pvtsimulation.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SlimTubeSim class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SlimTubeSim extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SlimTubeSim.class);
  SystemInterface inectionGasSystem = null;
  private int numberOfSlimTubeNodes = 200;
  SystemInterface[] slimTubeNodeSystem = null;

  /**
   * <p>
   * Constructor for SlimTubeSim.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param injectionGas a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SlimTubeSim(SystemInterface tempSystem, SystemInterface injectionGas) {
    super(tempSystem);
    inectionGasSystem = injectionGas;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    slimTubeNodeSystem = new SystemInterface[numberOfSlimTubeNodes + 1];

    getThermoSystem().setPressure(ThermodynamicConstantsInterface.referencePressure);
    getThermoSystem().setTemperature(288.15);
    thermoOps.TPflash();

    /*
     * double totalReferenceNodeVolumeAtStadardConditions; if (getThermoSystem().getNumberOfPhases()
     * > 1) { totalReferenceNodeVolumeAtStadardConditions =
     * getThermoSystem().getPhase(1).getVolume() * numberOfSlimTubeNodes; } else {
     * totalReferenceNodeVolumeAtStadardConditions = getThermoSystem().getPhase(0).getVolume() *
     * numberOfSlimTubeNodes; }
     */

    getThermoSystem().setPressure(getPressure());
    getThermoSystem().setTemperature(getTemperature());
    thermoOps.TPflash();
    if (getThermoSystem().getNumberOfPhases() > 1) {
      logger.debug(
          "more than one phase at initial pressure and temperature.....stopping slimtube simulation.");
      return;
    }

    double standardNodeVolume = getThermoSystem().getPhase(0).getVolume();

    inectionGasSystem.setPressure(getPressure());
    inectionGasSystem.setTemperature(getTemperature());
    inectionGasSystem.init(0);
    inectionGasSystem.init(1);

    for (int i = 0; i < numberOfSlimTubeNodes + 1; i++) {
      slimTubeNodeSystem[i] = getThermoSystem().clone();
    }

    slimTubeNodeSystem[numberOfSlimTubeNodes].reset();
    slimTubeNodeSystem[numberOfSlimTubeNodes].init(1);

    ThermodynamicOperations slimOps0 = new ThermodynamicOperations(slimTubeNodeSystem[0]);
    ThermodynamicOperations slimOps1 = new ThermodynamicOperations(slimTubeNodeSystem[0]);

    for (int timeStep = 0; timeStep < 200; timeStep++) {
      slimTubeNodeSystem[0].addFluid(inectionGasSystem);
      slimOps0.setSystem(slimTubeNodeSystem[0]);
      slimOps0.TPflash();

      for (int i = 0; i < numberOfSlimTubeNodes; i++) {
        double totalVolume = slimTubeNodeSystem[i].getVolume();
        double gasVolume = 0;
        int liquidPhaseNumber = 0;
        double excessVolume = totalVolume - standardNodeVolume;

        if (slimTubeNodeSystem[i].getNumberOfPhases() > 1) {
          gasVolume = slimTubeNodeSystem[i].getPhase(0).getVolume();
          liquidPhaseNumber = 1;
        }

        double liquidExcessVolume = totalVolume - standardNodeVolume - gasVolume;
        if (liquidExcessVolume < 0) {
          liquidExcessVolume = 0.0;
        }

        int numComp = slimTubeNodeSystem[0].getPhase(0).getNumberOfComponents();
        double[] removeMoles = new double[numComp];

        if (slimTubeNodeSystem[i].getNumberOfPhases() > 1) {
          double gasExcessVolume = totalVolume - standardNodeVolume - liquidExcessVolume;
          double gasfactor = gasExcessVolume / excessVolume;
          if (gasExcessVolume < excessVolume) {
            gasfactor = 1.0;
          }

          for (int k = 0; k < numComp; k++) {
            double moles =
                slimTubeNodeSystem[i].getPhase(0).getComponent(k).getNumberOfMolesInPhase();
            removeMoles[k] += gasfactor * moles;
          }
        }

        if (liquidExcessVolume > 0) {
          double liquidVolume = slimTubeNodeSystem[i].getPhase(liquidPhaseNumber).getVolume();
          double liqfactor = liquidExcessVolume / liquidVolume;
          for (int k = 0; k < numComp; k++) {
            double moles = slimTubeNodeSystem[i].getPhase(liquidPhaseNumber).getComponent(k)
                .getNumberOfMolesInPhase();
            removeMoles[k] += moles * liqfactor;
          }
        }

        /*
         * double sum = 0; for (int comp = 0; comp <
         * slimTubeNodeSystem[0].getPhase(liquidPhaseNumber) .getNumberOfComponents(); comp++) { sum
         * += removeMoles[comp]; }
         */

        for (int k = 0; k < numComp; k++) {
          try {
            if (removeMoles[k] <= slimTubeNodeSystem[i].getComponent(k).getNumberOfmoles()) {
              slimTubeNodeSystem[i].addComponent(k, -removeMoles[k]);
              slimTubeNodeSystem[i + 1].addComponent(k, removeMoles[k]);
            } else {
              slimTubeNodeSystem[i + 1].addComponent(k,
                  slimTubeNodeSystem[i].getComponent(k).getNumberOfmoles());
              slimTubeNodeSystem[i].addComponent(k,
                  -slimTubeNodeSystem[i].getComponent(k).getNumberOfmoles());
            }
          } catch (Exception e) {
            logger.warn(e.getMessage());
          }
        }
        slimOps0.setSystem(slimTubeNodeSystem[i]);
        slimOps0.TPflash();
        // System.out.println("node " + i + " delta volume end "
        // + (slimTubeNodeSystem[i].getVolume() - standardNodeVolume) + " add moles "
        // + sum);
        slimOps1.setSystem(slimTubeNodeSystem[i + 1]);
        slimOps1.TPflash();

        // slimTubeNodeSystem[i].display();
        // slimTubeNodeSystem[i + 1].display();
      }
      /*
       * logger.DEBUG("time " + timeStep + " node " + numberOfSlimTubeNodes + " volume " +
       * (slimTubeNodeSystem[numberOfSlimTubeNodes].getVolume()) + " moles " +
       * slimTubeNodeSystem[numberOfSlimTubeNodes].getNumberOfMoles());
       */
      slimTubeNodeSystem[numberOfSlimTubeNodes].setTemperature(288.15);
      slimTubeNodeSystem[numberOfSlimTubeNodes]
          .setPressure(ThermodynamicConstantsInterface.referencePressure);
      slimOps1.TPflash();

      /*
       * double totalAccumulatedVolumeAtStadardConditions =
       * slimTubeNodeSystem[numberOfSlimTubeNodes].getPhase(0).getVolume();
       *
       * if (slimTubeNodeSystem[numberOfSlimTubeNodes].getNumberOfPhases() > 1) {
       * totalAccumulatedVolumeAtStadardConditions =
       * slimTubeNodeSystem[numberOfSlimTubeNodes].getPhase(1).getVolume(); }
       */

      /*
       * System.out.println("accumulated VOlume " + totalAccumulatedVolumeAtStadardConditions +
       * " total reference volume " + totalReferenceNodeVolumeAtStadardConditions);
       * System.out.println("oil recovery ratio" + totalAccumulatedVolumeAtStadardConditions /
       * totalReferenceNodeVolumeAtStadardConditions);
       */
    }

    slimTubeNodeSystem[numberOfSlimTubeNodes].setTemperature(288.15);
    slimTubeNodeSystem[numberOfSlimTubeNodes]
        .setPressure(ThermodynamicConstantsInterface.referencePressure);
    slimOps1.TPflash();

    /*
     * double totalAccumulatedVolumeAtStadardConditions =
     * slimTubeNodeSystem[numberOfSlimTubeNodes].getPhase(0).getVolume();
     *
     * if (slimTubeNodeSystem[numberOfSlimTubeNodes].getNumberOfPhases() > 1) {
     * totalAccumulatedVolumeAtStadardConditions =
     * slimTubeNodeSystem[numberOfSlimTubeNodes].getPhase(1).getVolume(); }
     *
     * System.out.println("accumulated VOlume " + totalAccumulatedVolumeAtStadardConditions +
     * " total reference volume " + totalReferenceNodeVolumeAtStadardConditions);
     * System.out.println("oil recovery ratio" + totalAccumulatedVolumeAtStadardConditions /
     * totalReferenceNodeVolumeAtStadardConditions);
     */
    /*
     * for (int i = 0; i < numberOfSlimTubeNodes; i++) { slimTubeNodeSystem[i].display(); }
     */
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface gasSystem = new SystemSrkEos(298.0, 200.0);
    gasSystem.addComponent("CO2", 10.0);
    // gasSystem.addComponent("ethane", 2.0);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);

    SystemInterface oilSystem = new SystemSrkEos(298.0, 200.0);
    oilSystem.addComponent("CO2", 0.1);
    oilSystem.addComponent("methane", 1.5);
    oilSystem.addComponent("ethane", 1.5);
    oilSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    oilSystem.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    oilSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    oilSystem.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.79);
    oilSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
    oilSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806);
    oilSystem.addTBPfraction("C13", 0.32, 177.0 / 1000.0, 0.824);
    oilSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835);
    oilSystem.addTBPfraction("C15", 0.23, 202.0 / 1000.0, 0.84);
    oilSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
    oilSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84);
    oilSystem.addTBPfraction("C18", 0.13, 251.0 / 1000.0, 0.844);
    oilSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854);
    oilSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
    oilSystem.getCharacterization().characterisePlusFraction();
    oilSystem.createDatabase(true);
    oilSystem.setMixingRule(2);

    SlimTubeSim sepSim = new SlimTubeSim(oilSystem, gasSystem);
    sepSim.setTemperature(273.15 + 100);
    sepSim.setPressure(380.0);
    sepSim.setNumberOfSlimTubeNodes(40);
    sepSim.run();
  }

  /**
   * <p>
   * Getter for the field <code>numberOfSlimTubeNodes</code>.
   * </p>
   *
   * @return the numberOfSlimTubeNodes
   */
  public int getNumberOfSlimTubeNodes() {
    return numberOfSlimTubeNodes;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfSlimTubeNodes</code>.
   * </p>
   *
   * @param numberOfSlimTubeNodes the numberOfSlimTubeNodes to set
   */
  public void setNumberOfSlimTubeNodes(int numberOfSlimTubeNodes) {
    this.numberOfSlimTubeNodes = numberOfSlimTubeNodes;
  }
}

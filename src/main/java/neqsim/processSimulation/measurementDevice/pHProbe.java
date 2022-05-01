package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pHProbe class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class pHProbe extends MeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;

  protected int streamNumber = 0;
  /** Constant <code>numberOfStreams=0</code> */
  protected static int numberOfStreams = 0;
  protected String name = new String();
  protected StreamInterface stream = null;
  protected SystemInterface reactiveThermoSystem;
  protected ThermodynamicOperations thermoOps;
  private double alkanility = 0.0;

  /**
   * <p>
   * Constructor for pHProbe.
   * </p>
   */
  public pHProbe() {}

  /**
   * <p>
   * Constructor for pHProbe.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public pHProbe(StreamInterface stream) {
    this.stream = stream;
    numberOfStreams++;
    streamNumber = numberOfStreams;
  }

  /**
   * <p>
   * run.
   * </p>
   */
  public void run() {

    if (stream != null) {
      if (stream.getFluid().hasPhaseType("aqueous")) {
        reactiveThermoSystem = stream.getFluid().clone();
        // reactiveThermoSystem = stream.getFluid().phaseToSystem("aqueous");
        reactiveThermoSystem = reactiveThermoSystem.setModel("Electrolyte-CPA-EOS-statoil");
        if (getAlkanility() > 1e-10) {
          double waterkg = reactiveThermoSystem.getComponent("water").getTotalFlowRate("kg/sec");
          reactiveThermoSystem.addComponent("Na+", waterkg * getAlkanility() / 1e3);
          reactiveThermoSystem.addComponent("OH-", waterkg * getAlkanility() / 1e3);
        }
        if (!reactiveThermoSystem.isChemicalSystem()) {
          reactiveThermoSystem.chemicalReactionInit();
          reactiveThermoSystem.setMixingRule(10);
          reactiveThermoSystem.setMultiPhaseCheck(false);
        }
        thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
      } else
        return;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    System.out.println("measured temperature " + stream.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    if (stream != null) {
      if (stream.getFluid().hasPhaseType("aqueous")) {
        reactiveThermoSystem = stream.getFluid().clone();
        // reactiveThermoSystem = stream.getFluid().phaseToSystem("aqueous");
        reactiveThermoSystem = reactiveThermoSystem.setModel("Electrolyte-CPA-EOS-statoil");
        if (getAlkanility() > 1e-10) {
          double waterkg = reactiveThermoSystem.getComponent("water").getTotalFlowRate("kg/sec");
          reactiveThermoSystem.addComponent("Na+", waterkg * getAlkanility() / 1e3);
          reactiveThermoSystem.addComponent("OH-", waterkg * getAlkanility() / 1e3);
        }
        if (!reactiveThermoSystem.isChemicalSystem()) {
          reactiveThermoSystem.chemicalReactionInit();
          reactiveThermoSystem.setMixingRule(10);
          reactiveThermoSystem.setMultiPhaseCheck(false);
        }
        thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
        return reactiveThermoSystem.getPhase(reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"))
            .getpH();
      } else {
        System.out.println("no aqueous phase for pH analyser");
        return 7.0;
      }
    } else
      System.out.println("no stream connected to pH analyser");
    return Double.NaN;
  }

  /**
   * @return the alkanility
   */
  public double getAlkanility() {
    return alkanility;
  }

  /**
   * @param alkanility the alkanility to set
   */
  public void setAlkanility(double alkanility) {
    this.alkanility = alkanility;
  }
}

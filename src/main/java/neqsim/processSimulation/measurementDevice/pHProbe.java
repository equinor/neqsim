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
public class pHProbe extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;

  protected SystemInterface reactiveThermoSystem;
  protected ThermodynamicOperations thermoOps;

  private double alkalinity = 0.0;

  /**
   * <p>
   * Constructor for pHProbe.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public pHProbe(StreamInterface stream) {
    this("phProbe", stream);
  }

  /**
   * <p>
   * Constructor for pHProbe.
   * </p>
   *
   * @param name Name of pHProbe
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public pHProbe(String name, StreamInterface stream) {
    super(name, "", stream);
  }

  /**
   * <p>
   * run.
   * </p>
   */
  public void run() {
    if (stream != null && stream.getFluid().hasPhaseType("aqueous")) {
      reactiveThermoSystem = stream.getFluid().clone();
      // reactiveThermoSystem = stream.getFluid().phaseToSystem("aqueous");
      reactiveThermoSystem = reactiveThermoSystem.setModel("Electrolyte-CPA-EOS-statoil");
      if (getAlkalinity() > 1e-10) {
        double waterkg = reactiveThermoSystem.getComponent("water").getTotalFlowRate("kg/sec");
        reactiveThermoSystem.addComponent("Na+", waterkg * getAlkalinity() / 1e3);
        reactiveThermoSystem.addComponent("OH-", waterkg * getAlkalinity() / 1e3);
      }
      if (!reactiveThermoSystem.isChemicalSystem()) {
        reactiveThermoSystem.chemicalReactionInit();
        reactiveThermoSystem.setMixingRule(10);
        reactiveThermoSystem.setMultiPhaseCheck(false);
      }
      thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
      thermoOps.TPflash();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "can only be empty."));
    }
    if (stream != null) {
      if (stream.getFluid().hasPhaseType("aqueous")) {
        reactiveThermoSystem = stream.getFluid().clone();
        // reactiveThermoSystem = stream.getFluid().phaseToSystem("aqueous");
        reactiveThermoSystem = reactiveThermoSystem.setModel("Electrolyte-CPA-EOS-statoil");
        if (getAlkalinity() > 1e-10) {
          double waterkg = reactiveThermoSystem.getComponent("water").getTotalFlowRate("kg/sec");
          reactiveThermoSystem.addComponent("Na+", waterkg * getAlkalinity() / 1e3);
          reactiveThermoSystem.addComponent("OH-", waterkg * getAlkalinity() / 1e3);
        }
        if (!reactiveThermoSystem.isChemicalSystem()) {
          reactiveThermoSystem.chemicalReactionInit();
          reactiveThermoSystem.setMixingRule(10);
          reactiveThermoSystem.setMultiPhaseCheck(false);
        }
        thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
        return reactiveThermoSystem.getPhase("aqueous").getpH();
      } else {
        System.out.println("no aqueous phase for pH analyser");
        return 7.0;
      }
    } else {
      System.out.println("no stream connected to pH analyser");
    }
    return Double.NaN;
  }

  /**
   * <p>
   * Getter for the field <code>alkalinity</code>.
   * </p>
   *
   * @return the alkalinity
   */
  public double getAlkalinity() {
    return alkalinity;
  }

  /**
   * <p>
   * Getter for the field <code>alkalinity</code>.
   * </p>
   *
   * @return the alkalinity
   * @deprecated Replaced by {@link getAlkalinity}
   */
  @Deprecated
  public double getAlkanility() {
    return getAlkalinity();
  }

  /**
   * <p>
   * Setter for the field <code>alkalinity</code>.
   * </p>
   *
   * @param alkalinity the alkalinity to set
   */
  public void setAlkalinity(double alkalinity) {
    this.alkalinity = alkalinity;
  }

  /**
   * <p>
   * Setter for the field <code>alkalinity</code>.
   * </p>
   *
   * @param alkalinity the alkalinity to set
   * @deprecated Replaced by {@link setAlkalinity}
   */
  @Deprecated
  public void setAlkanility(double alkalinity) {
    setAlkalinity(alkalinity);
  }
}

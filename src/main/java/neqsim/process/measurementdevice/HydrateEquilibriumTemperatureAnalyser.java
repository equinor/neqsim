package neqsim.process.measurementdevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HydrateEquilibriumTemperatureAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrateEquilibriumTemperatureAnalyser extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateEquilibriumTemperatureAnalyser.class);

  private double referencePressure = 0;

  /**
   * <p>
   * Constructor for HydrateEquilibriumTemperatureAnalyser.
   * </p>
   *
   * @param name Name of HydrateEquilibriumTemperatureAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public HydrateEquilibriumTemperatureAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    /*
     * try { // System.out.println("total water production [kg/dag]" + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles() * //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24); //
     * System.out.println("water in phase 1 (ppm) " + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6); } finally { }
     */
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    if (!tempFluid.getHydrateCheck()) {
      tempFluid.setHydrateCheck(true);
    }
    tempFluid.setTemperature(10.0, "C");
    if (referencePressure > 1e-10) {
      tempFluid.setPressure(referencePressure);
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.hydrateFormationTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return tempFluid.getTemperature(unit);
  }

  /**
   * <p>
   * Getter for the field <code>referencePressure</code>.
   * </p>
   *
   * @return Reference pressure in bara
   */
  public double getReferencePressure() {
    return referencePressure;
  }

  /**
   * <p>
   * Setter for the field <code>referencePressure</code>.
   * </p>
   *
   * @param referencePressure Reference pressure to set in in bara
   */
  public void setReferencePressure(double referencePressure) {
    this.referencePressure = referencePressure;
  }
}

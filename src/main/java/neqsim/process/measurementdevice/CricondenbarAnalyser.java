package neqsim.process.measurementdevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * CricondenbarAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CricondenbarAnalyser extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CricondenbarAnalyser.class);

  /**
   * <p>
   * Constructor for CricondenbarAnalyser.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CricondenbarAnalyser(StreamInterface stream) {
    this("CricondenbarAnalyser", stream);
  }

  /**
   * <p>
   * Constructor for CricondenbarAnalyser.
   * </p>
   *
   * @param name Name of CricondenbarAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CricondenbarAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    /*
     * try { // System.out.println("total water production [kg/dag]" + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()*stream.
     * getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24); //
     * System.out.println("water in phase 1 (ppm) " + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6); } finally { }
     */
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    tempFluid.removeComponent("water");
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.setRunAsThread(true);
      thermoOps.calcPTphaseEnvelope(false, 1.);
      thermoOps.waitAndCheckForFinishedCalculation(15000);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return thermoOps.get("cricondenbar")[1];
  }

  /**
   * <p>
   * getMeasuredValue2.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @param temp a double
   * @return a double
   */
  public double getMeasuredValue2(String unit, double temp) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    tempFluid.setTemperature(temp, "C");
    tempFluid.setPressure(10.0, "bara");
    if (tempFluid.getPhase(0).hasComponent("water")) {
      tempFluid.removeComponent("water");
    }
    neqsim.pvtsimulation.simulation.SaturationPressure thermoOps =
        new neqsim.pvtsimulation.simulation.SaturationPressure(tempFluid);
    try {
      thermoOps.run();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return thermoOps.getSaturationPressure();
  }
}

package neqsim.processSimulation.measurementDevice;



import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.empiric.BukacekWaterInGas;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * WaterDewPointAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterDewPointAnalyser extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;
  

  private double referencePressure = 70.0;
  private String method = "Bukacek";

  /**
   * <p>
   * Constructor for WaterDewPointAnalyser.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public WaterDewPointAnalyser(StreamInterface stream) {
    this("WaterDewPointAnalyser", stream);
  }

  /**
   * <p>
   * Constructor for WaterDewPointAnalyser.
   * </p>
   *
   * @param name Name of WaterDewPointAnalyser
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public WaterDewPointAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (method.equals("Bukacek")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setTemperature(BukacekWaterInGas
          .waterDewPointTemperature(tempFluid.getComponent("water").getx(), referencePressure));
      return tempFluid.getTemperature(unit);
    } else if (method.equals("multiphase")) {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      tempFluid.setPressure(referencePressure);
      tempFluid.setTemperature(0.1, "C");
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.waterDewPointTemperatureMultiphaseFlash();
      } catch (Exception ex) {
        
      }
      return tempFluid.getTemperature(unit);
    } else {
      SystemInterface tempFluid = stream.getThermoSystem().clone();
      SystemInterface tempFluid2 = tempFluid.setModel("GERG-water-EOS");
      tempFluid2.setPressure(referencePressure);
      tempFluid2.setTemperature(-17.0, "C");
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid2);
      try {
        thermoOps.waterDewPointTemperatureFlash();
      } catch (Exception ex) {
        
      }
      return tempFluid2.getTemperature(unit);
    }
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

  /**
   * <p>
   * Getter for the field <code>method</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getMethod() {
    return method;
  }

  /**
   * <p>
   * Setter for the field <code>method</code>.
   * </p>
   *
   * @param method a {@link java.lang.String} object
   */
  public void setMethod(String method) {
    this.method = method;
  }
}

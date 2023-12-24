package neqsim.processSimulation.processEquipment.reservoir;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * WellFlow class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WellFlow extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(ThrottlingValve.class);
  SystemInterface thermoSystem;
  private double wellProductionIndex = 0;
  double pressureOut = 1.0;
  String pressureUnit = "bara";
  boolean useWellProductionIndex = false;
  boolean calcpressure = true;


  /**
   * <p>
   * Constructor for WellFlow.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public WellFlow(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    super.setInletStream(stream);
    StreamInterface outStream = stream.clone();
    outStream.setName("outStream");
    super.setOutletStream(outStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = getInletStream().getThermoSystem().clone();
    thermoSystem.setPressure(pressureOut, pressureUnit);
    outStream.setThermoSystem(thermoSystem);
    if (useWellProductionIndex) {
      if (calcpressure) {
        double presout = Math.sqrt(Math.pow(getInletStream().getPressure("bara"), 2.0)
            - getInletStream().getFlowRate("MSm3/day") / wellProductionIndex);
        outStream.setPressure(presout, "bara");
      } else {
        double flow = wellProductionIndex * (Math.pow(getInletStream().getPressure("bara"), 2.0)
            - Math.pow(thermoSystem.getPressure("bara"), 2.0));
        outStream.setFlowRate(flow, "MSm3/day");
      }
    } else {
      wellProductionIndex = getInletStream().getFlowRate("MSm3/day")
          / (Math.pow(getInletStream().getPressure("bara"), 2.0)
              - Math.pow(thermoSystem.getPressure("bara"), 2.0));
    }
    outStream.run();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }
    double flow = wellProductionIndex * (Math.pow(getInletStream().getPressure("bara"), 2.0)
        - Math.pow(thermoSystem.getPressure("bara"), 2.0));

    outStream.setFlowRate(flow, "MSm3/day");
    outStream.run();
  }

  public double getWellProductionIndex() {
    return wellProductionIndex;
  }

  public void setWellProductionIndex(double wellProductionIndex) {
    useWellProductionIndex = true;
    this.wellProductionIndex = wellProductionIndex;
  }

}

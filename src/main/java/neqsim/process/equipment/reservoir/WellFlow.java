package neqsim.process.equipment.reservoir;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.valve.ThrottlingValve;
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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ThrottlingValve.class);
  SystemInterface thermoSystem;
  private double wellProductionIndex = 0;
  double pressureOut = 1.0;
  String pressureUnit = "bara";
  boolean useWellProductionIndex = false;
  boolean calcpressure = true;

  /** Inflow performance models supported by the well. */
  public enum InflowPerformanceModel {
    /** Constant production index. */
    PRODUCTION_INDEX,
    /** Vogel correlation for solution gas drive wells. */
    VOGEL,
    /** Fetkovich correlation for gas wells. */
    FETKOVICH
  }

  InflowPerformanceModel inflowModel = InflowPerformanceModel.PRODUCTION_INDEX;
  // Vogel parameters
  double vogelQmax = 0.0;
  double vogelRefPres = 0.0;
  // Fetkovich parameters
  double fetkovichC = 0.0;
  double fetkovichN = 1.0;

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
  public void run(UUID id) {
    thermoSystem = getInletStream().getThermoSystem().clone();
    thermoSystem.setPressure(pressureOut, pressureUnit);
    outStream.setThermoSystem(thermoSystem);

    double presRes = getInletStream().getPressure("bara");

    switch (inflowModel) {
      case VOGEL:
        if (calcpressure) {
          double q = getInletStream().getFlowRate("MSm3/day");
          double term = q / vogelQmax;
          double a = 0.8;
          double b = 0.2;
          double c = term - 1.0;
          double disc = b * b - 4.0 * a * c;
          if (disc < 0) {
            logger.error("pressure lower that 0");
            throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
                "run:calcOutletPressure", "pressure", "- Outlet pressure is negative" + pressureOut));
          }
          double x = (-b + Math.sqrt(disc)) / (2.0 * a);
          outStream.setPressure(presRes * x, "bara");
        } else {
          double pwf = thermoSystem.getPressure("bara");
          double term = 1.0 - 0.2 * (pwf / presRes) - 0.8 * Math.pow(pwf / presRes, 2.0);
          double flow = vogelQmax * term;
          outStream.setFlowRate(flow, "MSm3/day");
        }
        break;
      case FETKOVICH:
        if (calcpressure) {
          double q = getInletStream().getFlowRate("MSm3/day");
          double delta = Math.pow(q / fetkovichC, 1.0 / fetkovichN);
          if (Math.pow(presRes, 2.0) - delta < 0) {
            logger.error("pressure lower that 0");
            throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
                "run:calcOutletPressure", "pressure", "- Outlet pressure is negative" + pressureOut));
          }
          outStream.setPressure(Math.sqrt(Math.pow(presRes, 2.0) - delta), "bara");
        } else {
          double pwf = thermoSystem.getPressure("bara");
          double flow = fetkovichC
              * Math.pow(Math.pow(presRes, 2.0) - Math.pow(pwf, 2.0), fetkovichN);
          outStream.setFlowRate(flow, "MSm3/day");
        }
        break;
      case PRODUCTION_INDEX:
      default:
        if (useWellProductionIndex) {
          if (calcpressure) {
            double presout;
            if (Math.pow(presRes, 2.0) - getInletStream().getFlowRate("MSm3/day") / wellProductionIndex > 0) {
              presout = Math
                  .sqrt(Math.pow(presRes, 2.0) - getInletStream().getFlowRate("MSm3/day") / wellProductionIndex);
            } else {
              logger.error("pressure lower that 0");
              throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
                  "run:calcOutletPressure", "pressure", "- Outlet pressure is negative" + pressureOut));
            }
            outStream.setPressure(presout, "bara");
          } else {
            double flow = wellProductionIndex * (Math.pow(presRes, 2.0)
                - Math.pow(thermoSystem.getPressure("bara"), 2.0));
            outStream.setFlowRate(flow, "MSm3/day");
          }
        } else {
          wellProductionIndex = getInletStream().getFlowRate("MSm3/day")
              / (Math.pow(presRes, 2.0) - Math.pow(thermoSystem.getPressure("bara"), 2.0));
        }
        break;
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

  /**
   * <p>
   * Getter for the field <code>wellProductionIndex</code>.
   * </p>
   *
   * @return a double
   */
  public double getWellProductionIndex() {
    return wellProductionIndex;
  }

  /**
   * <p>
   * Setter for the field <code>wellProductionIndex</code>.
   * </p>
   *
   * @param wellProductionIndex a double
   */
  public void setWellProductionIndex(double wellProductionIndex) {
    useWellProductionIndex = true;
    this.wellProductionIndex = wellProductionIndex;
  }

  /**
   * Use Vogel inflow performance relationship.
   *
   * @param qTest flow rate at test conditions (same unit as stream)
   * @param pwfTest bottom-hole pressure at test conditions in bara
   * @param reservoirPressure reservoir pressure in bara
   */
  public void setVogelParameters(double qTest, double pwfTest, double reservoirPressure) {
    this.inflowModel = InflowPerformanceModel.VOGEL;
    this.useWellProductionIndex = false;
    this.vogelRefPres = reservoirPressure;
    this.vogelQmax = qTest
        / (1.0 - 0.2 * (pwfTest / reservoirPressure) - 0.8 * Math.pow(pwfTest / reservoirPressure, 2.0));
  }

  /**
   * Use Fetkovich inflow performance relationship.
   *
   * @param c Fetkovich constant C
   * @param n Fetkovich exponent n
   * @param reservoirPressure reservoir pressure in bara
   */
  public void setFetkovichParameters(double c, double n, double reservoirPressure) {
    this.inflowModel = InflowPerformanceModel.FETKOVICH;
    this.useWellProductionIndex = false;
    this.fetkovichC = c;
    this.fetkovichN = n;
    this.vogelRefPres = reservoirPressure;
  }

  /**
   * Estimate well production index from Darcy law parameters. Units: permeability in mD,
   * viscosity in cP and lengths in meter.
   *
   * @param permeability reservoir permeability
   * @param thickness reservoir thickness
   * @param viscosity fluid viscosity
   * @param reservoirRadius drainage radius
   * @param wellRadius wellbore radius
   * @param skinFactor skin factor
   */
  public void setDarcyLawParameters(double permeability, double thickness, double viscosity,
      double reservoirRadius, double wellRadius, double skinFactor) {
    double numerator = 0.00708 * permeability * thickness;
    double denominator = viscosity * (Math.log(reservoirRadius / wellRadius) + skinFactor);
    setWellProductionIndex(numerator / denominator);
  }
}

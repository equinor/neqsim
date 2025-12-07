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
    FETKOVICH,
    /** Backpressure equation with optional non-Darcy term. */
    BACKPRESSURE,
    /** Table-driven inflow curve (flow vs. bottom-hole pressure). */
    TABLE
  }

  InflowPerformanceModel inflowModel = InflowPerformanceModel.PRODUCTION_INDEX;
  // Vogel parameters
  double vogelQmax = 0.0;
  double vogelRefPres = 0.0;
  // Fetkovich parameters
  double fetkovichC = 0.0;
  double fetkovichN = 1.0;
  // Backpressure parameters
  double backpressureA = 0.0;
  double backpressureB = 0.0;
  // Table-driven inflow parameters
  double[] inflowTablePwf = new double[0];
  double[] inflowTableRate = new double[0];

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
      case BACKPRESSURE:
        if (calcpressure) {
          double q = getInletStream().getFlowRate("MSm3/day");
          double delta = backpressureA * q + backpressureB * Math.pow(q, 2.0);
          if (Math.pow(presRes, 2.0) - delta < 0) {
            logger.error("pressure lower that 0");
            throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
                "run:calcOutletPressure", "pressure", "- Outlet pressure is negative" + pressureOut));
          }
          outStream.setPressure(Math.sqrt(Math.pow(presRes, 2.0) - delta), "bara");
        } else {
          double pwf = thermoSystem.getPressure("bara");
          double delta = Math.pow(presRes, 2.0) - Math.pow(pwf, 2.0);
          double flow = computeBackpressureFlow(delta);
          outStream.setFlowRate(flow, "MSm3/day");
        }
        break;
      case TABLE:
        if (inflowTablePwf.length < 2 || inflowTableRate.length < 2) {
          throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
              "run", "table", "- Table-driven inflow requires at least two points"));
        }
        if (calcpressure) {
          double q = getInletStream().getFlowRate("MSm3/day");
          outStream.setPressure(interpolatePressureForFlow(q), "bara");
        } else {
          double pwf = thermoSystem.getPressure("bara");
          outStream.setFlowRate(interpolateFlowForPressure(pwf), "MSm3/day");
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
   * Specify the well outlet pressure to be used when solving for flow from backpressure
   * (i.e. {@link #solveFlowFromOutletPressure(boolean)} set to true).
   *
   * @param pressure outlet pressure
   * @param unit pressure unit
   */
  public void setOutletPressure(double pressure, String unit) {
    this.pressureOut = pressure;
    this.pressureUnit = unit;
  }

  /**
   * Enable solving for flow rate from a specified outlet pressure instead of solving for outlet
   * pressure from a specified flow rate.
   *
   * @param solve true to compute flow from the set outlet pressure
   */
  public void solveFlowFromOutletPressure(boolean solve) {
    this.calcpressure = !solve;
  }

  /**
   * @return true if the well is set to compute outlet pressure from the inlet stream flowrate.
   */
  public boolean isCalculatingOutletPressure() {
    return calcpressure;
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
   * Use backpressure equation for gas wells: p<sub>res</sub><sup>2</sup> - p<sub>wf</sub><sup>2</sup> =
   * a·q + b·q². Parameter {@code b} captures non-Darcy (turbulence) effects.
   *
   * @param a deliverability coefficient a
   * @param b deliverability coefficient b (non-Darcy component)
   * @param reservoirPressure reservoir pressure in bara (stored for reference)
   */
  public void setBackpressureParameters(double a, double b, double reservoirPressure) {
    this.inflowModel = InflowPerformanceModel.BACKPRESSURE;
    this.useWellProductionIndex = false;
    this.backpressureA = a;
    this.backpressureB = b;
    this.vogelRefPres = reservoirPressure;
  }

  /**
   * Provide tabulated inflow data (flow rate vs. bottom-hole pressure). Arrays are sorted by
   * pressure internally to allow monotonic interpolation.
   *
   * @param bottomHolePressures bottom-hole flowing pressures in bara
   * @param flowRates flow rates corresponding to each pressure point (same unit as stream)
   */
  public void setTableInflow(double[] bottomHolePressures, double[] flowRates) {
    if (bottomHolePressures == null || flowRates == null || bottomHolePressures.length != flowRates.length
        || bottomHolePressures.length < 2) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
          "setTableInflow", "table",
          "- Provide matching pressure/flow arrays with at least two entries"));
    }

    this.inflowModel = InflowPerformanceModel.TABLE;
    this.useWellProductionIndex = false;

    // copy and sort by pressure to support interpolation in either direction
    inflowTablePwf = java.util.Arrays.copyOf(bottomHolePressures, bottomHolePressures.length);
    inflowTableRate = java.util.Arrays.copyOf(flowRates, flowRates.length);
    sortTableByPressure();
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

  private double computeBackpressureFlow(double drawdown) {
    if (backpressureA == 0.0 && backpressureB == 0.0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
          "run:calcFlow", "flow", "- Backpressure parameters a and b must be specified"));
    }
    if (backpressureB == 0.0) {
      return drawdown / backpressureA;
    }
    double discriminant = Math.pow(backpressureA, 2.0) + 4.0 * backpressureB * drawdown;
    if (discriminant < 0) {
      logger.error("pressure lower that 0");
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
          "run:calcFlow", "flow", "- Drawdown is insufficient for backpressure calculation"));
    }
    return (-backpressureA + Math.sqrt(discriminant)) / (2.0 * backpressureB);
  }

  private double interpolateFlowForPressure(double pwf) {
    if (pwf <= inflowTablePwf[0]) {
      return inflowTableRate[0];
    }
    int lastIndex = inflowTablePwf.length - 1;
    if (pwf >= inflowTablePwf[lastIndex]) {
      return inflowTableRate[lastIndex];
    }
    for (int i = 0; i < lastIndex; i++) {
      double lowP = inflowTablePwf[i];
      double highP = inflowTablePwf[i + 1];
      if (pwf >= lowP && pwf <= highP) {
        double fraction = (pwf - lowP) / (highP - lowP);
        return inflowTableRate[i] + fraction * (inflowTableRate[i + 1] - inflowTableRate[i]);
      }
    }
    return inflowTableRate[lastIndex];
  }

  private double interpolatePressureForFlow(double flow) {
    if (flow <= inflowTableRate[0]) {
      return inflowTablePwf[0];
    }
    int lastIndex = inflowTableRate.length - 1;
    if (flow >= inflowTableRate[lastIndex]) {
      return inflowTablePwf[lastIndex];
    }
    for (int i = 0; i < lastIndex; i++) {
      double lowQ = inflowTableRate[i];
      double highQ = inflowTableRate[i + 1];
      if (flow >= lowQ && flow <= highQ) {
        double fraction = (flow - lowQ) / (highQ - lowQ);
        return inflowTablePwf[i] + fraction * (inflowTablePwf[i + 1] - inflowTablePwf[i]);
      }
    }
    return inflowTablePwf[lastIndex];
  }

  private void sortTableByPressure() {
    for (int i = 0; i < inflowTablePwf.length - 1; i++) {
      for (int j = 0; j < inflowTablePwf.length - i - 1; j++) {
        if (inflowTablePwf[j] > inflowTablePwf[j + 1]) {
          double tempP = inflowTablePwf[j];
          inflowTablePwf[j] = inflowTablePwf[j + 1];
          inflowTablePwf[j + 1] = tempP;

          double tempQ = inflowTableRate[j];
          inflowTableRate[j] = inflowTableRate[j + 1];
          inflowTableRate[j + 1] = tempQ;
        }
      }
    }
  }
}

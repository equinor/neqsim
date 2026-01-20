package neqsim.process.equipment.reservoir;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * WellFlow class - Inflow Performance Relationship (IPR) model for reservoir-to-wellbore flow.
 * </p>
 *
 * <p>
 * This class models the reservoir inflow into the wellbore using various IPR correlations. It
 * calculates either the bottom-hole flowing pressure (Pwf) from a specified flow rate, or the flow
 * rate from a specified Pwf, depending on the operating mode.
 * </p>
 *
 * <h2>Supported IPR Models</h2>
 * <ul>
 * <li><b>PRODUCTION_INDEX</b> - Constant PI using squared-pressure drawdown: q = PI × (Pr² -
 * Pwf²)</li>
 * <li><b>VOGEL</b> - Solution gas drive oil wells (1968): q/qmax = 1 - 0.2(Pwf/Pr) -
 * 0.8(Pwf/Pr)²</li>
 * <li><b>FETKOVICH</b> - Gas well deliverability (1973): q = C × (Pr² - Pwf²)ⁿ</li>
 * <li><b>BACKPRESSURE</b> - Non-Darcy/turbulence effects: Pr² - Pwf² = a×q + b×q²</li>
 * <li><b>TABLE</b> - Tabulated IPR from well tests</li>
 * </ul>
 *
 * <h2>Usage Example 1 - Basic Gas Well</h2>
 * 
 * <pre>{@code
 * // Create reservoir stream at reservoir conditions
 * Stream reservoirStream = new Stream("reservoir", reservoirFluid);
 * reservoirStream.setFlowRate(5.0, "MSm3/day");
 * reservoirStream.run();
 *
 * // Create WellFlow with production index
 * WellFlow well = new WellFlow("gas producer");
 * well.setInletStream(reservoirStream);
 * well.setWellProductionIndex(5.0e-4); // MSm3/day/bar²
 * well.run();
 *
 * System.out.println("BHP = " + well.getOutletStream().getPressure("bara") + " bara");
 * }</pre>
 *
 * <h2>Usage Example 2 - Vogel IPR for Oil Well</h2>
 * 
 * <pre>{@code
 * WellFlow oilWell = new WellFlow("oil producer");
 * oilWell.setInletStream(reservoirStream);
 *
 * // Set Vogel parameters from well test
 * // qTest=3000 Sm3/day at Pwf=180 bara, Pr=250 bara
 * oilWell.setVogelParameters(3000, 180, 250);
 * oilWell.run();
 * }</pre>
 *
 * <h2>Usage Example 3 - Multi-Layer Commingled Well</h2>
 * 
 * <pre>{@code
 * WellFlow commingledWell = new WellFlow("commingled producer");
 * commingledWell.setInletStream(mainReservoirStream);
 *
 * // Add multiple producing layers
 * commingledWell.addLayer("Upper Sand", upperStream, 220.0, 3.0e-4);
 * commingledWell.addLayer("Middle Sand", middleStream, 200.0, 2.0e-4);
 * commingledWell.addLayer("Lower Sand", lowerStream, 180.0, 1.5e-4);
 *
 * // Solve for commingled production at specified BHP
 * commingledWell.setOutletPressure(150.0, "bara");
 * commingledWell.solveFlowFromOutletPressure(true);
 * commingledWell.run();
 *
 * // Get individual layer contributions
 * double[] layerRates = commingledWell.getLayerFlowRates("Sm3/day");
 * }</pre>
 *
 * <h2>Integration with Process Simulation</h2>
 * 
 * <pre>{@code
 * // Create reservoir
 * SimpleReservoir reservoir = new SimpleReservoir("Field");
 * reservoir.setReservoirFluid(fluid, gasVol, oilVol, waterVol);
 * StreamInterface prodStream = reservoir.addGasProducer("PROD-1");
 * prodStream.setFlowRate(5.0, "MSm3/day");
 *
 * // Create well with IPR
 * WellFlow wellIPR = new WellFlow("PROD-1 IPR");
 * wellIPR.setInletStream(prodStream);
 * wellIPR.setWellProductionIndex(5.0e-4);
 *
 * // Create tubing (VLP)
 * PipeBeggsAndBrills tubing = new PipeBeggsAndBrills("tubing", wellIPR.getOutletStream());
 * tubing.setLength(2500);
 * tubing.setElevation(2500);
 * tubing.setDiameter(0.1016);
 *
 * // Add to process system
 * ProcessSystem process = new ProcessSystem();
 * process.add(reservoir);
 * process.add(wellIPR);
 * process.add(tubing);
 * process.run();
 * }</pre>
 *
 * @author asmund
 * @version $Id: $Id
 * @see TubingPerformance
 * @see WellSystem
 * @see SimpleReservoir
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

  // Multi-layer support
  private List<ReservoirLayer> layers = new ArrayList<>();
  private boolean isMultiLayer = false;

  /**
   * Represents a single reservoir layer for commingled well production.
   */
  public static class ReservoirLayer {
    /** Layer name. */
    public String name;
    /** Stream from this layer. */
    public StreamInterface stream;
    /** Reservoir pressure for this layer (bara). */
    public double reservoirPressure;
    /** Productivity index for this layer. */
    public double productivityIndex;
    /** Calculated flow rate from this layer. */
    public double calculatedRate;

    /**
     * Create a reservoir layer.
     *
     * @param name layer identifier
     * @param stream fluid stream from layer
     * @param reservoirPressure layer reservoir pressure (bara)
     * @param pi layer productivity index
     */
    public ReservoirLayer(String name, StreamInterface stream, double reservoirPressure,
        double pi) {
      this.name = name;
      this.stream = stream;
      this.reservoirPressure = reservoirPressure;
      this.productivityIndex = pi;
    }
  }

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

  /**
   * Add a reservoir layer for commingled production.
   * 
   * <p>
   * Multiple layers can contribute to the total well flow. Each layer has its own reservoir
   * pressure and productivity index. The flow from each layer is calculated based on the common
   * bottom-hole pressure.
   * </p>
   *
   * @param name layer identifier
   * @param stream stream representing the layer fluid
   * @param reservoirPressure layer reservoir pressure (bara)
   * @param pi layer productivity index (Sm3/day/bar² for gas)
   */
  public void addLayer(String name, StreamInterface stream, double reservoirPressure, double pi) {
    layers.add(new ReservoirLayer(name, stream, reservoirPressure, pi));
    isMultiLayer = true;
  }

  /**
   * Get the number of reservoir layers.
   *
   * @return number of layers
   */
  public int getNumberOfLayers() {
    return layers.size();
  }

  /**
   * Get flow rates from individual layers.
   *
   * @param unit flow rate unit ("Sm3/day", "MSm3/day", etc.)
   * @return array of layer flow rates
   */
  public double[] getLayerFlowRates(String unit) {
    double[] rates = new double[layers.size()];
    for (int i = 0; i < layers.size(); i++) {
      double rate = layers.get(i).calculatedRate;
      if (unit.equalsIgnoreCase("MSm3/day")) {
        rate /= 1.0e6;
      }
      rates[i] = rate;
    }
    return rates;
  }

  /**
   * Get a specific layer by index.
   *
   * @param index layer index
   * @return ReservoirLayer object
   */
  public ReservoirLayer getLayer(int index) {
    return layers.get(index);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (isMultiLayer) {
      runMultiLayer(id);
      return;
    }

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
                "run:calcOutletPressure", "pressure",
                "- Outlet pressure is negative" + pressureOut));
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
                "run:calcOutletPressure", "pressure",
                "- Outlet pressure is negative" + pressureOut));
          }
          outStream.setPressure(Math.sqrt(Math.pow(presRes, 2.0) - delta), "bara");
        } else {
          double pwf = thermoSystem.getPressure("bara");
          double flow =
              fetkovichC * Math.pow(Math.pow(presRes, 2.0) - Math.pow(pwf, 2.0), fetkovichN);
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
                "run:calcOutletPressure", "pressure",
                "- Outlet pressure is negative" + pressureOut));
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
            if (Math.pow(presRes, 2.0)
                - getInletStream().getFlowRate("MSm3/day") / wellProductionIndex > 0) {
              presout = Math.sqrt(Math.pow(presRes, 2.0)
                  - getInletStream().getFlowRate("MSm3/day") / wellProductionIndex);
            } else {
              logger.error("pressure lower that 0");
              throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
                  "run:calcOutletPressure", "pressure",
                  "- Outlet pressure is negative" + pressureOut));
            }
            outStream.setPressure(presout, "bara");
          } else {
            double flow = wellProductionIndex
                * (Math.pow(presRes, 2.0) - Math.pow(thermoSystem.getPressure("bara"), 2.0));
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

  /**
   * Run multi-layer commingled production calculation.
   * 
   * <p>
   * For commingled wells, the flow from each layer is calculated based on the common bottom-hole
   * pressure. The total flow is the sum of individual layer contributions.
   * </p>
   *
   * @param id calculation UUID
   */
  private void runMultiLayer(UUID id) {
    if (layers.isEmpty()) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("WellFlow",
          "runMultiLayer", "layers", "- No layers defined for multi-layer well"));
    }

    double pwf = pressureOut; // Common bottom-hole pressure

    // Calculate flow from each layer
    double totalFlow = 0.0;
    for (ReservoirLayer layer : layers) {
      double presRes = layer.reservoirPressure;
      double pi = layer.productivityIndex;
      // Using production index equation: q = PI * (Pres² - Pwf²)
      double layerFlow = pi * (Math.pow(presRes, 2.0) - Math.pow(pwf, 2.0));
      if (layerFlow < 0) {
        layerFlow = 0.0; // Layer may not contribute if PWF > reservoir pressure
      }
      layer.calculatedRate = layerFlow;
      totalFlow += layerFlow;
    }

    // Set up output stream with blended composition (using first layer as base)
    thermoSystem = layers.get(0).stream.getThermoSystem().clone();
    thermoSystem.setPressure(pwf, "bara");
    outStream.setThermoSystem(thermoSystem);
    outStream.setFlowRate(totalFlow, "MSm3/day");
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
   * Specify the well outlet pressure to be used when solving for flow from backpressure (i.e.
   * {@link #solveFlowFromOutletPressure(boolean)} set to true).
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
    this.vogelQmax = qTest / (1.0 - 0.2 * (pwfTest / reservoirPressure)
        - 0.8 * Math.pow(pwfTest / reservoirPressure, 2.0));
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
   * Use backpressure equation for gas wells: p<sub>res</sub><sup>2</sup> -
   * p<sub>wf</sub><sup>2</sup> = a·q + b·q². Parameter {@code b} captures non-Darcy (turbulence)
   * effects.
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
    if (bottomHolePressures == null || flowRates == null
        || bottomHolePressures.length != flowRates.length || bottomHolePressures.length < 2) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("WellFlow", "setTableInflow", "table",
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
   * Estimate well production index from Darcy law parameters. Units: permeability in mD, viscosity
   * in cP and lengths in meter.
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

  /**
   * Load IPR curve from a CSV file.
   *
   * <p>
   * The CSV file should have two columns: bottom-hole pressure (bara) and flow rate. The first row
   * can be a header (will be skipped if non-numeric). Columns can be separated by comma, semicolon,
   * or tab.
   * </p>
   *
   * <p>
   * Example CSV format:
   * </p>
   * 
   * <pre>
   * Pwf(bara),Rate(MSm3/day)
   * 50,5.2
   * 80,4.1
   * 100,3.2
   * 120,2.4
   * 150,1.5
   * 180,0.8
   * 200,0.2
   * </pre>
   *
   * @param filePath path to the CSV file
   * @throws IOException if file cannot be read
   */
  public void loadIPRFromFile(String filePath) throws IOException {
    loadIPRFromFile(java.nio.file.Paths.get(filePath));
  }

  /**
   * Load IPR curve from a CSV file.
   *
   * @param filePath path to the CSV file
   * @throws IOException if file cannot be read
   * @see #loadIPRFromFile(String)
   */
  public void loadIPRFromFile(Path filePath) throws IOException {
    List<Double> pressures = new ArrayList<>();
    List<Double> rates = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(filePath)) {
      String line;
      boolean firstLine = true;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue; // Skip empty lines and comments
        }

        // Split by comma, semicolon, or tab
        String[] parts = line.split("[,;\t]+");
        if (parts.length < 2) {
          continue;
        }

        try {
          double pressure = Double.parseDouble(parts[0].trim());
          double rate = Double.parseDouble(parts[1].trim());
          pressures.add(pressure);
          rates.add(rate);
        } catch (NumberFormatException e) {
          if (firstLine) {
            // Skip header row
            firstLine = false;
            continue;
          }
          logger.warn("Skipping invalid line in IPR file: {}", line);
        }
        firstLine = false;
      }
    }

    if (pressures.size() < 2) {
      throw new IOException("IPR file must contain at least 2 valid data points: " + filePath);
    }

    double[] pwfArray = pressures.stream().mapToDouble(Double::doubleValue).toArray();
    double[] rateArray = rates.stream().mapToDouble(Double::doubleValue).toArray();
    setTableInflow(pwfArray, rateArray);

    logger.info("Loaded IPR curve with {} points from {}", pressures.size(), filePath);
  }

  /**
   * Get the current IPR table pressures.
   *
   * @return array of bottom-hole pressures (bara), or empty array if not using table IPR
   */
  public double[] getIPRTablePressures() {
    return java.util.Arrays.copyOf(inflowTablePwf, inflowTablePwf.length);
  }

  /**
   * Get the current IPR table flow rates.
   *
   * @return array of flow rates, or empty array if not using table IPR
   */
  public double[] getIPRTableRates() {
    return java.util.Arrays.copyOf(inflowTableRate, inflowTableRate.length);
  }
}

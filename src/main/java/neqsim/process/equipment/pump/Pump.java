package neqsim.process.equipment.pump;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.UUID;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;
import neqsim.process.util.monitor.PumpResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Centrifugal pump simulation model for process systems.
 *
 * <p>
 * This class simulates a centrifugal pump using either:
 * </p>
 * <ul>
 * <li>Isentropic compression with specified outlet pressure and efficiency</li>
 * <li>Manufacturer pump curves via {@link PumpChart} for realistic performance</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Pump Curves:</b> Support for head, efficiency, and NPSH curves with affinity law
 * scaling</li>
 * <li><b>Density Correction:</b> Automatic head correction when pumping fluids different from chart
 * test fluid</li>
 * <li><b>NPSH Monitoring:</b> Cavitation detection based on available vs required NPSH</li>
 * <li><b>Operating Status:</b> Surge, stonewall, and efficiency monitoring</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * // Simple usage with outlet pressure
 * Pump pump = new Pump("MainPump", feedStream);
 * pump.setOutletPressure(10.0, "bara");
 * pump.setIsentropicEfficiency(0.75);
 * pump.run();
 *
 * // With manufacturer pump curves
 * double[] speed = {1000.0, 1500.0};
 * double[][] flow = {{10, 20, 30}, {15, 30, 45}};
 * double[][] head = {{100, 95, 85}, {225, 214, 191}};
 * double[][] efficiency = {{70, 80, 75}, {72, 82, 77}};
 * double[] chartConditions = {18.0, 298.15, 1.0, 1.0, 998.0}; // Include ref density
 * pump.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);
 * pump.getPumpChart().setHeadUnit("meter");
 * pump.setSpeed(1200.0);
 * pump.run();
 * }</pre>
 *
 * @author esol
 * @version $Id: $Id
 * @see PumpChart
 * @see PumpChartInterface
 */
public class Pump extends TwoPortEquipment
    implements PumpInterface, neqsim.process.equipment.capacity.CapacityConstrainedEquipment,
    neqsim.process.design.AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Pump.class);

  /** Mechanical design for the pump. */
  private PumpMechanicalDesign mechanicalDesign;

  SystemInterface thermoSystem;
  double dH = 0.0;
  double pressure = 0.0;
  private double molarFlow = 10.0;
  private double speed = 1000.0;
  private double minimumFlow = 1e-20;

  private double outTemperature = 298.15;
  private boolean useOutTemperature = false;
  private boolean calculateAsCompressor = true;
  public double isentropicEfficiency = 1.0;
  public boolean powerSet = false;
  private String pressureUnit = "bara";
  private PumpChartInterface pumpChart = new PumpChart();
  private boolean checkNPSH = false;
  private double npshMargin = 1.3; // Safety margin for NPSH (NPSHa should be > npshMargin *
                                   // NPSHr)

  /**
   * Constructor for Pump.
   *
   * @param name name of pump
   */
  public Pump(String name) {
    super(name);
    initMechanicalDesign();
  }

  /**
   * <p>
   * Constructor for Pump.
   * </p>
   *
   * @param name name of pump
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Pump(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public PumpMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new PumpMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public double getEnergy() {
    return dH;
  }

  /** {@inheritDoc} */
  @Override
  public double getPower() {
    return dH;
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return getPower();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return getMechanicalDesign().maxDesignPower;
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(dH, "W");
    return powerUnit.getValue(unit);
  }

  /**
   * <p>
   * getDuty.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    return dH;
  }

  /**
   * <p>
   * calculateAsCompressor.
   * </p>
   *
   * @param setPumpCalcType a boolean
   */
  public void calculateAsCompressor(boolean setPumpCalcType) {
    calculateAsCompressor = setPumpCalcType;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("pump running..");
    if (inStream.getFlowRate("kg/sec") < minimumFlow) {
      thermoSystem = inStream.getThermoSystem().clone();
      thermoSystem.setPressure(pressure, pressureUnit);
      thermoSystem.init(3);
      dH = 0.0;
      outStream.setThermoSystem(thermoSystem);
      outStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      return;
    }

    // Check for cavitation risk if enabled
    if (checkNPSH && isCavitating()) {
      logger.warn("Pump " + getName() + " may be operating in cavitation conditions");
    }

    inStream.getThermoSystem().init(3);
    double hinn = inStream.getThermoSystem().getEnthalpy();
    double entropy = inStream.getThermoSystem().getEntropy();

    if (useOutTemperature) {
      thermoSystem = inStream.getThermoSystem().clone();
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
      // thermoSystem.setTotalNumberOfMoles(molarFlow);
      thermoSystem.setTemperature(outTemperature);
      thermoSystem.setPressure(pressure, pressureUnit);
      thermoOps.TPflash();
      thermoSystem.init(3);
    } else {
      if (!pumpChart.isUsePumpChart() && calculateAsCompressor) {
        thermoSystem = inStream.getThermoSystem().clone();
        thermoSystem.setPressure(pressure, pressureUnit);
        // System.out.println("entropy inn.." + entropy);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.PSflash(entropy);
        // double densOutIdeal = getThermoSystem().getDensity();
        if (!powerSet) {
          dH = (getThermoSystem().getEnthalpy() - hinn) / isentropicEfficiency;
        }
        double hout = hinn + dH;
        isentropicEfficiency = (getThermoSystem().getEnthalpy() - hinn) / dH;
        dH = hout - hinn;
        thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps.PHflash(hout, 0);
      } else if (pumpChart.isUsePumpChart()) {
        thermoSystem = inStream.getThermoSystem().clone();
        double flowRate_m3hr = inStream.getThermoSystem().getFlowRate("m3/hr");
        double densityInlet = inStream.getThermoSystem().getDensity("kg/m3");

        // Get kinematic viscosity for viscosity correction (cSt = mm²/s)
        // Dynamic viscosity (Pa·s) / density (kg/m³) × 1e6 = cSt
        double viscosity_cSt = 1.0; // Default to water-like
        try {
          thermoSystem.initPhysicalProperties();
          if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
            int liquidPhase =
                thermoSystem.hasPhaseType("oil") ? thermoSystem.getPhaseNumberOfPhase("oil")
                    : thermoSystem.getPhaseNumberOfPhase("aqueous");
            double dynViscosity =
                thermoSystem.getPhase(liquidPhase).getPhysicalProperties().getViscosity(); // Pa·s
            double phaseDensity = thermoSystem.getPhase(liquidPhase).getDensity("kg/m3");
            viscosity_cSt = (dynViscosity / phaseDensity) * 1.0e6;
          }
        } catch (Exception e) {
          logger.debug("Could not get viscosity, using default: " + e.getMessage());
        }

        // Get head and efficiency with optional density and viscosity corrections
        double pumpHead;
        double efficiencyPercent;

        if (getPumpChart().isUseViscosityCorrection() && viscosity_cSt > 4.0) {
          // Use fully corrected values for viscous fluids
          pumpHead = getPumpChart().getFullyCorrectedHead(flowRate_m3hr, getSpeed(), densityInlet,
              viscosity_cSt);
          efficiencyPercent =
              getPumpChart().getCorrectedEfficiency(flowRate_m3hr, getSpeed(), viscosity_cSt);
          logger.debug("Using viscosity correction: ν={} cSt, Cq={}, Ch={}, Cη={}",
              String.format("%.1f", viscosity_cSt),
              String.format("%.3f", getPumpChart().getFlowCorrectionFactor()),
              String.format("%.3f", getPumpChart().getHeadCorrectionFactor()),
              String.format("%.3f", getPumpChart().getEfficiencyCorrectionFactor()));
        } else {
          // Only density correction (or no correction)
          pumpHead = getPumpChart().getCorrectedHead(flowRate_m3hr, getSpeed(), densityInlet);
          efficiencyPercent = getPumpChart().getEfficiency(flowRate_m3hr, getSpeed());
        }

        double efficiencyDecimal = efficiencyPercent / 100.0;
        isentropicEfficiency = efficiencyPercent; // Store as percentage for consistency

        // Calculate pressure rise based on head unit
        double deltaP_Pa; // Pressure rise in Pa

        if (getPumpChart().getHeadUnit().equals("meter")) {
          // Head in meters: ΔP = ρ·g·H
          deltaP_Pa = pumpHead * densityInlet * ThermodynamicConstantsInterface.gravity;
        } else if (getPumpChart().getHeadUnit().equals("kJ/kg")) {
          // Specific energy in kJ/kg: ΔP = E·ρ
          deltaP_Pa = pumpHead * 1000.0 * densityInlet; // Convert kJ to J
        } else {
          throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "run",
              "headUnit", "Unsupported head unit: " + getPumpChart().getHeadUnit()
                  + ". Use 'meter' or 'kJ/kg'."));
        }

        // Set outlet pressure
        double deltaP_bar = deltaP_Pa / 1.0e5;
        thermoSystem.setPressure(inStream.getPressure() + deltaP_bar);

        // Calculate shaft power and enthalpy rise
        double volumetricFlow = thermoSystem.getFlowRate("kg/sec") / densityInlet; // m³/s
        double hydraulicPower = volumetricFlow * deltaP_Pa; // W
        double shaftPower = hydraulicPower / efficiencyDecimal; // W (accounts for losses)
        dH = shaftPower; // J/s = W

        // Flash to find outlet temperature
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        double hout = hinn + dH;
        thermoOps.PHflash(hout, 0);
        thermoSystem.init(3);
      } else {
        // Simple pressure rise calculation without pump curve
        thermoSystem = inStream.getThermoSystem().clone();
        thermoSystem.setPressure(pressure, pressureUnit);

        // Calculate hydraulic power and shaft power
        double volumetricFlow =
            thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3"); // m³/s
        double deltaP_Pa =
            thermoSystem.getPressure("Pa") - inStream.getThermoSystem().getPressure("Pa");
        double hydraulicPower = volumetricFlow * deltaP_Pa; // W
        double shaftPower = hydraulicPower / isentropicEfficiency; // W
        dH = shaftPower;

        // Flash to find outlet temperature
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        double hout = hinn + dH;
        thermoOps.PHflash(hout, 0);
        thermoSystem.init(3);
      }
    }

    // double entropy= inletStream.getThermoSystem().getEntropy();
    // thermoSystem.setPressure(pressure);
    // System.out.println("entropy inn.." + entropy);
    // thermoOps.PSflash(entropy);
    dH = thermoSystem.getEnthalpy() - hinn;
    outStream.setThermoSystem(thermoSystem);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    // outStream.run(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    thermoSystem.initPhysicalProperties();
    String[][] table = new String[50][5];
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhases()[i].getComponent(j).getx(), buf, test).toString();
        table[j + 1][4] = "[-]";
      }
      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getDensity(), buf, test)
              .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

      // Double.longValue(thermoSystem.getPhases()[i].getBeta());
      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getBeta(), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getMolarMass() * 1000, buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] =
          nf.format((thermoSystem.getPhases()[i].getCp()
              / thermoSystem.getPhases()[i].getNumberOfMolesInPhase() * 1.0
              / thermoSystem.getPhases()[i].getMolarMass() * 1000), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] =
          nf.format((thermoSystem.getPhases()[i].getPhysicalProperties().getViscosity()), buf, test)
              .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getPhysicalProperties().getConductivity(), buf, test)
          .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] =
          Double.toString(thermoSystem.getPhases()[i].getPressure());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          Double.toString(thermoSystem.getPhases()[i].getTemperature());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
      Double.toString(thermoSystem.getPhases()[i].getTemperature());

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * Getter for the field <code>molarFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getMolarFlow() {
    return molarFlow;
  }

  /**
   * <p>
   * Setter for the field <code>molarFlow</code>.
   * </p>
   *
   * @param molarFlow a double
   */
  public void setMolarFlow(double molarFlow) {
    this.molarFlow = molarFlow;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * <p>
   * Getter for the field <code>isentropicEfficiency</code>.
   * </p>
   *
   * @return the isentropicEfficiency
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>isentropicEfficiency</code>.
   * </p>
   *
   * @param isentropicEfficiency the isentropicEfficiency to set
   */
  public void setIsentropicEfficiency(double isentropicEfficiency) {
    this.isentropicEfficiency = isentropicEfficiency;
  }

  /**
   * <p>
   * Getter for the field <code>outTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getOutTemperature() {
    if (useOutTemperature) {
      return outTemperature;
    } else {
      return getThermoSystem().getTemperature();
    }
  }

  /**
   * <p>
   * Set the outlet temperature of the pump.
   * </p>
   *
   * @param outTemperature outlet temperature in Kelvin
   */
  @Override
  public void setOutletTemperature(double outTemperature) {
    useOutTemperature = true;
    this.outTemperature = outTemperature;
  }

  /**
   * <p>
   * Set the outlet temperature of the pump with unit specification.
   * </p>
   *
   * @param temperature outlet temperature value
   * @param unit temperature unit (e.g., "K", "C", "R", "F")
   */
  @Override
  public void setOutletTemperature(double temperature, String unit) {
    useOutTemperature = true;
    if (unit.equalsIgnoreCase("K") || unit.equalsIgnoreCase("Kelvin")) {
      this.outTemperature = temperature;
    } else if (unit.equalsIgnoreCase("C") || unit.equalsIgnoreCase("Celsius")) {
      this.outTemperature = temperature + 273.15;
    } else if (unit.equalsIgnoreCase("F") || unit.equalsIgnoreCase("Fahrenheit")) {
      this.outTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else if (unit.equalsIgnoreCase("R") || unit.equalsIgnoreCase("Rankine")) {
      this.outTemperature = temperature * 5.0 / 9.0;
    } else {
      this.outTemperature = temperature;
    }
  }

  /**
   * <p>
   * Setter for the field <code>outTemperature</code>.
   * </p>
   *
   * @param outTemperature outlet temperature in Kelvin
   * @deprecated use {@link #setOutletTemperature(double)} instead
   */
  @Deprecated
  public void setOutTemperature(double outTemperature) {
    setOutletTemperature(outTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    setOutletPressure(pressure);
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unit) {
    setOutletPressure(pressure);
    pressureUnit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOutletPressure(double pressure, String unit) {
    setOutletPressure(pressure);
    pressureUnit = unit;
  }

  /**
   * <p>
   * Setter for the field <code>speed</code>.
   * </p>
   *
   * @param speed a double
   */
  public void setSpeed(double speed) {
    this.speed = speed;
  }

  /**
   * <p>
   * Getter for the field <code>speed</code>.
   * </p>
   *
   * @return a double
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * <p>
   * Getter for the field <code>pumpChart</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.pump.PumpChart} object
   */
  public PumpChartInterface getPumpChart() {
    return pumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new PumpResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    PumpResponse res = new PumpResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  public void setPumpChartType(String type) {
    if (type.equals("simple") || type.equals("fan law")) {
      pumpChart = new PumpChart();
    } else if (type.equals("interpolate and extrapolate")) {
      pumpChart = new PumpChartAlternativeMapLookupExtrapolate();
    } else {
      pumpChart = new PumpChart();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void setMinimumFlow(double minimumFlow) {
    this.minimumFlow = minimumFlow;
  }

  /**
   * Calculate the Net Positive Suction Head Available (NPSHa) at the pump inlet.
   *
   * <p>
   * NPSHa represents the absolute pressure head available at the pump suction above the fluid vapor
   * pressure. It must exceed the pump's NPSHr to avoid cavitation.
   * </p>
   *
   * <p>
   * Formula: NPSHa = (P_suction - P_vapor) / (ρ·g) + v²/(2·g) + z
   * </p>
   *
   * @return NPSHa in meters
   */
  public double getNPSHAvailable() {
    try {
      inStream.getThermoSystem().init(3);
      double pressureSuction = inStream.getPressure("Pa");
      double temperature = inStream.getTemperature("K");

      // Calculate vapor pressure at inlet temperature
      SystemInterface tempSystem = inStream.getThermoSystem().clone();
      tempSystem.setTemperature(temperature);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempSystem);
      try {
        thermoOps.bubblePointPressureFlash(false);
      } catch (Exception e) {
        // If bubble point flash fails, fluid might be in supercritical state or single
        // phase
        // Use a conservative estimate or return high value
        logger.warn("Could not calculate vapor pressure for NPSH calculation: " + e.getMessage());
        return 1000.0; // Return large value to indicate no cavitation risk
      }
      double pressureVapor = tempSystem.getPressure("Pa");

      double density = inStream.getThermoSystem().getDensity("kg/m3");

      // Calculate velocity from flow rate and pipe diameter if available
      // For conservative estimate, assume low velocity contribution
      double velocity = 0.0; // m/s - velocity head contribution typically small

      // NPSH_a = (P_suction - P_vapor)/(ρ·g) + v²/(2·g)
      // Elevation term (z) is typically handled in the system layout
      double npsha =
          (pressureSuction - pressureVapor) / (density * ThermodynamicConstantsInterface.gravity)
              + (velocity * velocity) / (2.0 * ThermodynamicConstantsInterface.gravity);

      return npsha;
    } catch (Exception e) {
      logger.error("Error calculating NPSH available: " + e.getMessage());
      return 0.0;
    }
  }

  /**
   * Get the required Net Positive Suction Head (NPSHr) for the pump at current operating
   * conditions.
   *
   * <p>
   * If a pump chart with NPSH curve is available, uses manufacturer data. Otherwise returns a
   * conservative estimate based on pump type and flow rate.
   * </p>
   *
   * @return NPSHr in meters
   */
  public double getNPSHRequired() {
    // Try to get NPSH from pump chart if available
    if (pumpChart != null && pumpChart.isUsePumpChart() && pumpChart.hasNPSHCurve()) {
      try {
        double flowRate = inStream.getFlowRate("m3/hr");
        double npshFromChart = pumpChart.getNPSHRequired(flowRate, speed);
        if (npshFromChart > 0.0) {
          return npshFromChart;
        }
      } catch (Exception e) {
        logger.warn(
            "Error reading NPSH from pump chart: " + e.getMessage() + ". Using estimated value.");
      }
    }

    // Fallback: Conservative estimate based on pump flow rate
    // Typical values: 2-10 m for centrifugal pumps
    double flowRate = inStream.getFlowRate("m3/hr");
    if (flowRate < 10) {
      return 2.0; // Low flow pumps
    } else if (flowRate < 100) {
      return 5.0; // Medium flow pumps
    } else {
      return 8.0; // High flow pumps
    }
  }

  /**
   * Check if the pump is at risk of cavitation based on NPSH criteria.
   *
   * <p>
   * Cavitation occurs when NPSHa &lt; NPSHr, causing vapor bubbles to form and collapse,
   * potentially damaging the pump.
   * </p>
   *
   * @return true if cavitation risk exists (NPSHa &lt; npshMargin * NPSHr)
   */
  public boolean isCavitating() {
    if (!checkNPSH) {
      return false;
    }
    double npsha = getNPSHAvailable();
    double npshr = getNPSHRequired();
    boolean cavitating = npsha < (npshMargin * npshr);
    if (cavitating) {
      logger.warn("Pump " + getName() + " cavitation risk: NPSHa=" + String.format("%.2f", npsha)
          + " m < " + String.format("%.2f", npshMargin) + " × NPSHr=" + String.format("%.2f", npshr)
          + " m");
    }
    return cavitating;
  }

  /**
   * Enable or disable NPSH checking during pump operation.
   *
   * @param checkNPSH true to enable NPSH checking
   */
  public void setCheckNPSH(boolean checkNPSH) {
    this.checkNPSH = checkNPSH;
  }

  /**
   * Get whether NPSH checking is enabled.
   *
   * @return true if NPSH checking is enabled
   */
  public boolean isCheckNPSH() {
    return checkNPSH;
  }

  /**
   * Set the NPSH safety margin. NPSHa must be greater than npshMargin × NPSHr.
   *
   * @param npshMargin safety margin factor (typically 1.1 to 1.5)
   */
  public void setNPSHMargin(double npshMargin) {
    this.npshMargin = npshMargin;
  }

  /**
   * Get the NPSH safety margin factor.
   *
   * @return NPSH margin factor
   */
  public double getNPSHMargin() {
    return npshMargin;
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** Flag indicating if pump has been auto-sized. */
  private boolean autoSized = false;

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (inStream == null) {
      throw new IllegalStateException("Inlet stream must be connected before auto-sizing pump");
    }

    // Run the inlet stream to get current conditions
    inStream.run();
    inStream.getThermoSystem().initPhysicalProperties();

    // Get flow rate and conditions
    double volumeFlow = inStream.getFlowRate("m3/hr");
    double massFlow = inStream.getFlowRate("kg/hr");

    // Calculate design flow with safety factor
    double designVolumeFlow = volumeFlow * safetyFactor;
    double designMassFlow = massFlow * safetyFactor;

    // Set mechanical design parameters
    if (mechanicalDesign != null) {
      mechanicalDesign.setMaxDesignVolumeFlow(designVolumeFlow);

      // Calculate design power based on current operating point with safety
      if (dH > 0) {
        double designPower = dH * safetyFactor * safetyFactor; // Power scales with flow squared
        mechanicalDesign.setMaxDesignPower(designPower);
      }
    }

    // Update capacity constraints with new design values
    initializeCapacityConstraints();

    autoSized = true;
    logger.info("Pump '{}' auto-sized: Design flow = {:.1f} m3/hr, Safety factor = {}", getName(),
        designVolumeFlow, safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2); // Default 20% margin
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String companyStandard, String trDocument) {
    // Set company standard on mechanical design
    if (mechanicalDesign != null) {
      mechanicalDesign.setCompanySpecificDesignStandards(companyStandard);
      mechanicalDesign.readDesignSpecifications();
    }

    // Use company-specific safety factor if available, otherwise default
    double safetyFactor = 1.2;
    autoSize(safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Pump Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");

    if (inStream != null) {
      sb.append("\n--- Design Basis ---\n");
      sb.append("Flow Rate: ").append(String.format("%.2f m3/hr", inStream.getFlowRate("m3/hr")))
          .append("\n");
      sb.append("Mass Flow: ").append(String.format("%.2f kg/hr", inStream.getFlowRate("kg/hr")))
          .append("\n");
      sb.append("Inlet Pressure: ").append(String.format("%.2f bara", inStream.getPressure("bara")))
          .append("\n");
      sb.append("Outlet Pressure: ").append(String.format("%.2f bara", pressure)).append("\n");
    }

    sb.append("\n--- Operating Parameters ---\n");
    sb.append("Power: ").append(String.format("%.2f kW", getPower("kW"))).append("\n");
    sb.append("Isentropic Efficiency: ").append(String.format("%.1f%%", isentropicEfficiency * 100))
        .append("\n");
    sb.append("Speed: ").append(String.format("%.0f RPM", speed)).append("\n");

    if (mechanicalDesign != null) {
      sb.append("\n--- Design Limits ---\n");
      sb.append("Max Design Power: ")
          .append(String.format("%.2f kW", mechanicalDesign.maxDesignPower / 1000.0)).append("\n");
      sb.append("Max Design Flow: ")
          .append(String.format("%.2f m3/hr", mechanicalDesign.getMaxDesignVolumeFlow()))
          .append("\n");
    }

    if (checkNPSH) {
      sb.append("\n--- NPSH Analysis ---\n");
      sb.append("NPSHa: ").append(String.format("%.2f m", getNPSHAvailable())).append("\n");
      sb.append("NPSHr: ").append(String.format("%.2f m", getNPSHRequired())).append("\n");
      sb.append("NPSH Margin: ").append(String.format("%.2f", npshMargin)).append("\n");
      sb.append("Cavitation Risk: ").append(isCavitating() ? "YES" : "No").append("\n");
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
    report.put("equipmentName", getName());
    report.put("equipmentType", "Pump");
    report.put("autoSized", autoSized);

    if (inStream != null) {
      java.util.Map<String, Object> designBasis = new java.util.LinkedHashMap<>();
      designBasis.put("volumeFlow_m3hr", inStream.getFlowRate("m3/hr"));
      designBasis.put("massFlow_kghr", inStream.getFlowRate("kg/hr"));
      designBasis.put("inletPressure_bara", inStream.getPressure("bara"));
      designBasis.put("outletPressure_bara", pressure);
      report.put("designBasis", designBasis);
    }

    java.util.Map<String, Object> operating = new java.util.LinkedHashMap<>();
    operating.put("power_kW", getPower("kW"));
    operating.put("isentropicEfficiency", isentropicEfficiency);
    operating.put("speed_rpm", speed);
    report.put("operatingParameters", operating);

    if (mechanicalDesign != null) {
      java.util.Map<String, Object> limits = new java.util.LinkedHashMap<>();
      limits.put("maxDesignPower_kW", mechanicalDesign.maxDesignPower / 1000.0);
      limits.put("maxDesignFlow_m3hr", mechanicalDesign.getMaxDesignVolumeFlow());
      report.put("designLimits", limits);
    }

    if (checkNPSH) {
      java.util.Map<String, Object> npsh = new java.util.LinkedHashMap<>();
      npsh.put("npshaAvailable_m", getNPSHAvailable());
      npsh.put("npshrRequired_m", getNPSHRequired());
      npsh.put("npshMargin", npshMargin);
      npsh.put("cavitationRisk", isCavitating());
      report.put("npshAnalysis", npsh);
    }

    return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /** Storage for capacity constraints. */
  private final java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> capacityConstraints =
      new java.util.LinkedHashMap<>();

  /**
   * Initializes default capacity constraints for the pump.
   *
   * <p>
   * NOTE: All constraints are disabled by default for backwards compatibility. Enable specific
   * constraints when pump capacity analysis is needed (e.g., after sizing).
   * </p>
   */
  protected void initializeCapacityConstraints() {
    capacityConstraints.clear();
    // Power constraint (HARD limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("power", "kW",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD)
            .setDesignValue(getMechanicalDesign().maxDesignPower).setWarningThreshold(0.9)
            .setValueSupplier(() -> getPower()).setEnabled(false));

    // Flow rate constraint (DESIGN limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("flowRate",
        "m3/hr", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(getMechanicalDesign().maxDesignVolumeFlow).setWarningThreshold(0.9)
            .setValueSupplier(() -> inStream != null ? inStream.getFlowRate("m3/hr") : 0.0)
            .setEnabled(false));

    // NPSH margin constraint (SOFT limit) - disabled by default
    if (checkNPSH) {
      addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("npshMargin",
          "m", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
              .setDesignValue(npshMargin).setWarningThreshold(0.9).setValueSupplier(() -> {
                double npsha = getNPSHAvailable();
                double npshr = getNPSHRequired();
                return npsha > 0 && npshr > 0 ? npsha / (npshMargin * npshr) : 1.0;
              }).setEnabled(false));
    }
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    return java.util.Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }
}

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
import neqsim.process.util.monitor.PumpResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Pump class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Pump extends TwoPortEquipment implements PumpInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Pump.class);

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
    super(name, inletStream);
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

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    if (unit.equals("W")) {
    } else if (unit.equals("kW")) {
      return dH / 1000.0;
    } else if (unit.equals("MW")) {
      return dH / 1.0e6;
    }
    return dH;
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
        double pumpHead = getPumpChart().getHead(flowRate_m3hr, getSpeed());
        double efficiencyPercent = getPumpChart().getEfficiency(flowRate_m3hr, getSpeed());
        double efficiencyDecimal = efficiencyPercent / 100.0;
        isentropicEfficiency = efficiencyPercent; // Store as percentage for consistency

        // Calculate pressure rise based on head unit
        double deltaP_Pa; // Pressure rise in Pa
        double densityInlet = inStream.getThermoSystem().getDensity("kg/m3");

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
   * Setter for the field <code>outTemperature</code>.
   * </p>
   *
   * @param outTemperature a double
   */
  public void setOutTemperature(double outTemperature) {
    useOutTemperature = true;
    this.outTemperature = outTemperature;
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
        // If bubble point flash fails, fluid might be in supercritical state or single phase
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
}

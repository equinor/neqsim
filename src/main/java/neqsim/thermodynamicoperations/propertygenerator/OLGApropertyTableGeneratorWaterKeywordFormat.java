package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.steam.BrineSalinityCorrection;
import neqsim.thermo.util.steam.LiquidWaterReference;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Generate three-phase OLGA keyword tables, with RS based on gas plus oil mass and standard liquid ratios including the
 * separate aqueous phase. Optional bubble headers are omitted for multicomponent feeds; pure-component curves retain
 * only validated saturation points.
 *
 * @author Kjetil Raul
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorWaterKeywordFormat extends neqsim.thermodynamicoperations.BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorWaterKeywordFormat.class);

  SystemInterface thermoSystem = null;
  ThermodynamicOperations thermoOps = null;
  double stdPres = ThermodynamicConstantsInterface.referencePressure;
  double stdPresATM = 1;
  double stdTemp = 288.15;
  double[] molfracs;
  double[] MW;
  double[] dens;
  String[] components;
  double GOR;
  double GLR;
  /** Standard water volume divided by total standard liquid volume. */
  double WC;
  double stdGasDens;
  double stdLiqDens;
  double stdWatDens;
  double[] pressures;
  double[] temperatureLOG;
  double[] temperatures;
  double[] pressureLOG = null;
  double[][] ROG = null; // DROGDP, DROHLDP, DROGDT, DROHLDT;
  double[] bubP;
  double[] bubT;
  double[] dewP;
  double[] bubPLOG;
  /** Temperatures in Celsius paired with the retained bubble pressures, independent of the property grid. */
  private double[] bubbleTemperaturesC;
  double[] dewPLOG;
  double[] bubTLOG;
  double[][] ROL;
  double[][] CPG;
  double[][] CPHL;
  double[][] HG;
  double[][] HHL;
  double[][] TCG;
  double[][] TCHL;
  double[][] VISG;
  double[][] VISHL;
  double[][] SIGGHL;
  double[][] SEG;
  double[][] SEHL;
  double[][] RS;
  double TC;
  double PC;
  double TCLOG;
  double PCLOG;
  double RSWTOB;
  double[][][] props;
  int nProps;
  String[] names;
  String[] units;
  String[] namesKeyword;

  /** True at grid nodes where a gas phase exists. */
  private boolean[][] gasPresent;
  /** True at grid nodes where a hydrocarbon liquid phase exists. */
  private boolean[][] oilPresent;
  /** True at grid nodes where an aqueous phase exists. */
  private boolean[][] waterPresent;
  /** True for property columns that require a gas phase. */
  private boolean[] needsGas;
  /** True for property columns that require a hydrocarbon liquid phase. */
  private boolean[] needsOil;
  /** True for property columns that require an aqueous phase. */
  private boolean[] needsWater;
  /** Fluid label written to the table and referenced by the OLGA BRANCH FLUID key. */
  private String fluidLabel = "NewFluid";

  /** Gas density written when the grid holds no gas at all, in kg/m3. */
  private static final double DEFAULT_GAS_DENSITY = 1.0;
  /** Hydrocarbon liquid density written when the grid holds none at all, in kg/m3. */
  private static final double DEFAULT_OIL_DENSITY = 800.0;
  /** Water density written when the grid holds no aqueous phase at all, in kg/m3. */
  private static final double DEFAULT_WATER_DENSITY = 1000.0;
  /** OLGA sentinel for a standard gas/liquid ratio with no denominator phase. */
  private static final double NO_STANDARD_LIQUID = -999.0;
  /** Smallest water mass fraction of the aqueous phase for which the reference water equations are used. */
  private static final double REFERENCE_WATER_MIN_PURITY = 0.95;

  /** Feed passed to the constructor; produced water is added to a copy of it. */
  private final SystemInterface sourceSystem;
  /** Pressure axis definition in bara. */
  private final OlgaTableGrid pressureGrid = new OlgaTableGrid();
  /** Temperature axis definition in K. */
  private final OlgaTableGrid temperatureGrid = new OlgaTableGrid();
  /** True to refine the grid around the phase envelope. */
  private boolean envelopeRefinement = false;
  /** Ratio of grid points inside to outside the phase envelope. */
  private double gridFactor = 5.0;
  /** True to insert the standard pressure and temperature into the grid. */
  private boolean insertStandardConditions = false;
  /** True to fill absent-phase nodes monotonically with the phase gradients. */
  private boolean monotonicityCorrection = true;
  /** True to take aqueous properties from the IAPWS reference equations. */
  private boolean referenceWaterProperties = false;
  /** Salt mass fraction of the aqueous phase. */
  private double saltMassFraction = 0.0;
  /** Normalized produced-water specification, or null when the feed is used as given. */
  private String producedWaterSpecification = null;
  /** Produced-water specification value. */
  private double producedWaterValue = 0.0;
  /** Envelope extents of the water-free feed. */
  private OlgaEnvelopeInfo envelope = null;
  /** Water mass fraction of the aqueous phase at each node. */
  private double[][] waterPurity;
  /** Phase-existence mask per column before absent nodes were filled. */
  boolean[][][] columnPresent;
  /** Number of aqueous nodes evaluated with the reference equations. */
  private int referenceWaterNodes = 0;
  /** Number of aqueous nodes left on the equation of state because of purity or range. */
  private int referenceWaterSkippedNodes = 0;

  /**
   * Set the fluid label written to the table.
   *
   * @param label fluid label, ignored when null or empty
   */
  public void setFluidLabel(String label) {
    if (label != null && label.trim().length() > 0) {
      this.fluidLabel = label.trim();
    }
  }

  /**
   * Get the fluid label written to the table.
   *
   * @return fluid label
   */
  public String getFluidLabel() {
    return fluidLabel;
  }

  /**
   * Get a phase density, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return density in kg/m3
   */
  private static double density(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getPhysicalProperties().getDensity();
  }

  /**
   * Get a phase viscosity, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return viscosity in Ns/m2
   */
  private static double viscosity(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getPhysicalProperties().getViscosity();
  }

  /**
   * Get a phase thermal conductivity, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return conductivity in W/(m K)
   */
  private static double conductivity(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getPhysicalProperties().getConductivity();
  }

  /**
   * Get a mass-specific heat capacity, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return heat capacity in J/(kg K)
   */
  private static double specificHeatCapacity(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getCp() / phase.getNumberOfMolesInPhase() / phase.getMolarMass();
  }

  /**
   * Get a mass-specific enthalpy, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return enthalpy in J/kg
   */
  private static double specificEnthalpy(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getEnthalpy() / phase.getNumberOfMolesInPhase() / phase.getMolarMass();
  }

  /**
   * Get a mass-specific entropy, or NaN when the phase is absent.
   *
   * @param phase phase or null
   * @return entropy in J/(kg K)
   */
  private static double specificEntropy(PhaseInterface phase) {
    return phase == null ? Double.NaN : phase.getEntropy() / phase.getNumberOfMolesInPhase() / phase.getMolarMass();
  }

  /**
   * Get the interfacial tension between two phases, or NaN when either is absent.
   *
   * @param first first phase or null
   * @param second second phase or null
   * @return surface tension in N/m
   */
  private double surfaceTension(PhaseInterface first, PhaseInterface second) {
    if (first == null || second == null) {
      return Double.NaN;
    }
    return thermoSystem.getInterphaseProperties().getSurfaceTension(phaseIndex(first), phaseIndex(second));
  }

  /**
   * Find the index of a phase in the system's active phase array.
   *
   * @param phase phase to locate
   * @return array index, or -1 when the phase is not active
   */
  private int phaseIndex(PhaseInterface phase) {
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      if (thermoSystem.getPhase(i) == phase) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Extrapolate every phase-specific column into the nodes where that phase does not exist.
   *
   * <p>
   * OLGA rejects a table containing a zero gas, oil or water density, so nodes outside a phase's existence region are
   * filled from the nearest node where it does exist, and a phase that exists nowhere falls back to a physical default.
   * The phase mass fractions pin at zero there, so the extrapolated branch is never used in a flow calculation. With
   * the monotonicity correction, density, enthalpy and entropy are first filled with the phase gradients so they stay
   * monotonic and continuous across the phase boundary.
   * </p>
   */
  private void fillAbsentPhaseNodes() {
    columnPresent = new boolean[nProps][][];
    for (int k = 0; k < nProps; k++) {
      boolean[][] mask = null;
      if (needsGas[k] || needsOil[k] || needsWater[k]) {
        mask = new boolean[pressures.length][temperatures.length];
        for (int i = 0; i < pressures.length; i++) {
          for (int j = 0; j < temperatures.length; j++) {
            mask[i][j] = (!needsGas[k] || gasPresent[i][j]) && (!needsOil[k] || oilPresent[i][j])
                && (!needsWater[k] || waterPresent[i][j]);
          }
        }
      }
      columnPresent[k] = mask;
      if (mask != null) {
        boolean[][] fillMask = monotonicityCorrection
            ? OlgaTableGridFiller.monotonicFill(namesKeyword[k], namesKeyword, props, mask, pressureLOG, temperatures)
            : mask;
        OlgaTableGridFiller.fillAbsentNodes(props[k], fillMask, defaultFor(namesKeyword[k]));
      }
    }
  }

  /**
   * Physically sensible value for a column whose phase exists nowhere on the grid.
   *
   * @param keyword OLGA column keyword
   * @return default value
   */
  private static double defaultFor(String keyword) {
    if ("ROG".equals(keyword)) {
      return DEFAULT_GAS_DENSITY;
    }
    if ("ROHL".equals(keyword)) {
      return DEFAULT_OIL_DENSITY;
    }
    if ("ROWT".equals(keyword)) {
      return DEFAULT_WATER_DENSITY;
    }
    if ("VISG".equals(keyword)) {
      return 1.0e-5;
    }
    if ("VISHL".equals(keyword)) {
      return 1.0e-3;
    }
    if ("VISWT".equals(keyword)) {
      return 1.0e-3;
    }
    if ("CPG".equals(keyword) || "CPHL".equals(keyword)) {
      return 2000.0;
    }
    if ("CPWT".equals(keyword)) {
      return 4200.0;
    }
    if ("TCG".equals(keyword)) {
      return 0.03;
    }
    if ("TCHL".equals(keyword)) {
      return 0.13;
    }
    if ("TCWT".equals(keyword)) {
      return 0.6;
    }
    if ("SIGGHL".equals(keyword) || "SIGGWT".equals(keyword) || "SIGHLWT".equals(keyword)) {
      return 0.02;
    }
    if (keyword != null && keyword.endsWith("DP")) {
      return 1.0e-6;
    }
    return 0.0;
  }

  /**
   * Constructor for OLGApropertyTableGeneratorWaterKeywordFormat.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public OLGApropertyTableGeneratorWaterKeywordFormat(SystemInterface system) {
    this.sourceSystem = system;
    this.thermoSystem = system;
    thermoOps = new ThermodynamicOperations(thermoSystem);
  }

  /**
   * Set an equidistant pressure axis.
   *
   * @param minPressure lowest pressure [bara]
   * @param maxPressure highest pressure [bara]
   * @param numberOfSteps number of pressure points
   */
  public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
    pressureGrid.setRange(minPressure, maxPressure, numberOfSteps);
    setPressureAxis(OlgaTableGrid.uniform(minPressure, maxPressure, numberOfSteps));
  }

  /**
   * Set an equidistant temperature axis.
   *
   * @param minTemperature lowest temperature [K]
   * @param maxTemperature highest temperature [K]
   * @param numberOfSteps number of temperature points
   */
  public void setTemperatureRange(double minTemperature, double maxTemperature, int numberOfSteps) {
    temperatureGrid.setRange(minTemperature, maxTemperature, numberOfSteps);
    setTemperatureAxis(OlgaTableGrid.uniform(minTemperature, maxTemperature, numberOfSteps));
  }

  /**
   * Set explicit table pressures, covering all combinations with the temperatures.
   *
   * @param pressuresBara pressures [bara], in any order
   * @throws IllegalArgumentException if fewer than two distinct finite values are given
   */
  public void setPressures(double[] pressuresBara) {
    setPressureAxis(pressureGrid.setValues(pressuresBara));
  }

  /**
   * Set explicit table temperatures, covering all combinations with the pressures.
   *
   * @param temperaturesKelvin temperatures [K], in any order
   * @throws IllegalArgumentException if fewer than two distinct finite values are given
   */
  public void setTemperatures(double[] temperaturesKelvin) {
    setTemperatureAxis(temperatureGrid.setValues(temperaturesKelvin));
  }

  /**
   * Store the pressure axis and its Pa values.
   *
   * @param axis pressures [bara]
   */
  private void setPressureAxis(double[] axis) {
    pressures = axis;
    pressureLOG = new double[axis.length];
    for (int i = 0; i < axis.length; i++) {
      pressureLOG[i] = axis[i] * 1e5;
    }
  }

  /**
   * Store the temperature axis and its Celsius values.
   *
   * @param axis temperatures [K]
   */
  private void setTemperatureAxis(double[] axis) {
    temperatures = axis;
    temperatureLOG = new double[axis.length];
    for (int i = 0; i < axis.length; i++) {
      temperatureLOG[i] = axis[i] - 273.15;
    }
  }

  /**
   * Concentrate range-axis points inside the water-free phase envelope.
   *
   * <p>
   * Along each axis whose envelope maximum (cricondenbar or cricondentherm) lies inside the range, a share
   * gridFactor/(gridFactor+1) of the points is placed inside the envelope. Explicit axes are not changed.
   * </p>
   *
   * @param enabled true to refine
   * @param factor ratio of points inside to outside the envelope, 1 to 10
   * @throws IllegalArgumentException if the factor is outside 1 to 10
   */
  public void setEnvelopeRefinement(boolean enabled, double factor) {
    if (!Double.isFinite(factor) || factor < 1.0 || factor > 10.0) {
      throw new IllegalArgumentException("Grid factor must be within 1-10, got " + factor);
    }
    this.envelopeRefinement = enabled;
    this.gridFactor = factor;
  }

  /**
   * Insert the standard pressure and temperature into the grid when they lie inside it. A range axis keeps its point
   * count; an explicit axis gains one point.
   *
   * @param insert true to insert
   */
  public void setInsertStandardConditions(boolean insert) {
    this.insertStandardConditions = insert;
  }

  /**
   * Set the standard conditions for GOR, GLR, water cut and standard densities.
   *
   * @param temperature standard temperature [K]
   * @param pressure standard pressure [bara]
   * @throws IllegalArgumentException if either value is not positive and finite
   */
  public void setStandardConditions(double temperature, double pressure) {
    if (!(temperature > 0.0) || !(pressure > 0.0) || Double.isInfinite(temperature) || Double.isInfinite(pressure)) {
      throw new IllegalArgumentException("Standard temperature and pressure must be positive");
    }
    this.stdTemp = temperature;
    this.stdPres = pressure;
    this.stdPresATM = pressure / ThermodynamicConstantsInterface.referencePressure;
  }

  /**
   * Fill absent-phase density, enthalpy and entropy with the phase gradients so they stay monotonic and continuous. On
   * by default; when off, absent nodes take the nearest existing value.
   *
   * @param correct true to correct
   */
  public void setMonotonicityCorrection(boolean correct) {
    this.monotonicityCorrection = correct;
  }

  /**
   * Take aqueous density, its derivatives, heat capacity, enthalpy, entropy, viscosity and thermal conductivity from
   * the IAPWS reference equations (IF97 Region 1, R12-08, R15-11) instead of the equation of state.
   *
   * <p>
   * Applied at nodes where the aqueous phase is at least 95 wt% water and inside 253.15-623.15 K and up to 1000 bara;
   * other nodes keep the equation-of-state values, so inhibitor-rich water is not treated as pure water. Enthalpy and
   * entropy are shifted to the equation-of-state reference at standard conditions, keeping latent heats consistent with
   * the gas phase. Interfacial tensions are not changed.
   * </p>
   *
   * @param use true to use the reference equations
   */
  public void setUseReferenceWaterProperties(boolean use) {
    this.referenceWaterProperties = use;
  }

  /**
   * Set the salinity of the aqueous phase. Density, its derivatives and viscosity are corrected with the Batzle and
   * Wang (1992) NaCl brine ratios; phase amounts and compositions are not affected. Not allowed for a feed that holds
   * ionic components.
   *
   * @param value salinity value
   * @param unit "mg/l" (of solution at standard conditions), "ppm" (mg/kg solution), "wt%" or "kg/kg"
   * @throws IllegalArgumentException if the unit is unknown or the value is out of range
   */
  public void setWaterSalinity(double value, String unit) {
    String normalized = unit == null ? "" : unit.trim().toLowerCase(java.util.Locale.ROOT);
    double fraction;
    if ("mg/l".equals(normalized)) {
      fraction = BrineSalinityCorrection.massFractionFromConcentration(value, stdTemp, stdPres);
    } else if ("ppm".equals(normalized)) {
      fraction = value * 1.0e-6;
    } else if ("wt%".equals(normalized)) {
      fraction = value / 100.0;
    } else if ("kg/kg".equals(normalized)) {
      fraction = value;
    } else {
      throw new IllegalArgumentException("Salinity unit must be mg/l, ppm, wt% or kg/kg, got " + unit);
    }
    // Evaluated only to reject an out-of-range mass fraction.
    BrineSalinityCorrection.densityRatio(stdTemp, stdPres, fraction);
    this.saltMassFraction = fraction;
  }

  /**
   * Get the salt mass fraction of the aqueous phase.
   *
   * @return salt mass fraction [kg/kg solution]
   */
  public double getWaterSaltMassFraction() {
    return saltMassFraction;
  }

  /**
   * Add produced water to a water-free feed before the table is generated. The feed passed to the constructor is not
   * modified; the table fluid is available from {@link #getTableFluid()} after {@link #run()}.
   *
   * @param value specification value
   * @param specification "watercut" (volume % of the standard liquid), "mol/mol" (mol water per mol feed), "mol%" or
   * "wt%" of feed plus water
   * @throws IllegalArgumentException if the specification or value is invalid
   */
  public void setProducedWater(double value, String specification) {
    this.producedWaterSpecification = OlgaProducedWater.validate(specification, value);
    this.producedWaterValue = value;
  }

  /**
   * Get the fluid the table was generated from, including any produced water.
   *
   * @return table fluid
   */
  public SystemInterface getTableFluid() {
    return thermoSystem;
  }

  /**
   * Get the number of aqueous nodes evaluated with the reference water equations in the last run.
   *
   * @return node count
   */
  public int getReferenceWaterNodeCount() {
    return referenceWaterNodes;
  }

  /**
   * Get the number of aqueous nodes that kept equation-of-state values because the water was impure or the state was
   * outside the reference range.
   *
   * @return node count
   */
  public int getReferenceWaterSkippedNodeCount() {
    return referenceWaterSkippedNodes;
  }

  /**
   * Build the quality report of the generated table.
   *
   * @return quality report
   * @throws IllegalStateException if {@link #run()} has not completed
   */
  public OlgaTableQualityReport getQualityReport() {
    if (props == null || columnPresent == null) {
      throw new IllegalStateException("Run the generator before requesting a quality report");
    }
    return new OlgaTableQualityReport(namesKeyword, props, pressures, temperatureLOG, columnPresent);
  }

  /**
   * Build the engineering report of the generated table in Markdown: fluid, grid, standard ratios, water-free envelope,
   * water models and quality checks.
   *
   * @return report text
   * @throws IllegalStateException if {@link #run()} has not completed
   */
  public String getTableReport() {
    OlgaTableQualityReport quality = getQualityReport();
    OlgaTableReportWriter report = new OlgaTableReportWriter();
    report.title("OLGA PVT table report: " + fluidLabel);
    report.paragraph("Three-phase keyword table (PHASE = THREE) generated with NeqSim. Phase properties are"
        + " calculated by a TP flash at every grid node. Nodes where a phase does not exist are filled "
        + (monotonicityCorrection ? "with the phase gradients (monotonicity correction)"
            : "from the nearest node where the phase exists")
        + "; OLGA does not use them where the phase mass fraction is zero.");
    report.section("Fluid");
    Map<String, String> fluid = new LinkedHashMap<String, String>();
    fluid.put("Fluid label", fluidLabel);
    fluid.put("Equation of state", thermoSystem.getModelName());
    fluid.put("Produced water", producedWaterSpecification == null ? "as in the feed"
        : OlgaTableReportWriter.number(producedWaterValue) + " " + producedWaterSpecification);
    fluid.put("Total water mass fraction (TOTWATERFRACTION)", OlgaTableReportWriter.number(RSWTOB));
    report.keyValues(fluid);
    report.composition(components, molfracs, MW);
    report.grid(pressures, pressureGrid.isDefined() ? pressureGrid.getLastMode() : "as set", temperatureLOG,
        temperatureGrid.isDefined() ? temperatureGrid.getLastMode() : "as set");
    report.section("Standard conditions and ratios");
    Map<String, String> standard = new LinkedHashMap<String, String>();
    standard.put("Standard temperature [K]", OlgaTableReportWriter.number(stdTemp));
    standard.put("Standard pressure [bara]", OlgaTableReportWriter.number(stdPres));
    standard.put("GOR [Sm3/Sm3]", OlgaTableReportWriter.number(GOR));
    standard.put("GLR [Sm3/Sm3]", OlgaTableReportWriter.number(GLR));
    standard.put("Water cut WC [-]", OlgaTableReportWriter.number(WC));
    standard.put("Standard gas density [kg/m3]", OlgaTableReportWriter.number(stdGasDens));
    standard.put("Standard oil density [kg/m3]", OlgaTableReportWriter.number(stdLiqDens));
    standard.put("Standard water density [kg/m3]", OlgaTableReportWriter.number(stdWatDens));
    report.keyValues(standard);
    report.envelope(envelope, envelopeRefinement, gridFactor);
    report.section("Water properties");
    Map<String, String> water = new LinkedHashMap<String, String>();
    water.put("Aqueous property model",
        referenceWaterProperties ? "IAPWS reference equations (IF97 Region 1, R12-08 viscosity, R15-11 conductivity)"
            : "equation of state and NeqSim physical-property models");
    if (referenceWaterProperties) {
      water.put("Nodes on reference equations", Integer.toString(referenceWaterNodes));
      water.put("Nodes kept on equation of state (water below 95 wt% or out of range)",
          Integer.toString(referenceWaterSkippedNodes));
    }
    water.put("Salt mass fraction [kg/kg]", OlgaTableReportWriter.number(saltMassFraction));
    water.put("Salinity correction",
        saltMassFraction > 0.0 ? "Batzle and Wang (1992) NaCl brine ratios on density, its derivatives and viscosity"
            : "none");
    report.keyValues(water);
    return report.quality(quality).toString();
  }

  /**
   * Write the engineering report of the generated table as Markdown.
   *
   * @param filename output file
   * @throws IOException if the file cannot be written
   * @throws IllegalStateException if {@link #run()} has not completed
   */
  public void writeTableReport(String filename) throws IOException {
    OlgaTableReportWriter writer = new OlgaTableReportWriter();
    writer.paragraph(getTableReport().trim());
    writer.write(filename);
  }

  /**
   * Calculate the critical point and envelope extents of the water-free feed.
   */
  public void calcPhaseEnvelope() {
    envelope = OlgaEnvelopeInfo.of(thermoSystem);
    double criticalTemperature = Double.isFinite(envelope.criticalTemperature) ? envelope.criticalTemperature
        : thermoSystem.getTC();
    double criticalPressure = Double.isFinite(envelope.criticalPressure) ? envelope.criticalPressure
        : thermoSystem.getPC();
    TCLOG = criticalTemperature;
    PCLOG = criticalPressure / ThermodynamicConstantsInterface.referencePressure;
    TC = criticalTemperature - 273.15;
    PC = criticalPressure * 1e5;
  }

  /**
   * Prepare the table fluid, build the grid and check the water options.
   */
  private void prepare() {
    if (producedWaterSpecification != null) {
      thermoSystem = OlgaProducedWater.apply(sourceSystem, producedWaterSpecification, producedWaterValue, stdTemp,
          stdPres, this::standardWaterDensity);
      thermoSystem.setMultiPhaseCheck(true);
      thermoOps = new ThermodynamicOperations(thermoSystem);
    } else if (thermoSystem != sourceSystem) {
      thermoSystem = sourceSystem;
      thermoOps = new ThermodynamicOperations(thermoSystem);
    }
    if (saltMassFraction > 0.0) {
      for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
        if (thermoSystem.getPhase(0).getComponent(i).getIonicCharge() != 0) {
          throw new IllegalArgumentException("Salinity cannot be combined with ionic components in the feed: "
              + thermoSystem.getPhase(0).getComponent(i).getComponentName());
        }
      }
    }
    if (pressures == null || temperatures == null) {
      throw new IllegalStateException("Set the pressure and temperature axes before running the generator");
    }
    calcPhaseEnvelope();
    if (pressureGrid.isDefined()) {
      setPressureAxis(pressureGrid.build(envelopeRefinement, gridFactor, envelope.minimumPressure,
          envelope.cricondenbarPressure, insertStandardConditions, stdPres));
    }
    if (temperatureGrid.isDefined()) {
      setTemperatureAxis(temperatureGrid.build(envelopeRefinement, gridFactor, envelope.minimumTemperature,
          envelope.cricondenthermTemperature, insertStandardConditions, stdTemp));
    }
  }

  /**
   * Calculate bubble pressures on independent copies. Failed or nonphysical pure-component saturation points are NaN in
   * the returned array and are never exported as zero pressures.
   *
   * @param temperatures an array of type double
   * @return an array of type double
   */
  public double[] calcBubP(double[] temperatures) {
    double[] bubP = new double[temperatures.length];
    bubPLOG = new double[temperatures.length];
    bubbleTemperaturesC = new double[temperatures.length];
    Arrays.fill(bubP, Double.NaN);
    Arrays.fill(bubPLOG, Double.NaN);
    Arrays.fill(bubbleTemperaturesC, Double.NaN);
    for (int i = 0; i < temperatures.length; i++) {
      SystemInterface saturationSystem = thermoSystem.clone();
      saturationSystem.setTemperature(temperatures[i]);
      try {
        new ThermodynamicOperations(saturationSystem).bubblePointPressureFlash(false);
        if (!isPhysicalBubblePoint(saturationSystem)) {
          continue;
        }
        bubP[i] = saturationSystem.getPressure();
        bubPLOG[i] = bubP[i] * 1e5;
        bubbleTemperaturesC[i] = saturationSystem.getTemperature() - 273.15;
      } catch (Exception ex) {
        logger.debug("Omitting bubble pressure at {} K: {}", temperatures[i], ex.getMessage());
      }
    }
    return bubP;
  }

  /**
   * Reject invalid pressures and pure-component single-root or spinodal solutions.
   *
   * @param system completed saturation flash
   * @return whether the point has physical saturation roots
   */
  private static boolean isPhysicalBubblePoint(SystemInterface system) {
    double pressure = system.getPressure();
    if (!Double.isFinite(pressure) || pressure <= 0.0) {
      return false;
    }
    if (system.getNumberOfComponents() != 1) {
      return true;
    }
    if (system.getTemperature() >= system.getPhase(0).getComponent(0).getTC()
        || pressure >= system.getPhase(0).getComponent(0).getPC() || system.getNumberOfPhases() < 2) {
      return false;
    }
    double firstVolume = system.getPhase(0).getMolarVolume();
    double secondVolume = system.getPhase(1).getMolarVolume();
    if (!Double.isFinite(firstVolume) || !Double.isFinite(secondVolume) || firstVolume <= 0.0 || secondVolume <= 0.0
        || Math.abs(firstVolume - secondVolume) <= 1.0e-6 * Math.max(firstVolume, secondVolume)) {
      return false;
    }
    for (int phase = 0; phase < 2; phase++) {
      if (system.getPhase(phase) instanceof PhaseEosInterface) {
        double eosPressure = ((PhaseEosInterface) system.getPhase(phase)).calcPressure();
        if (!Double.isFinite(eosPressure) || Math.abs(eosPressure - pressure) > 1.0e-6 * pressure) {
          return false;
        }
      }
    }
    double logFugacityRatio = Math.log(system.getPhase(0).getComponent(0).getFugacityCoefficient())
        - Math.log(system.getPhase(1).getComponent(0).getFugacityCoefficient());
    return Double.isFinite(logFugacityRatio) && Math.abs(logFugacityRatio) < 1.0e-6;
  }

  /**
   * Return the physical volume used for standard ratios, including density volume corrections.
   *
   * @param phase initialized phase, or null when absent
   * @return volume in cubic metres
   */
  private static double standardVolume(PhaseInterface phase) {
    return phase == null ? 0.0 : phase.getMass() / phase.getPhysicalProperties().getDensity();
  }

  /**
   * calcDewP.
   *
   * @param temperatures an array of type double
   * @return an array of type double
   */
  public double[] calcDewP(double[] temperatures) {
    double[] dewP = new double[temperatures.length];
    dewPLOG = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      thermoSystem.setTemperature(temperatures[i]);
      try {
        thermoOps.dewPointPressureFlash();
        dewP[i] = thermoSystem.getPressure();
        dewPLOG[i] = dewP[i] * 1e5;
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        dewP[i] = 0;
      }
    }
    return dewP;
  }

  /**
   * calcBubT.
   *
   * @param pressures an array of type double
   * @return an array of type double
   */
  public double[] calcBubT(double[] pressures) {
    double[] bubT = new double[pressures.length];
    bubTLOG = new double[pressures.length];
    for (int i = 0; i < pressures.length; i++) {
      thermoSystem.setPressure(pressures[i]);
      try {
        thermoOps.bubblePointTemperatureFlash();
        bubT[i] = thermoSystem.getTemperature();
        bubTLOG[i] = bubT[i] - 273.15;
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        bubT[i] = 0.0;
      }
    }
    return bubT;
  }

  /**
   * initCalc.
   */
  public void initCalc() {
    molfracs = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    MW = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    dens = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    components = new String[thermoSystem.getPhase(0).getNumberOfComponents()];

    for (int i = 0; i < molfracs.length; i++) {
      molfracs[i] = thermoSystem.getPhase(0).getComponent(i).getz();
      components[i] = thermoSystem.getPhase(0).getComponent(i).getComponentName();
      MW[i] = thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000;
      dens[i] = thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity();
    }

    thermoSystem.setTemperature(stdTemp);
    thermoSystem.setPressure(stdPres);

    thermoOps.TPflash();
    thermoSystem.initPhysicalProperties();

    PhaseInterface stdGas = thermoSystem.hasPhaseType("gas") ? thermoSystem.getPhaseOfType("gas") : null;
    PhaseInterface stdOil = thermoSystem.hasPhaseType("oil") ? thermoSystem.getPhaseOfType("oil") : null;
    PhaseInterface stdWater = thermoSystem.hasPhaseType("aqueous") ? thermoSystem.getPhaseOfType("aqueous") : null;
    stdWatDens = stdWater != null ? standardWaterDensity(stdWater)
        : representativeValue(props[2], DEFAULT_WATER_DENSITY);
    double gasVolume = standardVolume(stdGas);
    double oilVolume = standardVolume(stdOil);
    double waterVolume = stdWater == null ? 0.0 : stdWater.getMass() / stdWatDens;
    double liquidVolume = oilVolume + waterVolume;

    GOR = oilVolume > 0.0 ? gasVolume / oilVolume : NO_STANDARD_LIQUID;
    GLR = liquidVolume > 0.0 ? gasVolume / liquidVolume : NO_STANDARD_LIQUID;
    WC = liquidVolume > 0.0 ? waterVolume / liquidVolume : 0.0;

    stdGasDens = stdGas != null ? stdGas.getPhysicalProperties().getDensity()
        : representativeValue(props[0], DEFAULT_GAS_DENSITY);
    stdLiqDens = stdOil != null ? stdOil.getPhysicalProperties().getDensity()
        : representativeValue(props[1], DEFAULT_OIL_DENSITY);
  }

  /**
   * Standard-condition aqueous density with the same water models as the table.
   *
   * @param water aqueous phase flashed at standard conditions
   * @return density [kg/m3]
   */
  private double standardWaterDensity(PhaseInterface water) {
    double density = water.getPhysicalProperties().getDensity();
    if (referenceWaterProperties && LiquidWaterReference.isInRange(stdTemp, stdPres)
        && purity(water) >= REFERENCE_WATER_MIN_PURITY) {
      density = LiquidWaterReference.at(stdTemp, stdPres).getDensity();
    }
    if (saltMassFraction > 0.0) {
      density *= BrineSalinityCorrection.densityRatio(stdTemp, stdPres, saltMassFraction);
    }
    return density;
  }

  /**
   * Water mass fraction of an aqueous phase.
   *
   * @param water aqueous phase
   * @return water mass fraction, zero without a water component
   */
  private double purity(PhaseInterface water) {
    if (!water.hasComponent("water")) {
      return 0.0;
    }
    return water.getComponent("water").getx() * water.getComponent("water").getMolarMass() / water.getMolarMass();
  }

  /**
   * Find a column by keyword.
   *
   * @param keyword OLGA column keyword
   * @return column index
   */
  private int columnIndex(String keyword) {
    for (int k = 0; k < nProps; k++) {
      if (keyword.equals(namesKeyword[k])) {
        return k;
      }
    }
    throw new IllegalStateException("Missing table column " + keyword);
  }

  /**
   * Apply the reference water equations and the salinity correction at aqueous nodes.
   */
  private void applyWaterModels() {
    referenceWaterNodes = 0;
    referenceWaterSkippedNodes = 0;
    int rho = columnIndex("ROWT");
    int rhoP = columnIndex("DROWTDP");
    int rhoT = columnIndex("DROWTDT");
    int viscosity = columnIndex("VISWT");
    if (referenceWaterProperties) {
      int cp = columnIndex("CPWT");
      int enthalpy = columnIndex("HWT");
      int entropy = columnIndex("SEWT");
      int conductivity = columnIndex("TCWT");
      double[] offsets = referenceWaterOffsets();
      for (int i = 0; i < pressures.length; i++) {
        for (int j = 0; j < temperatures.length; j++) {
          if (!waterPresent[i][j]) {
            continue;
          }
          if (waterPurity[i][j] < REFERENCE_WATER_MIN_PURITY
              || !LiquidWaterReference.isInRange(temperatures[j], pressures[i])) {
            referenceWaterSkippedNodes++;
            continue;
          }
          LiquidWaterReference water = LiquidWaterReference.at(temperatures[j], pressures[i]);
          if (offsets == null) {
            offsets = new double[] {props[enthalpy][i][j] - water.getEnthalpy(),
                props[entropy][i][j] - water.getEntropy()};
          }
          props[rho][i][j] = water.getDensity();
          props[rhoP][i][j] = water.getDensityPressureDerivative();
          props[rhoT][i][j] = water.getDensityTemperatureDerivative();
          props[viscosity][i][j] = water.getViscosity();
          props[cp][i][j] = water.getHeatCapacityCp();
          props[enthalpy][i][j] = water.getEnthalpy() + offsets[0];
          props[entropy][i][j] = water.getEntropy() + offsets[1];
          props[conductivity][i][j] = water.getThermalConductivity();
          referenceWaterNodes++;
        }
      }
    }
    if (saltMassFraction > 0.0) {
      for (int i = 0; i < pressures.length; i++) {
        for (int j = 0; j < temperatures.length; j++) {
          if (!waterPresent[i][j]) {
            continue;
          }
          double fresh = props[rho][i][j];
          double ratio = BrineSalinityCorrection.densityRatio(temperatures[j], pressures[i], saltMassFraction);
          props[rho][i][j] = fresh * ratio;
          props[rhoP][i][j] = props[rhoP][i][j] * ratio + fresh
              * BrineSalinityCorrection.densityRatioPressureDerivative(temperatures[j], pressures[i], saltMassFraction);
          props[rhoT][i][j] = props[rhoT][i][j] * ratio + fresh * BrineSalinityCorrection
              .densityRatioTemperatureDerivative(temperatures[j], pressures[i], saltMassFraction);
          props[viscosity][i][j] *= BrineSalinityCorrection.viscosityRatio(temperatures[j], saltMassFraction);
        }
      }
    }
  }

  /**
   * Shift that moves reference-water enthalpy and entropy onto the equation-of-state reference, anchored on the aqueous
   * phase at standard conditions.
   *
   * @return enthalpy [J/kg] and entropy [J/(kg K)] offsets, or null when standard conditions give no usable water
   */
  private double[] referenceWaterOffsets() {
    if (!LiquidWaterReference.isInRange(stdTemp, stdPres)) {
      return null;
    }
    try {
      SystemInterface standard = thermoSystem.clone();
      standard.setTemperature(stdTemp);
      standard.setPressure(stdPres);
      new ThermodynamicOperations(standard).TPflash();
      standard.init(3);
      if (!standard.hasPhaseType("aqueous")) {
        return null;
      }
      PhaseInterface water = standard.getPhaseOfType("aqueous");
      if (purity(water) < REFERENCE_WATER_MIN_PURITY) {
        return null;
      }
      LiquidWaterReference reference = LiquidWaterReference.at(stdTemp, stdPres);
      return new double[] {specificEnthalpy(water) - reference.getEnthalpy(),
          specificEntropy(water) - reference.getEntropy()};
    } catch (Exception ex) {
      logger.debug("Standard-condition water anchor failed: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * Pick a finite positive representative value from a filled property grid.
   *
   * @param grid property values indexed [pressure][temperature]
   * @param fallback value returned when the grid holds nothing usable
   * @return representative value
   */
  private static double representativeValue(double[][] grid, double fallback) {
    if (grid == null) {
      return fallback;
    }
    for (int i = 0; i < grid.length; i++) {
      for (int j = 0; j < grid[i].length; j++) {
        double value = grid[i][j];
        if (!Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0) {
          return value;
        }
      }
    }
    return fallback;
  }

  /**
   * calcRSWTOB.
   */
  public void calcRSWTOB() {
    thermoSystem.init(0);
    thermoSystem.init(1);

    // The three-phase table has water columns, but the generator must not throw when it is
    // handed a dry fluid: RSWTOB is simply zero then.
    if (!thermoSystem.hasComponent("water")) {
      RSWTOB = 0.0;
      return;
    }
    RSWTOB = thermoSystem.getPhase(0).getComponent("water").getNumberOfmoles()
        * thermoSystem.getPhase(0).getComponent("water").getMolarMass()
        / (thermoSystem.getTotalNumberOfMoles() * thermoSystem.getMolarMass());
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    logger.info("Start creating arrays");
    prepare();
    calcRSWTOB();
    nProps = 29;
    props = new double[nProps][pressures.length][temperatures.length];
    units = new String[nProps];
    names = new String[nProps];
    namesKeyword = new String[nProps];
    needsGas = new boolean[nProps];
    needsOil = new boolean[nProps];
    needsWater = new boolean[nProps];
    gasPresent = new boolean[pressures.length][temperatures.length];
    oilPresent = new boolean[pressures.length][temperatures.length];
    waterPresent = new boolean[pressures.length][temperatures.length];
    waterPurity = new double[pressures.length][temperatures.length];
    /*
     * ROG = new double[pressures.length][temperatures.length]; ROL = new double[pressures.length][temperatures.length];
     * CPG = new double[pressures.length][temperatures.length]; CPHL = new
     * double[pressures.length][temperatures.length]; HG = new double[pressures.length][temperatures.length]; HHL = new
     * double[pressures.length][temperatures.length]; VISG = new double[pressures.length][temperatures.length]; VISHL =
     * new double[pressures.length][temperatures.length]; TCG = new double[pressures.length][temperatures.length]; TCHL
     * = new double[pressures.length][temperatures.length]; // SIGGHL = new
     * double[pressures.length][temperatures.length]; SEG = new double[pressures.length][temperatures.length]; SEHL =
     * new double[pressures.length][temperatures.length]; RS = new double[pressures.length][temperatures.length]; //
     * DROGDP = new double[pressures.length][temperatures.length]; // DROHLDP = new
     * double[pressures.length][temperatures.length]; // DROGDT = new double[pressures.length][temperatures.length]; //
     * DROHLDT = new double[pressures.length][temperatures.length];
     */

    for (int i = 0; i < pressures.length; i++) {
      thermoSystem.setPressure(pressures[i]);
      for (int j = 0; j < temperatures.length; j++) {
        thermoSystem.setTemperature(temperatures[j]);
        try {
          thermoOps.TPflash();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        thermoSystem.init(3);
        thermoSystem.initPhysicalProperties();
        /*
         * ROG[i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getDensity(); ROL[i][j] =
         * thermoSystem.getPhase(1).getPhysicalProperties().getDensity(); DROGDP[i][j] =
         * thermoSystem.getPhase(0).getdrhodP(); // DROHLDP[i][j] = thermoSystem.getPhase(1).getdrhodP(); //
         * DROGDT[i][j] = thermoSystem.getPhase(0).getdrhodT(); // DROHLDT[i][j] = thermoSystem.getPhase(1).getdrhodT();
         * CPG[i][j] = thermoSystem.getPhase(0).getCp(); CPHL[i][j] = thermoSystem.getPhase(1).getCp(); HG[i][j] =
         * thermoSystem.getPhase(0).getEnthalpy(); HHL[i][j] = thermoSystem.getPhase(1).getEnthalpy(); TCG[i][j] =
         * thermoSystem.getPhase(0).getPhysicalProperties().getConductivity(); TCHL[i][j] =
         * thermoSystem.getPhase(1).getPhysicalProperties().getConductivity(); VISG[i][j] =
         * thermoSystem.getPhase(0).getPhysicalProperties().getViscosity(); VISHL[i][j] =
         * thermoSystem.getPhase(1).getPhysicalProperties().getViscosity(); // SIGGHL[i][j] =
         * thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1); SEG[i][j] =
         * thermoSystem.getPhase(0).getEntropy(); SEHL[i][j] = thermoSystem.getPhase(1).getEntropy(); RS[i][j] =
         * thermoSystem.getPhase(0).getBeta();
         */

        // Resolve phases by TYPE, not by array position: a flash only returns the phases that
        // exist, so getPhase(1) is the water phase on a gas/water node and getPhase(2) does not
        // exist at all. Writing a zero density for an absent phase makes OLGA reject the table.
        PhaseInterface gas = thermoSystem.hasPhaseType("gas") ? thermoSystem.getPhaseOfType("gas") : null;
        PhaseInterface oil = thermoSystem.hasPhaseType("oil") ? thermoSystem.getPhaseOfType("oil") : null;
        PhaseInterface water = thermoSystem.hasPhaseType("aqueous") ? thermoSystem.getPhaseOfType("aqueous") : null;
        gasPresent[i][j] = gas != null;
        oilPresent[i][j] = oil != null;
        waterPresent[i][j] = water != null;
        waterPurity[i][j] = (water == null || !thermoSystem.hasComponent("water")) ? 0.0
            : water.getComponent("water").getx() * water.getComponent("water").getMolarMass() / water.getMolarMass();

        int k = 0;
        props[k][i][j] = density(gas);
        names[k] = "GAS DENSITY";
        units[k] = "KG/M3";
        namesKeyword[k] = "ROG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = density(oil);
        names[k] = "LIQUID DENSITY";
        units[k] = "KG/M3";
        namesKeyword[k] = "ROHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = density(water);
        names[k] = "WATER DENSITY";
        units[k] = "KG/M3";
        namesKeyword[k] = "ROWT";
        needsWater[k] = true;
        k++;
        props[k][i][j] = gas == null ? Double.NaN : gas.getdrhodP() / 1.0e5;
        names[k] = "DRHOG/DP";
        units[k] = "S2/M2";
        namesKeyword[k] = "DROGDP";
        needsGas[k] = true;
        k++;
        props[k][i][j] = oil == null ? Double.NaN : oil.getdrhodP() / 1.0e5;
        names[k] = "DRHOL/DP";
        units[k] = "S2/M2";
        namesKeyword[k] = "DROHLDP";
        needsOil[k] = true;
        k++;
        props[k][i][j] = water == null ? Double.NaN : water.getdrhodP() / 1.0e5;
        names[k] = "DRHOWAT/DP";
        units[k] = "S2/M2";
        namesKeyword[k] = "DROWTDP";
        needsWater[k] = true;
        k++;
        props[k][i][j] = gas == null ? Double.NaN : gas.getdrhodT();
        names[k] = "DRHOG/DT";
        units[k] = "KG/M3-K";
        namesKeyword[k] = "DROGDT";
        needsGas[k] = true;
        k++;
        props[k][i][j] = oil == null ? Double.NaN : oil.getdrhodT();
        names[k] = "DRHOL/DT";
        units[k] = "KG/M3-K";
        namesKeyword[k] = "DROHLDT";
        needsOil[k] = true;
        k++;
        props[k][i][j] = water == null ? Double.NaN : water.getdrhodT();
        names[k] = "DRHOWAT/DT";
        units[k] = "KG/M3-K";
        namesKeyword[k] = "DROWTDT";
        needsWater[k] = true;
        k++;
        // RS excludes the separate aqueous phase. Fractions are never extrapolated.
        double gasMass = gas == null ? 0.0 : gas.getMass();
        double oilMass = oil == null ? 0.0 : oil.getMass();
        props[k][i][j] = gasMass + oilMass > 0.0 ? gasMass / (gasMass + oilMass) : 0.0;
        names[k] = "GAS MASS FRACTION";
        units[k] = "-";
        namesKeyword[k] = "RS";
        k++;
        props[k][i][j] = (gas == null || !thermoSystem.hasComponent("water")) ? 0.0
            : gas.getComponent("water").getx() * gas.getComponent("water").getMolarMass() / gas.getMolarMass();
        names[k] = "WATER VAPOR MASS FRACTION";
        units[k] = "-";
        namesKeyword[k] = "RSW";
        k++;
        props[k][i][j] = viscosity(gas);
        names[k] = "GAS VISCOSITY";
        units[k] = "NS/M2";
        namesKeyword[k] = "VISG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = viscosity(oil);
        names[k] = "LIQUID VISCOSITY";
        units[k] = "NS/M2";
        namesKeyword[k] = "VISHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = viscosity(water);
        names[k] = "WATER VISCOSITY";
        units[k] = "NS/M2";
        namesKeyword[k] = "VISWT";
        needsWater[k] = true;
        k++;
        props[k][i][j] = specificHeatCapacity(gas);
        names[k] = "GAS HEAT CAPACITY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "CPG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificHeatCapacity(oil);
        names[k] = "LIQUID HEAT CAPACITY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "CPHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = specificHeatCapacity(water);
        names[k] = "WATER HEAT CAPACITY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "CPWT";
        needsWater[k] = true;
        k++;
        props[k][i][j] = specificEnthalpy(gas);
        names[k] = "GAS ENTHALPY";
        units[k] = "J/KG";
        namesKeyword[k] = "HG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificEnthalpy(oil);
        names[k] = "LIQUID ENTHALPY";
        units[k] = "J/KG";
        namesKeyword[k] = "HHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = specificEnthalpy(water);
        names[k] = "WATER ENTHALPY";
        units[k] = "J/KG";
        namesKeyword[k] = "HWT";
        needsWater[k] = true;
        k++; // fra neqsim er entalpi per mol
        props[k][i][j] = conductivity(gas);
        names[k] = "GAS THERMAL CONDUCTIVITY";
        units[k] = "W/M-K";
        namesKeyword[k] = "TCG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = conductivity(oil);
        names[k] = "LIQUID THERMAL CONDUCTIVITY";
        units[k] = "W/M-K";
        namesKeyword[k] = "TCHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = conductivity(water);
        names[k] = "WATER THERMAL CONDUCTIVITY";
        units[k] = "W/M-K";
        namesKeyword[k] = "TCWT";
        needsWater[k] = true;
        k++;
        props[k][i][j] = surfaceTension(gas, oil);
        names[k] = "VAPOR-LIQUID SURFACE TENSION";
        units[k] = "N/M";
        namesKeyword[k] = "SIGGHL";
        needsGas[k] = true;
        needsOil[k] = true;
        k++;
        props[k][i][j] = surfaceTension(gas, water);
        names[k] = "VAPOR-WATER SURFACE TENSION";
        units[k] = "N/M";
        namesKeyword[k] = "SIGGWT";
        needsGas[k] = true;
        needsWater[k] = true;
        k++;
        props[k][i][j] = surfaceTension(oil, water);
        names[k] = "LIQUID-WATER SURFACE TENSION";
        units[k] = "N/M";
        namesKeyword[k] = "SIGHLWT";
        needsOil[k] = true;
        needsWater[k] = true;
        k++;
        props[k][i][j] = specificEntropy(gas);
        names[k] = "GAS ENTROPY";
        units[k] = "J/KG/K";
        namesKeyword[k] = "SEG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificEntropy(oil);
        names[k] = "LIQUID ENTROPY";
        units[k] = "J/KG/K";
        namesKeyword[k] = "SEHL";
        needsOil[k] = true;
        k++;
        props[k][i][j] = specificEntropy(water);
        names[k] = "WATER ENTROPY";
        units[k] = "J/KG/K";
        namesKeyword[k] = "SEWT";
        needsWater[k] = true;
        k++;
      }
    }
    applyWaterModels();
    fillAbsentPhaseNodes();
    // A wet multicomponent feed has no unambiguous bulk liquid bubble branch. These
    // headers are optional for mixtures; avoid both unreliable roots and unnecessary flashes.
    bubP = thermoSystem.getNumberOfComponents() == 1 ? calcBubP(temperatures) : new double[0];
    if (thermoSystem.getNumberOfComponents() != 1) {
      bubPLOG = new double[0];
      bubbleTemperaturesC = new double[0];
    }
    logger.info("Finished creating arrays");
    initCalc();
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    logger.info("TC " + TC + " PC " + PC);
    for (int i = 0; i < pressures.length; i++) {
      thermoSystem.setPressure(pressures[i]);
      for (int j = 0; j < temperatures.length; j++) {
        logger.info("pressure " + pressureLOG[i] + " temperature " + temperatureLOG[j]);
        // + " ROG " + ROG[i][j] + " ROL " + ROL[i][j]);
      }
    }
    writeOLGAinpFile("test.tab");
  }

  /**
   * Check whether a bubble curve index holds a complete finite pressure/temperature pair.
   *
   * @param index curve index
   * @return whether both values can be exported
   */
  private boolean hasBubblePoint(int index) {
    return bubPLOG != null && bubbleTemperaturesC != null && index < bubPLOG.length
        && index < bubbleTemperaturesC.length && Double.isFinite(bubPLOG[index]) && bubPLOG[index] > 0.0
        && Double.isFinite(bubbleTemperaturesC[index]) && bubbleTemperaturesC[index] > -273.15;
  }

  /**
   * Writes an OLGA .inp file for PVT table input.
   *
   * @param filename a {@link java.lang.String} object
   * @throws IllegalStateException if a pure component has no validated saturation points
   */
  public void writeOLGAinpFile(String filename) {
    boolean pureComponent = thermoSystem.getNumberOfComponents() == 1;
    int bubblePointCount = 0;
    if (pureComponent && bubPLOG != null) {
      for (int i = 0; i < bubPLOG.length; i++) {
        if (hasBubblePoint(i)) {
          bubblePointCount++;
        }
      }
    }
    if (pureComponent && bubblePointCount == 0) {
      throw new IllegalStateException("Pure-component OLGA tables require a valid saturation curve; "
          + "include subcritical temperatures in the grid.");
    }
    try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf-8"))) {
      writer.write("PVTTABLE LABEL = \"" + fluidLabel + "\", PHASE = THREE,\\\n");
      writer.write("EOS = \"" + thermoSystem.getModelName() + "\",\\\n");

      writer.write("COMPONENTS = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write("\"" + components[i] + "\"");
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write("),\\\n");

      writer.write("MOLES = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(Double.toString(molfracs[i]));
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write("),\\\n");

      writer.write("MOLWEIGHT = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(Double.toString(MW[i]));
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") g/mol,\\\n");

      writer.write("DENSITY = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(Double.toString(dens[i]));
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") g/cm3,\\\n");

      writer.write("STDPRESSURE = " + stdPresATM + " ATM,\\\n");
      writer.write("STDTEMPERATURE = " + stdTemp + " K,\\\n");
      writer.write("GOR = " + GOR + " Sm3/Sm3,\\\n");
      writer.write("GLR = " + GLR + " Sm3/Sm3,\\\n");
      writer.write("WC = " + WC + ",\\\n");
      writer.write("STDGASDENSITY = " + stdGasDens + " kg/m3,\\\n");
      writer.write("STDOILDENSITY = " + stdLiqDens + " kg/m3,\\\n");
      writer.write("STDWATDENSITY = " + stdWatDens + " kg/m3,\\\n");
      writer.write("CRITICALPRESSURE = " + PCLOG + " ATM,\\\n");
      writer.write("CRITICALTEMPERATURE = " + TCLOG + " K,\\\n");
      writer.write("MESHTYPE = STANDARD, TOTWATERFRACTION = (" + RSWTOB + "),\\\n");

      writer.write("PRESSURE = (");
      for (int i = 0; i < pressures.length; i++) {
        writer.write(Double.toString(pressureLOG[i]));
        if (i < pressures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") Pa,\\\n");

      writer.write("TEMPERATURE = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(Double.toString(temperatureLOG[i]));
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") C,\\\n");

      if (bubblePointCount > 0) {
        // Both arrays use exactly the same retained saturation points, which need not
        // coincide with property grid temperatures when calcBubP is called directly.
        writer.write("BUBBLEPRESSURES = (");
        int written = 0;
        for (int i = 0; i < bubPLOG.length; i++) {
          if (hasBubblePoint(i)) {
            if (written++ > 0) {
              writer.write(",");
            }
            writer.write(Double.toString(bubPLOG[i]));
          }
        }
        writer.write(") Pa,\\\n");

        writer.write("BUBBLETEMPERATURES = (");
        written = 0;
        for (int i = 0; i < bubPLOG.length; i++) {
          if (hasBubblePoint(i)) {
            if (written++ > 0) {
              writer.write(",");
            }
            writer.write(Double.toString(bubbleTemperaturesC[i]));
          }
        }
        writer.write(") C,\\\n");
      }

      writer.write("COLUMNS = (PT,TM,");
      for (int k = 0; k < nProps; k++) {
        writer.write(namesKeyword[k]);
        if (k < nProps - 1) {
          writer.write(",");
        }
      }
      writer.write(")\n");

      for (int i = 0; i < pressures.length; i++) {
        thermoSystem.setPressure(pressures[i]);
        for (int j = 0; j < temperatures.length; j++) {
          thermoSystem.setTemperature(temperatures[j]);
          writer.write("PVTTABLE POINT = (");
          writer.write(pressureLOG[i] + "," + temperatureLOG[j]);
          for (int k = 0; k < nProps; k++) {
            writer.write("," + props[k][i][j]);
          }
          writer.write(")\n");
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }
}

package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * OLGApropertyTableGeneratorKeywordFormat class.
 *
 * @author Kjetil Raul
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorKeywordFormat extends neqsim.thermodynamicoperations.BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorKeywordFormat.class);

  /** GOR written when no liquid forms at standard conditions, so the key stays finite. */
  private static final double SINGLE_PHASE_GOR = 1.0e6;
  /** Gas density written when the grid holds no gas at all, in kg/m3. */
  private static final double DEFAULT_GAS_DENSITY = 1.0;
  /** Liquid density written when the grid holds no liquid at all, in kg/m3. */
  private static final double DEFAULT_LIQUID_DENSITY = 1000.0;
  /** Gas column keywords, used when the gas branch has to be extrapolated. */
  private static final String[] GAS_KEYWORDS = { "ROG", "DROGDP", "DROGDT", "VISG", "CPG", "HG", "TCG", "SEG" };
  /** Liquid column keywords, used when the liquid branch has to be extrapolated. */
  private static final String[] LIQUID_KEYWORDS = { "ROHL", "DROHLDP", "DROHLDT", "VISHL", "CPHL", "HHL", "TCHL",
      "SEHL" };

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
  double stdGasDens;
  double stdLiqDens;
  double[] pressures;
  double[] temperatureLOG;
  double[] temperatures;
  double[] pressureLOG = null;
  double[][] ROG = null; // DROGDP, DROHLDP, DROGDT, DROHLDT;
  double[] bubP;
  double[] bubT;
  double[] dewP;
  double[] bubPLOG;
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
  double[][][] props;
  int nProps;
  String[] names;
  String[] units;
  String[] namesKeyword;

  /** True at grid nodes where a gas phase exists. */
  private boolean[][] gasPresent;
  /** True at grid nodes where a hydrocarbon or aqueous liquid phase exists. */
  private boolean[][] liquidPresent;
  /** True for property columns that require a gas phase. */
  private boolean[] needsGas;
  /** True for property columns that require a liquid phase. */
  private boolean[] needsLiquid;
  /** Fluid label written to the table and referenced by the OLGA BRANCH FLUID key. */
  private String fluidLabel = "NewFluid";

  /**
   * Set the fluid label written to the table.
   *
   * <p>
   * The OLGA case refers to this exact string in {@code BRANCH FLUID="..."}, so it has to match.
   * </p>
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
   * Resolve the liquid phase used for the OLGA liquid columns.
   *
   * <p>
   * A hydrocarbon liquid is preferred. A water-only fluid has an aqueous phase and no oil phase, and its liquid columns
   * must still be populated, so the aqueous phase is used when there is no oil.
   * </p>
   *
   * @return liquid phase, or null when the node is all gas
   */
  private PhaseInterface resolveLiquidPhase() {
    if (thermoSystem.hasPhaseType("oil")) {
      return thermoSystem.getPhaseOfType("oil");
    }
    if (thermoSystem.hasPhaseType("aqueous")) {
      return thermoSystem.getPhaseOfType("aqueous");
    }
    return null;
  }

  /**
   * Find the index of a phase in the system's active phase array.
   *
   * <p>
   * The interphase property model is addressed by array position, which is not the same as the phase type once the
   * phases are resolved by type rather than by index.
   * </p>
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
   * Extrapolate every phase-specific column into the nodes where that phase does not exist.
   *
   * <p>
   * OLGA rejects a table containing a zero gas or liquid density, so single-phase regions cannot be left empty. Nodes
   * inside the grid are filled from the nearest node where the phase exists. When a phase exists nowhere on the grid -
   * a dry gas has no liquid anywhere, a dead oil no gas - there is no neighbour to borrow from, so the column is taken
   * from a forced single-phase evaluation of the whole composition, and only if that fails from a physical default.
   * </p>
   */
  private void fillAbsentPhaseNodes() {
    boolean anyGas = anyTrue(gasPresent);
    boolean anyLiquid = anyTrue(liquidPresent);
    java.util.Map<String, double[][]> gasBranch = anyGas ? null : forcedPhaseBranch("gas");
    java.util.Map<String, double[][]> liquidBranch = anyLiquid ? null : forcedPhaseBranch("oil");

    for (int k = 0; k < nProps; k++) {
      if (needsGas[k] && needsLiquid[k]) {
        boolean[][] both = new boolean[pressures.length][temperatures.length];
        for (int i = 0; i < pressures.length; i++) {
          for (int j = 0; j < temperatures.length; j++) {
            both[i][j] = gasPresent[i][j] && liquidPresent[i][j];
          }
        }
        OlgaTableGridFiller.fillAbsentNodes(props[k], both, defaultFor(namesKeyword[k]));
      } else if (needsGas[k]) {
        applyBranch(k, gasBranch);
        OlgaTableGridFiller.fillAbsentNodes(props[k], anyGas ? gasPresent : allTrue(), defaultFor(namesKeyword[k]));
      } else if (needsLiquid[k]) {
        applyBranch(k, liquidBranch);
        OlgaTableGridFiller.fillAbsentNodes(props[k], anyLiquid ? liquidPresent : allTrue(),
            defaultFor(namesKeyword[k]));
      }
    }
  }

  /**
   * Overwrite a property column with a forced single-phase branch when one was computed.
   *
   * @param k property column index
   * @param branch forced-branch values by keyword, or null when the phase exists on the grid
   */
  private void applyBranch(int k, java.util.Map<String, double[][]> branch) {
    if (branch == null) {
      return;
    }
    double[][] values = branch.get(namesKeyword[k]);
    if (values == null) {
      return;
    }
    for (int i = 0; i < pressures.length; i++) {
      System.arraycopy(values[i], 0, props[k][i], 0, temperatures.length);
    }
  }

  /**
   * Evaluate the whole composition as a single forced phase over the grid.
   *
   * <p>
   * This is the metastable branch a table needs when the phase never flashes out on its own. If the equation of state
   * cannot produce a usable root the entry is left NaN and the caller's default applies.
   * </p>
   *
   * @param phaseTypeName "gas" or "oil"
   * @return property values by OLGA keyword, or null when the forced evaluation is unusable
   */
  private java.util.Map<String, double[][]> forcedPhaseBranch(String phaseTypeName) {
    java.util.Map<String, double[][]> branch = new java.util.HashMap<String, double[][]>();
    String[] keywords = "gas".equals(phaseTypeName) ? GAS_KEYWORDS : LIQUID_KEYWORDS;
    for (String keyword : keywords) {
      branch.put(keyword, new double[pressures.length][temperatures.length]);
    }

    SystemInterface forced;
    try {
      forced = thermoSystem.clone();
      forced.setForcePhaseTypes(true);
      forced.setNumberOfPhases(1);
      forced.setPhaseType(0, phaseTypeName);
    } catch (Exception ex) {
      logger.warn("Could not force a {} phase for the absent-phase branch: {}", phaseTypeName, ex.getMessage());
      return null;
    }

    boolean usable = false;
    for (int i = 0; i < pressures.length; i++) {
      for (int j = 0; j < temperatures.length; j++) {
        PhaseInterface phase = null;
        try {
          forced.setPressure(pressures[i]);
          forced.setTemperature(temperatures[j]);
          forced.init(3);
          forced.initPhysicalProperties();
          phase = forced.getPhase(0);
        } catch (Exception ex) {
          phase = null;
        }
        double rho = density(phase);
        boolean nodeOk = !Double.isNaN(rho) && !Double.isInfinite(rho) && rho > 0.0;
        usable = usable || nodeOk;
        for (String keyword : keywords) {
          branch.get(keyword)[i][j] = nodeOk ? phaseProperty(keyword, phase) : Double.NaN;
        }
      }
    }
    if (!usable) {
      logger.warn("Forced {} branch produced no usable root; falling back to default properties", phaseTypeName);
      return null;
    }
    return branch;
  }

  /**
   * Evaluate one OLGA property keyword on a phase.
   *
   * @param keyword OLGA column keyword
   * @param phase phase to evaluate, may be null
   * @return property value, NaN when unavailable
   */
  private static double phaseProperty(String keyword, PhaseInterface phase) {
    if (phase == null) {
      return Double.NaN;
    }
    if ("ROG".equals(keyword) || "ROHL".equals(keyword)) {
      return density(phase);
    }
    if ("DROGDP".equals(keyword) || "DROHLDP".equals(keyword)) {
      return phase.getdrhodP() / 1.0e5;
    }
    if ("DROGDT".equals(keyword) || "DROHLDT".equals(keyword)) {
      return phase.getdrhodT();
    }
    if ("VISG".equals(keyword) || "VISHL".equals(keyword)) {
      return viscosity(phase);
    }
    if ("CPG".equals(keyword) || "CPHL".equals(keyword)) {
      return specificHeatCapacity(phase);
    }
    if ("HG".equals(keyword) || "HHL".equals(keyword)) {
      return specificEnthalpy(phase);
    }
    if ("TCG".equals(keyword) || "TCHL".equals(keyword)) {
      return conductivity(phase);
    }
    if ("SEG".equals(keyword) || "SEHL".equals(keyword)) {
      return specificEntropy(phase);
    }
    return Double.NaN;
  }

  /**
   * Physically sensible value for a column whose phase exists nowhere and cannot be forced.
   *
   * <p>
   * OLGA only requires these to be finite and, for density, positive: the phase mass fraction pins at zero everywhere,
   * so the branch is never used in a flow calculation. Zero density is the one value OLGA refuses.
   * </p>
   *
   * @param keyword OLGA column keyword
   * @return default value
   */
  private static double defaultFor(String keyword) {
    if ("ROG".equals(keyword)) {
      return DEFAULT_GAS_DENSITY;
    }
    if ("ROHL".equals(keyword)) {
      return DEFAULT_LIQUID_DENSITY;
    }
    if ("VISG".equals(keyword)) {
      return 1.0e-5;
    }
    if ("VISHL".equals(keyword)) {
      return 1.0e-3;
    }
    if ("CPG".equals(keyword) || "CPHL".equals(keyword)) {
      return 2000.0;
    }
    if ("TCG".equals(keyword)) {
      return 0.03;
    }
    if ("TCHL".equals(keyword)) {
      return 0.13;
    }
    if ("SIGGHL".equals(keyword)) {
      return 0.02;
    }
    if ("DROGDP".equals(keyword) || "DROHLDP".equals(keyword)) {
      return 1.0e-6;
    }
    return 0.0;
  }

  /**
   * Check whether a presence mask has any true entry.
   *
   * @param mask presence mask
   * @return true when the phase exists somewhere
   */
  private static boolean anyTrue(boolean[][] mask) {
    for (int i = 0; i < mask.length; i++) {
      for (int j = 0; j < mask[i].length; j++) {
        if (mask[i][j]) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Build a mask that accepts every grid node.
   *
   * @return all-true mask sized to the grid
   */
  private boolean[][] allTrue() {
    boolean[][] mask = new boolean[pressures.length][temperatures.length];
    for (int i = 0; i < pressures.length; i++) {
      java.util.Arrays.fill(mask[i], true);
    }
    return mask;
  }

  /**
   * Constructor for OLGApropertyTableGeneratorKeywordFormat.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public OLGApropertyTableGeneratorKeywordFormat(SystemInterface system) {
    this.thermoSystem = system;
    thermoOps = new ThermodynamicOperations(thermoSystem);
  }

  /**
   * setPressureRange.
   *
   * @param minPressure a double
   * @param maxPressure a double
   * @param numberOfSteps a int
   */
  public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
    pressures = new double[numberOfSteps];
    pressureLOG = new double[numberOfSteps];
    double step = (maxPressure - minPressure) / (numberOfSteps * 1.0 - 1.0);
    for (int i = 0; i < numberOfSteps; i++) {
      pressures[i] = minPressure + i * step;
      pressureLOG[i] = pressures[i] * 1e5;
    }
  }

  /**
   * setTemperatureRange.
   *
   * @param minTemperature a double
   * @param maxTemperature a double
   * @param numberOfSteps a int
   */
  public void setTemperatureRange(double minTemperature, double maxTemperature, int numberOfSteps) {
    temperatures = new double[numberOfSteps];
    temperatureLOG = new double[numberOfSteps];
    double step = (maxTemperature - minTemperature) / (numberOfSteps * 1.0 - 1.0);
    for (int i = 0; i < numberOfSteps; i++) {
      temperatures[i] = minTemperature + i * step;
      temperatureLOG[i] = temperatures[i] - 273.15;
    }
  }

  /**
   * calcPhaseEnvelope.
   */
  public void calcPhaseEnvelope() {
    try {
      thermoOps.calcPTphaseEnvelope();
      TCLOG = thermoSystem.getTC();
      PCLOG = thermoSystem.getPC() * 0.986923267; // convert to ATM
      TC = thermoSystem.getTC() - 273.15;
      PC = thermoSystem.getPC() * 1e5;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // thermoOps.ge
  }

  /**
   * calcBubP.
   *
   * @param temperatures an array of type double
   * @return an array of type double
   */
  public double[] calcBubP(double[] temperatures) {
    double[] bubP = new double[temperatures.length];
    bubPLOG = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      thermoSystem.setTemperature(temperatures[i]);
      try {
        thermoOps.bubblePointPressureFlash(false);
        bubP[i] = thermoSystem.getPressure();
        bubPLOG[i] = bubP[i] * 1e5;
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        bubP[i] = 0;
      }
    }
    return bubP;
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

    // A dry gas has no liquid at standard conditions and a dead oil has no gas, so neither
    // phase can be assumed present. OLGA still requires a finite positive STDGASDENSITY and
    // STDOILDENSITY, which are taken from the table grid when the phase does not flash out.
    PhaseInterface stdGas = thermoSystem.hasPhaseType("gas") ? thermoSystem.getPhaseOfType("gas") : null;
    PhaseInterface stdLiquid = resolveLiquidPhase();
    double gasVolume = stdGas == null ? 0.0 : stdGas.getTotalVolume();
    double liquidVolume = stdLiquid == null ? 0.0 : stdLiquid.getTotalVolume();

    GOR = liquidVolume > 0.0 ? gasVolume / liquidVolume : SINGLE_PHASE_GOR;
    GLR = GOR;
    stdGasDens = stdGas != null ? stdGas.getPhysicalProperties().getDensity()
        : representativeValue(props[0], DEFAULT_GAS_DENSITY);
    stdLiqDens = stdLiquid != null ? stdLiquid.getPhysicalProperties().getDensity()
        : representativeValue(props[1], DEFAULT_LIQUID_DENSITY);
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

  /** {@inheritDoc} */
  @Override
  public void run() {
    logger.info("Start creating arrays");

    nProps = 18;
    props = new double[nProps][pressures.length][temperatures.length];
    units = new String[nProps];
    names = new String[nProps];
    namesKeyword = new String[nProps];
    needsGas = new boolean[nProps];
    needsLiquid = new boolean[nProps];
    gasPresent = new boolean[pressures.length][temperatures.length];
    liquidPresent = new boolean[pressures.length][temperatures.length];
    calcPhaseEnvelope();
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

        // A flash only returns the phases that exist. Resolving them by TYPE rather than by
        // array position keeps the gas column gas and the liquid column liquid even when the
        // node is single-phase, where getPhase(0) is whichever phase happens to be present.
        PhaseInterface gas = thermoSystem.hasPhaseType("gas") ? thermoSystem.getPhaseOfType("gas") : null;
        PhaseInterface liquid = resolveLiquidPhase();
        gasPresent[i][j] = gas != null;
        liquidPresent[i][j] = liquid != null;

        int k = 0;
        props[k][i][j] = density(gas);
        names[k] = "GAS DENSITY";
        units[k] = "KG/M3";
        namesKeyword[k] = "ROG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = density(liquid);
        names[k] = "LIQUID DENSITY";
        units[k] = "KG/M3";
        namesKeyword[k] = "ROHL";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = gas == null ? Double.NaN : gas.getdrhodP() / 1.0e5;
        names[k] = "DRHOG/DP";
        units[k] = "S2/M2";
        namesKeyword[k] = "DROGDP";
        needsGas[k] = true;
        k++;
        props[k][i][j] = liquid == null ? Double.NaN : liquid.getdrhodP() / 1.0e5;
        names[k] = "DRHOL/DP";
        units[k] = "S2/M2";
        namesKeyword[k] = "DROHLDP";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = gas == null ? Double.NaN : gas.getdrhodT();
        names[k] = "DRHOG/DT";
        units[k] = "KG/M3-K";
        namesKeyword[k] = "DROGDT";
        needsGas[k] = true;
        k++;
        props[k][i][j] = liquid == null ? Double.NaN : liquid.getdrhodT();
        names[k] = "DRHOL/DT";
        units[k] = "KG/M3-K";
        namesKeyword[k] = "DROHLDT";
        needsLiquid[k] = true;
        k++;
        // Genuinely zero when there is no gas, so this column is never extrapolated.
        props[k][i][j] = gas == null ? 0.0 : gas.getBeta() * gas.getMolarMass() / thermoSystem.getMolarMass();
        names[k] = "GAS MASS FRACTION";
        units[k] = "-";
        namesKeyword[k] = "RS";
        k++;
        props[k][i][j] = viscosity(gas);
        names[k] = "GAS VISCOSITY";
        units[k] = "NS/M2";
        namesKeyword[k] = "VISG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = viscosity(liquid);
        names[k] = "LIQUID VISCOSITY";
        units[k] = "NS/M2";
        namesKeyword[k] = "VISHL";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = specificHeatCapacity(gas);
        names[k] = "GAS HEAT CAPACITY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "CPG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificHeatCapacity(liquid);
        names[k] = "LIQUID HEAT CAPACITY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "CPHL";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = specificEnthalpy(gas);
        names[k] = "GAS ENTHALPY";
        units[k] = "J/KG";
        namesKeyword[k] = "HG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificEnthalpy(liquid);
        names[k] = "LIQUD ENTHALPY";
        units[k] = "J/KG";
        namesKeyword[k] = "HHL";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = conductivity(gas);
        names[k] = "GAS THERMAL CONDUCTIVITY";
        units[k] = "W/M-K";
        namesKeyword[k] = "TCG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = conductivity(liquid);
        names[k] = "LIQUID THERMAL CONDUCTIVITY";
        units[k] = "W/M-K";
        namesKeyword[k] = "TCHL";
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = (gas == null || liquid == null) ? Double.NaN
            : thermoSystem.getInterphaseProperties().getSurfaceTension(phaseIndex(gas), phaseIndex(liquid));
        names[k] = "VAPOR-LIQUID SURFACE TENSION";
        units[k] = "N/M";
        namesKeyword[k] = "SIGGHL";
        needsGas[k] = true;
        needsLiquid[k] = true;
        k++;
        props[k][i][j] = specificEntropy(gas);
        names[k] = "GAS ENTROPY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "SEG";
        needsGas[k] = true;
        k++;
        props[k][i][j] = specificEntropy(liquid);
        names[k] = "LIQUID ENTROPY";
        units[k] = "J/KG-K";
        namesKeyword[k] = "SEHL";
        needsLiquid[k] = true;
        k++;
      }
    }
    fillAbsentPhaseNodes();
    bubP = calcBubP(temperatures);
    // dewP = calcDewP(temperatures);
    // One bubble point temperature per pressure - this is what the BUBBLETEMPERATURES
    // keyword expects, and what writeOLGAinpFile iterates over.
    bubT = calcBubT(pressures);
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
        // + "ROG"+ROG[i][j]+"ROL" + ROL[i][j]);
      }
    }
    writeOLGAinpFile("");
  }

  /**
   * writeOLGAinpFile.
   *
   * @param filename a {@link java.lang.String} object
   */
  public void writeOLGAinpFile(String filename) {
    File outputFile = new File(filename);
    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      logger.error("Could not create output directory {}", parent.getAbsolutePath());
      return;
    }
    try (FileOutputStream outputStream = new FileOutputStream(outputFile);
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))) {
      writer.write("PVTTABLE LABEL = " + "\"" + fluidLabel + "\"" + "," + "PHASE = TWO" + ",\\" + "\n");
      writer.write("EOS = " + "\"" + thermoSystem.getModelName() + "\"" + ",\\" + "\n");

      writer.write("COMPONENTS = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write("\"" + components[i] + "\""); // How to set extra " ??
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write("),\\" + "\n");

      writer.write("MOLES = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(molfracs[i] + "");
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write("),\\" + "\n");

      writer.write("MOLWEIGHT = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(MW[i] + "");
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") g/mol,\\" + "\n");

      writer.write("DENSITY = (");
      for (int i = 0; i < molfracs.length; i++) {
        writer.write(dens[i] + "");
        if (i < molfracs.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") g/cm3,\\" + "\n");

      writer.write("STDPRESSURE = " + stdPresATM + " ATM,\\" + "\n");
      writer.write("STDTEMPERATURE = " + stdTemp + " K,\\" + "\n");
      writer.write("GOR = " + GOR + " Sm3/Sm3,\\" + "\n");
      writer.write("GLR = " + GLR + " Sm3/Sm3,\\" + "\n");
      writer.write("STDGASDENSITY = " + stdGasDens + " kg/m3,\\" + "\n");
      writer.write("STDOILDENSITY = " + stdLiqDens + " kg/m3,\\" + "\n");
      writer.write("CRITICALPRESSURE = " + PCLOG + " ATM,\\" + "\n");
      writer.write("CRITICALTEMPERATURE = " + TCLOG + " K,\\" + "\n");

      writer.write("MESHTYPE = STANDARD" + ",\\" + "\n");

      writer.write("PRESSURE = (");
      for (int i = 0; i < pressures.length; i++) {
        writer.write(pressureLOG[i] + "");
        if (i < pressures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") Pa,\\" + "\n");

      writer.write("TEMPERATURE = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(temperatureLOG[i] + "");
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") C,\\" + "\n");

      // OLGA requires BUBBLEPRESSURES and BUBBLETEMPERATURES to be paired arrays of
      // equal length: the bubble point pressure at each grid temperature, and the
      // grid temperature it belongs to.
      writer.write("BUBBLEPRESSURES = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(bubPLOG[i] + "");
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") Pa,\\" + "\n");

      writer.write("BUBBLETEMPERATURES = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(temperatureLOG[i] + "");
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") C,\\" + "\n");

      writer.write("COLUMNS = (PT,TM,");
      for (int k = 0; k < nProps; k++) {
        writer.write(namesKeyword[k] + "");
        if (k < nProps - 1) {
          writer.write(",");
        }
      }
      writer.write(")" + "\n");

      for (int i = 0; i < pressures.length; i++) {
        thermoSystem.setPressure(pressures[i]);
        for (int j = 0; j < temperatures.length; j++) {
          thermoSystem.setTemperature(temperatures[j]);
          writer.write("PVTTABLE POINT = (");
          writer.write(pressureLOG[i] + ",");
          writer.write(temperatureLOG[j] + ",");
          for (int k = 0; k < nProps; k++) {
            writer.write(props[k][i][j] + "");
            if (k < nProps - 1) {
              writer.write(",");
            }
          }
          writer.write(")" + "\n");
        }
      }
    } catch (IOException ex) {
      logger.error("Failed writing OLGA table to " + filename, ex);
    }
  }
}

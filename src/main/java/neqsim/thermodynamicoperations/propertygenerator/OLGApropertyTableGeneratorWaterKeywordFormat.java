package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
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
 * OLGApropertyTableGeneratorWaterKeywordFormat class.
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
  /** GOR written when no liquid forms at standard conditions, so the key stays finite. */
  private static final double SINGLE_PHASE_GOR = 1.0e6;

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
   * The phase mass fractions pin at zero there, so the extrapolated branch is never used in a flow calculation.
   * </p>
   */
  private void fillAbsentPhaseNodes() {
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
      if (mask != null) {
        OlgaTableGridFiller.fillAbsentNodes(props[k], mask, defaultFor(namesKeyword[k]));
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
      thermoOps.calcPTphaseEnvelopeNew();
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

    PhaseInterface stdGas = thermoSystem.hasPhaseType("gas") ? thermoSystem.getPhaseOfType("gas") : null;
    PhaseInterface stdOil = thermoSystem.hasPhaseType("oil") ? thermoSystem.getPhaseOfType("oil") : null;
    PhaseInterface stdWater = thermoSystem.hasPhaseType("aqueous") ? thermoSystem.getPhaseOfType("aqueous") : null;
    double gasVolume = stdGas == null ? 0.0 : stdGas.getTotalVolume();
    double oilVolume = stdOil == null ? 0.0 : stdOil.getTotalVolume();

    GOR = oilVolume > 0.0 ? gasVolume / oilVolume : SINGLE_PHASE_GOR;
    GLR = GOR;

    stdGasDens = stdGas != null ? stdGas.getPhysicalProperties().getDensity()
        : representativeValue(props[0], DEFAULT_GAS_DENSITY);
    stdLiqDens = stdOil != null ? stdOil.getPhysicalProperties().getDensity()
        : representativeValue(props[1], DEFAULT_OIL_DENSITY);
    stdWatDens = stdWater != null ? stdWater.getPhysicalProperties().getDensity()
        : representativeValue(props[2], DEFAULT_WATER_DENSITY);
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
        // Genuinely zero without gas, so these two fractions are never extrapolated.
        props[k][i][j] = gas == null ? 0.0 : gas.getBeta() * gas.getMolarMass() / thermoSystem.getMolarMass();
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
        // + " ROG " + ROG[i][j] + " ROL " + ROL[i][j]);
      }
    }
    writeOLGAinpFile("test.tab");
  }

  /**
   * Writes an OLGA .inp file for PVT table input.
   *
   * @param filename a {@link java.lang.String} object
   */
  public void writeOLGAinpFile(String filename) {
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

      // OLGA requires BUBBLEPRESSURES and BUBBLETEMPERATURES to be paired arrays of
      // equal length: the bubble point pressure at each grid temperature, and the
      // grid temperature it belongs to.
      writer.write("BUBBLEPRESSURES = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(Double.toString(bubPLOG[i]));
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") Pa,\\\n");

      writer.write("BUBBLETEMPERATURES = (");
      for (int i = 0; i < temperatures.length; i++) {
        writer.write(Double.toString(temperatureLOG[i]));
        if (i < temperatures.length - 1) {
          writer.write(",");
        }
      }
      writer.write(") C,\\\n");

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

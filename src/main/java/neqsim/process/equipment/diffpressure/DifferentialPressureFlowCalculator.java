package neqsim.process.equipment.diffpressure;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Utility for calculating mass flow rates from differential pressure devices using NeqSim
 * thermodynamic properties. The implementation mirrors the calculation logic used in the
 * tilstandomatic flow calculation tool.
 */
public final class DifferentialPressureFlowCalculator {

  private static final double DEFAULT_STANDARD_PRESSURE_BARA = 1.0125;
  private static final double DEFAULT_STANDARD_TEMPERATURE_K = 273.15 + 15.0;
  private static final double BARG_TO_BARA_OFFSET = 1.0125;
  private static final double BAR_TO_PA = 1.0e5;
  private static final double CELSIUS_TO_KELVIN = 273.15;
  private static final int CONVERGING_MASS_COUNT_MAX = 100;

  private static final Map<String, Double> DEFAULT_DISCHARGE_COEFFICIENTS;

  static {
    Map<String, Double> coefficients = new HashMap<>();
    coefficients.put("Venturi", 0.985);
    coefficients.put("V-Cone", 0.82);
    DEFAULT_DISCHARGE_COEFFICIENTS = Collections.unmodifiableMap(coefficients);
  }

  private static final String[] DEFAULT_COMPONENTS =
      {"H2O", "N2", "CO2", "C1", "C2", "C3", "iC4", "nC4", "iC5", "nC5", "C6"};

  private static final double[] DEFAULT_FRACTIONS = {0, 0, 0, 80, 10, 10, 0, 0, 0, 0, 0};

  private DifferentialPressureFlowCalculator() {
    // Utility class
  }

  /**
   * Result container for the differential pressure flow calculation.
   */
  public static final class FlowCalculationResult {
    private final double[] massFlowKgPerHour;
    private final double[] volumetricFlowM3PerHour;
    private final double[] standardFlowMSm3PerDay;
    private final double[] molecularWeightGPerMol;

    FlowCalculationResult(double[] massFlowKgPerHour, double[] volumetricFlowM3PerHour,
        double[] standardFlowMSm3PerDay, double[] molecularWeightGPerMol) {
      this.massFlowKgPerHour = massFlowKgPerHour;
      this.volumetricFlowM3PerHour = volumetricFlowM3PerHour;
      this.standardFlowMSm3PerDay = standardFlowMSm3PerDay;
      this.molecularWeightGPerMol = molecularWeightGPerMol;
    }

    /**
     * Gets the mass flow rate in kg/h.
     *
     * @return mass flow array
     */
    public double[] getMassFlowKgPerHour() {
      return Arrays.copyOf(massFlowKgPerHour, massFlowKgPerHour.length);
    }

    /**
     * Gets the actual volumetric flow rate in m3/h.
     *
     * @return volumetric flow array
     */
    public double[] getVolumetricFlowM3PerHour() {
      return Arrays.copyOf(volumetricFlowM3PerHour, volumetricFlowM3PerHour.length);
    }

    /**
     * Gets the standard volumetric flow rate in MSm3/day.
     *
     * @return standard volumetric flow array
     */
    public double[] getStandardFlowMSm3PerDay() {
      return Arrays.copyOf(standardFlowMSm3PerDay, standardFlowMSm3PerDay.length);
    }

    /**
     * Gets the molecular weight in g/mol.
     *
     * @return molecular weight array
     */
    public double[] getMolecularWeightGPerMol() {
      return Arrays.copyOf(molecularWeightGPerMol, molecularWeightGPerMol.length);
    }
  }

  /**
   * Calculates flow rates for the provided operating conditions.
   *
   * @param pressureBarg pressure values in barg
   * @param temperatureC temperature values in degrees Celsius
   * @param differentialPressureMbar differential pressure across restriction in mbar
   * @param flowType device type (Venturi, Orifice, ISA1932, V-Cone, DallTube, Annubar, Nozzle,
   *        Simplified, Perrys-Orifice)
   * @param flowData geometry parameters (see individual calculation methods)
   * @param components gas component names
   * @param fractions component fractions (mole basis)
   * @param normalizeFractions whether to normalise the composition fractions
   * @return flow calculation result
   */
  public static FlowCalculationResult calculate(double[] pressureBarg, double[] temperatureC,
      double[] differentialPressureMbar, String flowType, double[] flowData,
      List<String> components, double[] fractions, boolean normalizeFractions) {

    Objects.requireNonNull(pressureBarg, "pressureBarg");
    Objects.requireNonNull(temperatureC, "temperatureC");
    Objects.requireNonNull(differentialPressureMbar, "differentialPressureMbar");

    if (pressureBarg.length != temperatureC.length
        || pressureBarg.length != differentialPressureMbar.length) {
      throw new IllegalArgumentException(
          "Pressure, temperature and dp arrays must have same length");
    }

    double[] validatedFlowData = flowData != null ? Arrays.copyOf(flowData, flowData.length)
        : new double[] {300.0, 200.0, 0.9};

    List<String> componentList = components != null && !components.isEmpty() ? components
        : Arrays.asList(DEFAULT_COMPONENTS.clone());
    double[] fractionArray =
        fractions != null && fractions.length > 0 ? Arrays.copyOf(fractions, fractions.length)
            : Arrays.copyOf(DEFAULT_FRACTIONS, DEFAULT_FRACTIONS.length);

    if (componentList.size() != fractionArray.length) {
      throw new IllegalArgumentException("Component and fraction list must have same length");
    }

    double[] massFlowKgPerHour = new double[pressureBarg.length];
    double[] volumetricFlowM3PerHour = new double[pressureBarg.length];
    double[] standardFlowMSm3PerDay = new double[pressureBarg.length];
    double[] molecularWeightGPerMol = new double[pressureBarg.length];

    SystemInterface baseSystem = createBaseSystem(componentList, fractionArray, normalizeFractions);
    double molecularWeight = baseSystem.getMolarMass() * 1000.0;

    SystemInterface actualSystem = (SystemInterface) baseSystem.clone();
    SystemInterface standardSystem = (SystemInterface) baseSystem.clone();
    SystemInterface zeroPressureSystem = (SystemInterface) baseSystem.clone();

    double standardDensity = calculateStandardDensity(standardSystem);

    double[] pressurePa = new double[pressureBarg.length];
    double[] density = new double[pressureBarg.length];
    double[] viscosity = new double[pressureBarg.length];
    double[] kappa = new double[pressureBarg.length];
    double[] dpPa = new double[pressureBarg.length];

    for (int i = 0; i < pressureBarg.length; i++) {
      double pressureAbar = pressureBarg[i] + BARG_TO_BARA_OFFSET;
      double temperatureKelvin = temperatureC[i] + CELSIUS_TO_KELVIN;
      double absolutePressurePa = pressureAbar * BAR_TO_PA;
      double differentialPressurePa = differentialPressureMbar[i] * 100.0;

      setState(actualSystem, temperatureKelvin, pressureAbar);
      density[i] = actualSystem.getDensity("kg/m3");
      viscosity[i] = actualSystem.getViscosity("kg/msec");

      setState(zeroPressureSystem, temperatureKelvin, DEFAULT_STANDARD_PRESSURE_BARA);
      kappa[i] = zeroPressureSystem.getGamma();

      pressurePa[i] = absolutePressurePa;
      dpPa[i] = differentialPressurePa;
      molecularWeightGPerMol[i] = molecularWeight;
    }

    double[] calculatedMassFlow =
        calculateMassFlow(flowType, validatedFlowData, pressurePa, density, viscosity, kappa, dpPa);

    for (int i = 0; i < pressureBarg.length; i++) {
      massFlowKgPerHour[i] = calculatedMassFlow[i];
      volumetricFlowM3PerHour[i] =
          density[i] != 0.0 ? calculatedMassFlow[i] / density[i] : Double.NaN;
      standardFlowMSm3PerDay[i] =
          standardDensity != 0.0 ? calculatedMassFlow[i] / standardDensity * 24.0 / 1.0e6
              : Double.NaN;
    }

    return new FlowCalculationResult(massFlowKgPerHour, volumetricFlowM3PerHour,
        standardFlowMSm3PerDay, molecularWeightGPerMol);
  }

  /**
   * Convenience overload using default composition and normalisation.
   *
   * @param pressureBarg pressure values in barg
   * @param temperatureC temperature values in degrees Celsius
   * @param differentialPressureMbar differential pressure across restriction in mbar
   * @param flowType device type (Venturi, Orifice, ISA1932, V-Cone, DallTube, Annubar, Nozzle,
   *        Simplified, Perrys-Orifice)
   * @param flowData geometry parameters (see individual calculation methods)
   * @return flow calculation result
   */
  public static FlowCalculationResult calculate(double[] pressureBarg, double[] temperatureC,
      double[] differentialPressureMbar, String flowType, double[] flowData) {
    return calculate(pressureBarg, temperatureC, differentialPressureMbar, flowType, flowData, null,
        null, true);
  }

  /**
   * Convenience overload with default flow data and composition.
   *
   * @param pressureBarg pressure values in barg
   * @param temperatureC temperature values in degrees Celsius
   * @param differentialPressureMbar differential pressure across restriction in mbar
   * @param flowType device type (Venturi, Orifice, ISA1932, V-Cone, DallTube, Annubar, Nozzle,
   *        Simplified, Perrys-Orifice)
   * @return flow calculation result
   */
  public static FlowCalculationResult calculate(double[] pressureBarg, double[] temperatureC,
      double[] differentialPressureMbar, String flowType) {
    return calculate(pressureBarg, temperatureC, differentialPressureMbar, flowType, null, null,
        null, true);
  }

  private static SystemInterface createBaseSystem(List<String> components, double[] fractions,
      boolean normalize) {
    double[] composition = Arrays.copyOf(fractions, fractions.length);
    if (normalize) {
      double sum = 0.0;
      for (double value : composition) {
        sum += value;
      }
      if (sum <= 0.0) {
        throw new IllegalArgumentException("Composition sum must be greater than zero");
      }
      for (int i = 0; i < composition.length; i++) {
        composition[i] = composition[i] / sum;
      }
    }

    SystemInterface system =
        new SystemSrkEos(DEFAULT_STANDARD_TEMPERATURE_K, DEFAULT_STANDARD_PRESSURE_BARA);
    for (int i = 0; i < components.size(); i++) {
      system.addComponent(components.get(i), composition[i]);
    }

    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
    system.init(1);
    system.initPhysicalProperties();

    return system;
  }

  private static double calculateStandardDensity(SystemInterface standardSystem) {
    setState(standardSystem, DEFAULT_STANDARD_TEMPERATURE_K, DEFAULT_STANDARD_PRESSURE_BARA);
    return standardSystem.getDensity("kg/m3");
  }

  private static void setState(SystemInterface system, double temperatureK, double pressureAbar) {
    system.setTemperature(temperatureK);
    system.setPressure(pressureAbar, "bara");
    system.init(0);
    system.init(1);
    system.initPhysicalProperties();
  }

  private static double[] calculateMassFlow(String flowTypeRaw, double[] flowData, double[] p,
      double[] rho, double[] mu, double[] kappa, double[] dp) {
    String flowType = normaliseFlowType(flowTypeRaw);
    double dischargeCoefficient = resolveDischargeCoefficient(flowType, flowData);

    double[] result;
    switch (flowType) {
      case "Venturi":
        result = calcVenturi(dp, p, rho, kappa, flowData[0] / 1000.0, flowData[1] / 1000.0,
            dischargeCoefficient);
        break;
      case "Orifice":
        result = calcOrifice(dp, p, rho, kappa, mu, flowData[0] / 1000.0, flowData[1] / 1000.0);
        break;
      case "V-Cone":
        result = calcVCone(dp, p, rho, kappa, flowData[0] / 1000.0, flowData[1] / 1000.0,
            dischargeCoefficient);
        break;
      case "DallTube":
        result = calcDallTube(dp, p, rho, kappa, flowData[0] / 1000.0, flowData[1] / 1000.0);
        break;
      case "Annubar":
        result = calcAnnubar(dp, p, rho, kappa, flowData[0], flowData[1]);
        break;
      case "Nozzle":
        result = calcNozzle(dp, p, rho, kappa, mu, flowData[0] / 1000.0, flowData[1] / 1000.0);
        break;
      case "Simplified":
        result = calcSimplified(dp, rho, flowData);
        break;
      case "Perrys-Orifice":
        result = calcPerrysOrifice(p, dp, rho, flowData[0] / 1000.0, flowData[1] / 1000.0, kappa);
        break;
      case "ISA1932":
        throw new UnsupportedOperationException(
            "ISA1932 calculation is not implemented in this calculator");
      default:
        throw new IllegalArgumentException("Unsupported flow type: " + flowTypeRaw);
    }

    return result;
  }

  private static String normaliseFlowType(String flowTypeRaw) {
    if (flowTypeRaw == null) {
      return "Venturi";
    }
    String key = flowTypeRaw.trim();
    if (key.isEmpty()) {
      return "Venturi";
    }
    Map<String, String> lookup = new HashMap<>();
    lookup.put("VENTURI", "Venturi");
    lookup.put("ORIFICE", "Orifice");
    lookup.put("ISA1932", "ISA1932");
    lookup.put("V-CONE", "V-Cone");
    lookup.put("V CONE", "V-Cone");
    lookup.put("DALLTUBE", "DallTube");
    lookup.put("DALL TUBE", "DallTube");
    lookup.put("ANNUBAR", "Annubar");
    lookup.put("NOZZLE", "Nozzle");
    lookup.put("SIMPLIFIED", "Simplified");
    lookup.put("PERRYS-ORIFICE", "Perrys-Orifice");
    lookup.put("PERRYS ORIFICE", "Perrys-Orifice");
    String upperKey = key.toUpperCase(Locale.ROOT);
    return lookup.getOrDefault(upperKey, key);
  }

  private static double resolveDischargeCoefficient(String flowType, double[] flowData) {
    double candidate = flowData.length > 2 ? flowData[2] : Double.NaN;
    if (Double.isFinite(candidate) && candidate >= 0.4 && candidate <= 1.0) {
      return candidate;
    }
    return DEFAULT_DISCHARGE_COEFFICIENTS.getOrDefault(flowType, 0.985);
  }

  private static double[] calcVenturi(double[] dp, double[] p, double[] rho, double[] kappa,
      double D, double d, double C) {
    double beta = d / D;
    double beta4 = Math.pow(beta, 4.0);
    double betaTerm = Math.sqrt(Math.max(1.0 - beta4, 1e-30));
    double[] massFlow = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      double tau = p[i] / (p[i] + dp[i]);
      double k = kappa[i];
      double tau2k = Math.pow(tau, 2.0 / k);
      double numerator = k * tau2k / (k - 1.0) * (1.0 - beta4) / (1.0 - beta4 * tau2k)
          * (1.0 - Math.pow(tau, (k - 1.0) / k)) / (1.0 - tau);
      double eps = Math.sqrt(Math.max(numerator, 0.0));
      double rootTerm = Math.sqrt(Math.max(dp[i] * rho[i] * 2.0, 0.0));
      double value = C / betaTerm * eps * Math.PI / 4.0 * d * d * rootTerm;
      massFlow[i] = tau == 1.0 ? 0.0 : value * 3600.0;
    }
    return massFlow;
  }

  private static double[] calcOrifice(double[] dp, double[] p, double[] rho, double[] kappa,
      double[] mu, double D, double d) {
    double beta = d / D;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double beta8 = beta4 * beta4;
    double L1 = 1.0;
    double L2 = 0.47;
    double M2 = 2.0 * L2 / (1.0 - beta);
    double[] eps = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      double pressureRatio = (p[i] - dp[i]) / p[i];
      eps[i] = 1.0 - (0.351 + 0.256 * beta4 + 0.93 * beta8)
          * (1.0 - Math.pow(pressureRatio, 1.0 / kappa[i]));
    }

    double[] m = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      m[i] = 10000.0 / 4.0 * Math.PI * mu[i] * D;
    }

    int count = 0;
    while (count < 100) {
      double maxDiff = 0.0;
      for (int i = 0; i < dp.length; i++) {
        double prev = m[i];
        double ReD = Math.max(4.0 * m[i] / (Math.PI * mu[i] * D), 500.0);
        double A = Math.pow(19000.0 * beta / ReD, 0.8);
        double C =
            0.5961 + 0.0261 * beta2 - 0.216 * beta8 + 0.000521 * Math.pow(1.0e7 * beta / ReD, 0.7)
                + (0.0188 + 0.0063 * A) * Math.pow(beta, 3.5) * Math.pow(1.0e7 / ReD, 0.3)
                + (0.043 + 0.080 * Math.exp(-10.0 * L1) - 0.123 * Math.exp(-7.0 * L1))
                    * (1.0 - 0.22 * A) * beta4 / (1.0 - beta4)
                - 0.031 * (M2 - 0.8 * Math.pow(M2, 1.1)) * Math.pow(beta, 1.3);
        if (D < 71.12 / 1000.0) {
          C += 0.011 * (0.75 - beta) * (2.8 - d / 0.0254);
        }
        double rootTerm = Math.sqrt(Math.max(dp[i] * rho[i] * 2.0, 0.0));
        m[i] = C / Math.sqrt(1.0 - beta4) * eps[i] * Math.PI / 4.0 * d * d * rootTerm;
        maxDiff = Math.max(maxDiff, Math.abs(m[i] - prev));
      }
      if (maxDiff <= 0.01) {
        break;
      }
      count++;
    }

    for (int i = 0; i < m.length; i++) {
      m[i] *= 3600.0;
    }
    return m;
  }

  private static double[] calcVCone(double[] dp, double[] p, double[] rho, double[] kappa, double D,
      double d, double C) {
    double[] massFlow = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      double beta = Math.sqrt(1.0 - (d * d) / (D * D));
      double eps = 1.0 - (0.649 + 0.696 * Math.pow(beta, 4.0)) * dp[i] / (kappa[i] * p[i]);
      double rootTerm = Math.sqrt(Math.max(dp[i] * rho[i] * 2.0, 0.0));
      massFlow[i] = C / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * eps * Math.PI / 4.0
          * Math.pow(D * beta, 2.0) * rootTerm * 3600.0;
    }
    return massFlow;
  }

  private static double[] calcDallTube(double[] dp, double[] p, double[] rho, double[] kappa,
      double D, double d) {
    double[] massFlow = new double[dp.length];
    double beta = (d * 1000.0) / (D * 1000.0);
    double E = 1.0 / Math.sqrt(1.0 - Math.pow(beta, 4.0));
    double C1 = 1.046;
    double C2 = 1.094116;
    double C3 = 0.04518228;
    double C4 = 0.664843419;
    double C5 = 0.273529;
    double C = 0.5
        * (C1 + Math.sqrt(C2 - 4.0 * (C3 * (Math.pow(beta * beta + 0.08, 2.0) / C4 - 1.0) + C5)));
    double N = 0.1264467;
    double Fa = 1.0;
    double A = N * Fa * C * E * beta * beta * Math.pow(D * 1000.0, 2.0);
    for (int i = 0; i < dp.length; i++) {
      double hw = dp[i] / 1000.0;
      double rootTerm = Math.sqrt(Math.max(hw * rho[i], 0.0));
      massFlow[i] = A * rootTerm;
    }
    return massFlow;
  }

  private static double[] calcAnnubar(double[] dp, double[] p, double[] rho, double[] kappa,
      double D, double elementSize) {
    double[] massFlow = new double[dp.length];
    double Y1 = 0.31424;
    double Y2 = 0.09484;
    double C1;
    double C2;
    double d;
    if (elementSize == 1) {
      C1 = -1.515;
      C2 = 1.0 - 4229.0;
      d = 1.4986;
    } else if (elementSize == 2) {
      C1 = -1.492;
      C2 = 1.4179;
      d = 2.6924;
    } else if (elementSize == 3) {
      C1 = -1.5856;
      C2 = 1.3318;
      d = 4.915;
    } else {
      C1 = 1.0;
      C2 = 1.0;
      d = 1.0;
    }

    double B = (4.0 * d) / (Math.PI * D);
    double Fna = 0.12645;
    double Faa = 1.0;
    double K = (1.0 - C2 * B) / Math.sqrt(1.0 - C1 * Math.pow(1.0 - C2 * B, 2.0));

    for (int i = 0; i < dp.length; i++) {
      double hw = dp[i] * 0.0010;
      double Pf = p[i] / 1000.0;
      double Y = 1.0 - (Y1 * Math.pow(1.0 - B, 2.0) - Y2) * hw / (Pf * kappa[i]);
      double C = Fna * K * D * D * Y * Faa * Math.sqrt(rho[i]);
      massFlow[i] = C * Math.pow(hw, 0.5);
    }
    return massFlow;
  }

  private static double[] calcNozzle(double[] dp, double[] p, double[] rho, double[] kappa,
      double[] mu, double D, double d) {
    double beta = d / D;
    double beta4 = Math.pow(beta, 4.0);
    double[] massFlow = new double[dp.length];
    double[] m = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      m[i] = 10000.0 / 4.0 * Math.PI * mu[i] * D;
    }

    int count = 0;
    while (count < CONVERGING_MASS_COUNT_MAX) {
      double maxDiff = 0.0;
      for (int i = 0; i < dp.length; i++) {
        double tau = p[i] / (p[i] + dp[i]);
        double k = kappa[i];
        double tau2k = Math.pow(tau, 2.0 / k);
        double numerator = k * tau2k / (k - 1.0) * (1.0 - beta4) / (1.0 - beta4 * tau2k)
            * (1.0 - Math.pow(tau, (k - 1.0) / k)) / (1.0 - tau);
        double eps = Math.sqrt(Math.max(numerator, 0.0));
        double ReD = Math.max(4.0 * m[i] / (Math.PI * mu[i] * D), 500.0);
        double C = 0.99 - 0.2262 * Math.pow(beta, 4.1)
            - (0.00175 * beta * beta - 0.0033 * Math.pow(beta, 4.15)) * Math.pow(1.0e7 / ReD, 1.15);
        double rootTerm = Math.sqrt(Math.max(dp[i] * rho[i] * 2.0, 0.0));
        double newM = C / Math.sqrt(1.0 - beta4) * eps * Math.PI / 4.0 * d * d * rootTerm;
        if (tau == 1.0) {
          newM = 0.0;
        }
        maxDiff = Math.max(maxDiff, Math.abs(newM - m[i]));
        m[i] = newM;
      }
      if (maxDiff <= 0.01) {
        break;
      }
      count++;
    }

    if (count == CONVERGING_MASS_COUNT_MAX) {
      Arrays.fill(m, Double.NaN);
    }

    for (int i = 0; i < m.length; i++) {
      if (!Double.isNaN(m[i])) {
        massFlow[i] = m[i] * 3600.0;
      } else {
        massFlow[i] = Double.NaN;
      }
    }
    return massFlow;
  }

  private static double[] calcSimplified(double[] dp, double[] rho, double[] flowData) {
    if (flowData.length == 0) {
      throw new IllegalArgumentException("FlowData must contain at least the Cv value");
    }
    double Cv = flowData[0];
    if (Cv <= 0.0) {
      throw new IllegalArgumentException(
          "FlowData[0] must be greater than 0 when FlowType=Simplified");
    }
    double[] massFlow = new double[dp.length];
    for (int i = 0; i < dp.length; i++) {
      massFlow[i] = Cv * Math.sqrt(Math.max(dp[i] * rho[i], 0.0));
    }
    return massFlow;
  }

  private static double[] calcPerrysOrifice(double[] p1, double[] dp, double[] rho, double D,
      double d, double[] kappa) {
    double[] massFlow = new double[dp.length];
    double beta = d / D;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    for (int i = 0; i < dp.length; i++) {
      double p1Bar = p1[i] / BAR_TO_PA;
      double p2Bar = p1Bar - dp[i] / BAR_TO_PA;
      double rp = p2Bar / p1Bar;
      double rc = Math.pow(2.0 / (kappa[i] + 1.0), kappa[i] / (kappa[i] - 1.0));
      double CdSub = 0.62;
      double YSub = 1.0 - ((1.0 - rp) / kappa[i]) * (0.41 + 0.35 * beta4);
      double flowSub =
          CdSub * YSub * Math.PI / 4.0 * d * d * Math.sqrt(2.0 * dp[i] * rho[i] / (1.0 - beta4));
      double CdCrit = 0.84 - (0.84 - 0.75) * rp / rc;
      double flowCrit = Math.PI / 4.0 * d * d
          * (CdCrit * p1Bar * BAR_TO_PA * Math.sqrt(kappa[i] * (rho[i] / (p1Bar * BAR_TO_PA))
              * Math.pow(2.0 / (kappa[i] + 1.0), (kappa[i] + 1.0) / (kappa[i] - 1.0))));
      double flow = rp > rc ? flowSub : flowCrit;
      massFlow[i] = flow * 3600.0;
    }
    return massFlow;
  }

  /**
   * Calculates differential pressure from mass flow rate for a Venturi meter.
   *
   * <p>
   * This is the inverse of the flow calculation. Given the mass flow rate and fluid properties, it
   * calculates the differential pressure across the Venturi. Uses an iterative approach since the
   * expansibility factor depends on the pressure ratio.
   * </p>
   *
   * @param massFlowKgPerHour mass flow rate in kg/h
   * @param pressureBara upstream pressure in bara
   * @param density fluid density in kg/m³
   * @param kappa isentropic exponent (Cp/Cv)
   * @param pipeDiameterMm pipe diameter in mm
   * @param throatDiameterMm throat diameter in mm
   * @param dischargeCoefficient discharge coefficient (default 0.985 for Venturi)
   * @return differential pressure in mbar
   */
  public static double calculateDpFromFlowVenturi(double massFlowKgPerHour, double pressureBara,
      double density, double kappa, double pipeDiameterMm, double throatDiameterMm,
      double dischargeCoefficient) {

    double D = pipeDiameterMm / 1000.0;
    double d = throatDiameterMm / 1000.0;
    double C = dischargeCoefficient;
    double massFlowKgPerSec = massFlowKgPerHour / 3600.0;

    double beta = d / D;
    double beta4 = Math.pow(beta, 4.0);
    double betaTerm = Math.sqrt(Math.max(1.0 - beta4, 1e-30));

    // For incompressible flow (eps = 1), dp can be solved directly:
    // m = C/sqrt(1-beta^4) * pi/4 * d^2 * sqrt(2 * rho * dp)
    // Solving for dp: dp = (m * sqrt(1-beta^4) / (C * pi/4 * d^2))^2 / (2 * rho)
    double A = Math.PI / 4.0 * d * d;
    double dpInitial = Math.pow(massFlowKgPerSec * betaTerm / (C * A), 2) / (2.0 * density);

    // Iterate to account for expansibility factor
    double dpPa = dpInitial;
    double pPa = pressureBara * BAR_TO_PA;

    for (int iter = 0; iter < CONVERGING_MASS_COUNT_MAX; iter++) {
      double tau = pPa / (pPa + dpPa);
      double tau2k = Math.pow(tau, 2.0 / kappa);
      double numerator = kappa * tau2k / (kappa - 1.0) * (1.0 - beta4) / (1.0 - beta4 * tau2k)
          * (1.0 - Math.pow(tau, (kappa - 1.0) / kappa)) / (1.0 - tau);
      double eps = Math.sqrt(Math.max(numerator, 1e-30));

      // Recalculate dp with the expansibility factor
      double dpNew = Math.pow(massFlowKgPerSec * betaTerm / (C * eps * A), 2) / (2.0 * density);

      if (Math.abs(dpNew - dpPa) < 0.01) {
        dpPa = dpNew;
        break;
      }
      dpPa = dpNew;
    }

    // Convert Pa to mbar
    return dpPa / 100.0;
  }

  /**
   * Calculates differential pressure from mass flow rate for a Venturi meter using default
   * discharge coefficient.
   *
   * @param massFlowKgPerHour mass flow rate in kg/h
   * @param pressureBara upstream pressure in bara
   * @param density fluid density in kg/m³
   * @param kappa isentropic exponent (Cp/Cv)
   * @param pipeDiameterMm pipe diameter in mm
   * @param throatDiameterMm throat diameter in mm
   * @return differential pressure in mbar
   */
  public static double calculateDpFromFlowVenturi(double massFlowKgPerHour, double pressureBara,
      double density, double kappa, double pipeDiameterMm, double throatDiameterMm) {
    return calculateDpFromFlowVenturi(massFlowKgPerHour, pressureBara, density, kappa,
        pipeDiameterMm, throatDiameterMm, 0.985);
  }

  /**
   * Calculates differential pressure from mass flow rate using NeqSim thermodynamic properties.
   *
   * @param massFlowKgPerHour mass flow rate in kg/h
   * @param pressureBarg pressure in barg
   * @param temperatureC temperature in degrees Celsius
   * @param flowType device type (currently only "Venturi" supported)
   * @param flowData geometry parameters [pipeDiameterMm, throatDiameterMm, dischargeCoefficient]
   * @param components gas component names
   * @param fractions component fractions (mole basis)
   * @param normalizeFractions whether to normalise the composition fractions
   * @return differential pressure in mbar
   */
  public static double calculateDpFromFlow(double massFlowKgPerHour, double pressureBarg,
      double temperatureC, String flowType, double[] flowData, List<String> components,
      double[] fractions, boolean normalizeFractions) {

    Objects.requireNonNull(flowData, "flowData");

    double[] validatedFlowData = Arrays.copyOf(flowData, flowData.length);

    List<String> componentList = components != null && !components.isEmpty() ? components
        : Arrays.asList(DEFAULT_COMPONENTS.clone());
    double[] fractionArray =
        fractions != null && fractions.length > 0 ? Arrays.copyOf(fractions, fractions.length)
            : Arrays.copyOf(DEFAULT_FRACTIONS, DEFAULT_FRACTIONS.length);

    if (componentList.size() != fractionArray.length) {
      throw new IllegalArgumentException("Component and fraction list must have same length");
    }

    SystemInterface system = createBaseSystem(componentList, fractionArray, normalizeFractions);

    double pressureAbar = pressureBarg + BARG_TO_BARA_OFFSET;
    double temperatureKelvin = temperatureC + CELSIUS_TO_KELVIN;

    setState(system, temperatureKelvin, pressureAbar);
    double density = system.getDensity("kg/m3");

    SystemInterface zeroPressureSystem = (SystemInterface) system.clone();
    setState(zeroPressureSystem, temperatureKelvin, DEFAULT_STANDARD_PRESSURE_BARA);
    double kappa = zeroPressureSystem.getGamma();

    String normalizedFlowType = normaliseFlowType(flowType);
    double dischargeCoefficient =
        resolveDischargeCoefficient(normalizedFlowType, validatedFlowData);

    if ("Venturi".equals(normalizedFlowType)) {
      return calculateDpFromFlowVenturi(massFlowKgPerHour, pressureAbar, density, kappa,
          validatedFlowData[0], validatedFlowData[1], dischargeCoefficient);
    } else {
      throw new UnsupportedOperationException(
          "Inverse dp calculation not yet implemented for flow type: " + flowType);
    }
  }
}


package neqsim.process.util.fire;

/**
 * Dynamic relief valve (PSV) sizing for fire scenarios per API 521.
 *
 * <p>
 * This class provides methods for sizing pressure safety valves (PSVs) for gas-filled vessels
 * subject to fire exposure. Unlike the traditional conservative API 521 approach which sizes for
 * peak flow, this implementation supports dynamic sizing that accounts for the transient nature of
 * fire-induced blowdown.
 * </p>
 *
 * <p>
 * Key features:
 * <ul>
 * <li>API 520/521 compliant orifice area calculations</li>
 * <li>Support for both conventional and balanced-bellows PSVs</li>
 * <li>Dynamic fire scenarios with time-varying heat input</li>
 * <li>Back pressure effects for multiple PSV installations</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>API Standard 521, 7th Edition (2020) - Pressure-relieving and Depressuring Systems</li>
 * <li>Andreasen, A. (2021). HydDown: A Python package for calculation of hydrogen (or other gas)
 * pressure vessel filling and discharge. Journal of Open Source Software, 6(66), 3695.</li>
 * </ul>
 *
 * @author ESOL
 * @see <a href="https://doi.org/10.21105/joss.03695">HydDown - JOSS Paper</a>
 */
public final class ReliefValveSizing {

  private ReliefValveSizing() {}

  /** Gas constant [J/(mol*K)]. */
  public static final double R_GAS = 8314.0;

  /** Standard API 520 orifice areas [in²]. */
  public static final double[] STANDARD_ORIFICE_AREAS_IN2 = {0.110, 0.196, 0.307, 0.503, 0.785,
      1.287, 1.838, 2.853, 3.600, 4.340, 6.380, 11.05, 16.0, 26.0};

  /** Standard API 520 orifice letter designations. */
  public static final String[] STANDARD_ORIFICE_LETTERS =
      {"D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R", "T"};

  /**
   * Result container for PSV sizing calculations.
   */
  public static final class PSVSizingResult {
    private final double requiredArea;
    private final double requiredAreaIn2;
    private final double massFlowCapacity;
    private final String recommendedOrifice;
    private final double selectedArea;
    private final double selectedAreaIn2;
    private final double overpressureFraction;
    private final double backPressureFraction;
    private final double kd;
    private final double kb;
    private final double kc;

    /**
     * Creates a PSV sizing result.
     *
     * @param requiredArea Required orifice area [m²]
     * @param requiredAreaIn2 Required orifice area [in²]
     * @param massFlowCapacity Mass flow capacity at set pressure [kg/s]
     * @param recommendedOrifice Recommended standard orifice letter
     * @param selectedArea Selected standard orifice area [m²]
     * @param selectedAreaIn2 Selected standard orifice area [in²]
     * @param overpressureFraction Overpressure fraction (typically 0.1 or 0.21)
     * @param backPressureFraction Back pressure / set pressure ratio
     * @param kd Discharge coefficient
     * @param kb Back pressure correction factor
     * @param kc Combination correction factor
     */
    public PSVSizingResult(double requiredArea, double requiredAreaIn2, double massFlowCapacity,
        String recommendedOrifice, double selectedArea, double selectedAreaIn2,
        double overpressureFraction, double backPressureFraction, double kd, double kb, double kc) {
      this.requiredArea = requiredArea;
      this.requiredAreaIn2 = requiredAreaIn2;
      this.massFlowCapacity = massFlowCapacity;
      this.recommendedOrifice = recommendedOrifice;
      this.selectedArea = selectedArea;
      this.selectedAreaIn2 = selectedAreaIn2;
      this.overpressureFraction = overpressureFraction;
      this.backPressureFraction = backPressureFraction;
      this.kd = kd;
      this.kb = kb;
      this.kc = kc;
    }

    public double getRequiredArea() {
      return requiredArea;
    }

    public double getRequiredAreaIn2() {
      return requiredAreaIn2;
    }

    public double getMassFlowCapacity() {
      return massFlowCapacity;
    }

    public String getRecommendedOrifice() {
      return recommendedOrifice;
    }

    public double getSelectedArea() {
      return selectedArea;
    }

    public double getSelectedAreaIn2() {
      return selectedAreaIn2;
    }

    public double getOverpressureFraction() {
      return overpressureFraction;
    }

    public double getBackPressureFraction() {
      return backPressureFraction;
    }

    public double getDischargeCoefficient() {
      return kd;
    }

    public double getBackPressureCorrectionFactor() {
      return kb;
    }

    public double getCombinationCorrectionFactor() {
      return kc;
    }
  }

  /**
   * Calculates the required PSV orifice area for gas/vapor service per API 520.
   *
   * <p>
   * Uses the API 520 equation for critical (sonic) flow:
   * 
   * <pre>
   * A = W / (C * Kd * P1 * Kb * Kc) * sqrt(T * Z / M)
   * </pre>
   *
   * @param massFlowRate Required relieving rate [kg/s]
   * @param setPressure PSV set pressure [Pa absolute]
   * @param overpressureFraction Overpressure fraction (0.10 for fire, 0.21 for external fire)
   * @param backPressure Downstream/back pressure [Pa absolute]
   * @param temperature Relieving temperature [K]
   * @param molecularWeight Molecular weight [kg/mol]
   * @param compressibility Compressibility factor Z
   * @param specificHeatRatio Cp/Cv ratio (gamma/k)
   * @param isBalancedBellows true for balanced-bellows PSV
   * @param hasRuptureDisk true if rupture disk is installed upstream
   * @return PSV sizing result
   */
  public static PSVSizingResult calculateRequiredArea(double massFlowRate, double setPressure,
      double overpressureFraction, double backPressure, double temperature, double molecularWeight,
      double compressibility, double specificHeatRatio, boolean isBalancedBellows,
      boolean hasRuptureDisk) {
    if (massFlowRate <= 0.0) {
      throw new IllegalArgumentException("Mass flow rate must be positive");
    }
    if (setPressure <= 0.0 || temperature <= 0.0 || molecularWeight <= 0.0) {
      throw new IllegalArgumentException("Set pressure, temperature, and MW must be positive");
    }

    // Relieving pressure (set pressure + overpressure)
    double P1 = setPressure * (1.0 + overpressureFraction);

    // Back pressure ratio
    double backPressureFraction = backPressure / P1;

    // Discharge coefficient (Kd)
    // API 520: Kd = 0.975 for gas/vapor through certified PSV
    double kd = 0.975;

    // Capacity correction factor for subcritical flow (Kc)
    // If with rupture disk: Kc = 0.9, otherwise Kc = 1.0
    double kc = hasRuptureDisk ? 0.9 : 1.0;

    // Calculate critical pressure ratio
    double criticalRatio =
        Math.pow(2.0 / (specificHeatRatio + 1.0), specificHeatRatio / (specificHeatRatio - 1.0));

    // Back pressure correction factor (Kb)
    double kb = 1.0;
    if (isBalancedBellows) {
      // For balanced-bellows PSV, Kb depends on back pressure
      if (backPressureFraction > criticalRatio) {
        // Subcritical flow correction
        double r = backPressureFraction;
        kb = Math.sqrt((specificHeatRatio / (specificHeatRatio - 1.0))
            * (Math.pow(r, 2.0 / specificHeatRatio)
                - Math.pow(r, (specificHeatRatio + 1.0) / specificHeatRatio))
            / (1.0 - criticalRatio));
        kb = Math.min(kb, 1.0);
      }
    } else {
      // Conventional PSV - back pressure reduces capacity
      if (backPressureFraction > 0.5) {
        kb = 1.0 - (backPressureFraction - 0.5) * 2.0; // Linear reduction above 50%
        kb = Math.max(kb, 0.1);
      }
    }

    // Gas constant C per API 520 Table 9
    // C = 520 * sqrt(k * (2/(k+1))^((k+1)/(k-1)))
    double k = specificHeatRatio;
    double C = 520.0 * Math.sqrt(k * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));

    // Convert to SI (API uses US customary units internally)
    // Original API equation: A[in²] = W[lb/hr] / (C * Kd * Kb * Kc * P[psia]) * sqrt(T[R] * Z / M)
    // We'll compute in SI and convert

    // Required area in SI [m²]
    // Using derived SI form of API equation
    double MWkg = molecularWeight * 1000.0; // kg/mol to g/mol
    double Ppsia = P1 / 6894.76; // Pa to psia
    double Wlbhr = massFlowRate * 7936.64; // kg/s to lb/hr
    double TR = temperature * 1.8; // K to Rankine

    double A_in2 = Wlbhr / (C * kd * kb * kc * Ppsia) * Math.sqrt(TR * compressibility / MWkg);
    double A_m2 = A_in2 * 6.4516e-4; // in² to m²

    // Select standard orifice
    String selectedOrifice = "T";
    double selectedArea_in2 = STANDARD_ORIFICE_AREAS_IN2[STANDARD_ORIFICE_AREAS_IN2.length - 1];
    for (int i = 0; i < STANDARD_ORIFICE_AREAS_IN2.length; i++) {
      if (STANDARD_ORIFICE_AREAS_IN2[i] >= A_in2) {
        selectedOrifice = STANDARD_ORIFICE_LETTERS[i];
        selectedArea_in2 = STANDARD_ORIFICE_AREAS_IN2[i];
        break;
      }
    }
    double selectedArea_m2 = selectedArea_in2 * 6.4516e-4;

    return new PSVSizingResult(A_m2, A_in2, massFlowRate, selectedOrifice, selectedArea_m2,
        selectedArea_in2, overpressureFraction, backPressureFraction, kd, kb, kc);
  }

  /**
   * Calculates the mass flow capacity of a PSV for gas/vapor service.
   *
   * @param orificeArea Effective orifice area [m²]
   * @param setPressure PSV set pressure [Pa absolute]
   * @param overpressureFraction Overpressure fraction
   * @param backPressure Back pressure [Pa absolute]
   * @param temperature Relieving temperature [K]
   * @param molecularWeight Molecular weight [kg/mol]
   * @param compressibility Compressibility factor Z
   * @param specificHeatRatio Cp/Cv ratio
   * @param dischargeCoefficient Discharge coefficient (typically 0.975)
   * @return Mass flow capacity [kg/s]
   */
  public static double calculateMassFlowCapacity(double orificeArea, double setPressure,
      double overpressureFraction, double backPressure, double temperature, double molecularWeight,
      double compressibility, double specificHeatRatio, double dischargeCoefficient) {
    double P1 = setPressure * (1.0 + overpressureFraction);
    double k = specificHeatRatio;

    // Critical pressure ratio
    double criticalRatio = Math.pow(2.0 / (k + 1.0), k / (k - 1.0));
    double pressureRatio = backPressure / P1;

    double C;
    if (pressureRatio <= criticalRatio) {
      // Choked (sonic) flow
      C = Math.sqrt(k * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    } else {
      // Subsonic flow
      C = Math.sqrt(2.0 * k / (k - 1.0)
          * (Math.pow(pressureRatio, 2.0 / k) - Math.pow(pressureRatio, (k + 1.0) / k)));
    }

    // Mass flow rate
    return dischargeCoefficient * orificeArea * P1 * C
        * Math.sqrt(molecularWeight / (compressibility * R_GAS * temperature));
  }

  /**
   * Calculates the required heat absorption rate for a given PSV size during fire.
   *
   * <p>
   * This is useful for determining if a given PSV size can handle the heat input from a fire
   * scenario.
   * </p>
   *
   * @param massFlowCapacity PSV mass flow capacity [kg/s]
   * @param latentHeat Latent heat of vaporization (or Cp*dT for sensible heating) [J/kg]
   * @return Maximum heat absorption rate [W]
   */
  public static double calculateMaxHeatAbsorption(double massFlowCapacity, double latentHeat) {
    return massFlowCapacity * latentHeat;
  }

  /**
   * Dynamic sizing calculation for fire scenarios.
   *
   * <p>
   * Unlike static sizing which uses peak flow, this method evaluates the transient response and can
   * result in smaller (more realistic) PSV sizes.
   * </p>
   *
   * @param initialMass Initial fluid inventory [kg]
   * @param initialPressure Initial pressure [Pa]
   * @param setPressure PSV set pressure [Pa]
   * @param initialTemperature Initial temperature [K]
   * @param fireHeatInput Constant fire heat input [W]
   * @param vesselVolume Vessel volume [m³]
   * @param molecularWeight Molecular weight [kg/mol]
   * @param specificHeatRatio Cp/Cv ratio
   * @param compressibility Compressibility factor
   * @param heatCapacity Heat capacity [J/(kg*K)]
   * @param blowdownTime Target blowdown time [s] (typically 15 minutes = 900s per API 521)
   * @return PSV sizing result for fire case
   */
  public static PSVSizingResult dynamicFireSizing(double initialMass, double initialPressure,
      double setPressure, double initialTemperature, double fireHeatInput, double vesselVolume,
      double molecularWeight, double specificHeatRatio, double compressibility, double heatCapacity,
      double blowdownTime) {
    // API 521 fire case: 21% overpressure for first PSV, 16% for additional
    double overpressureFraction = 0.21;
    double relievingPressure = setPressure * (1.0 + overpressureFraction);

    // Estimate relieving temperature (assume some superheat from fire)
    // Simple estimate: T increases due to fire heat input during pressurization
    double deltaT = fireHeatInput * 60.0 / (initialMass * heatCapacity); // 1 minute heat-up
    double relievingTemperature = initialTemperature + deltaT;

    // Peak mass flow required to prevent further pressure rise
    // At relief: d(mU)/dt = Q_fire - mdot*h
    // For gas: approximately Q_fire = mdot * Cp * (T - T_ambient)
    // Simplified: mdot = Q_fire / (Cp * deltaT_superheat)
    double deltaT_superheat = 50.0; // Assume 50K superheat
    double peakMassFlow = fireHeatInput / (heatCapacity * deltaT_superheat);

    // Alternative: size for average flow to empty vessel in blowdown time
    double averageMassFlow = initialMass / blowdownTime;

    // Use the larger of peak and average requirements
    double designMassFlow = Math.max(peakMassFlow, averageMassFlow);

    // Atmospheric back pressure
    double backPressure = 101325.0;

    return calculateRequiredArea(designMassFlow, setPressure, overpressureFraction, backPressure,
        relievingTemperature, molecularWeight, compressibility, specificHeatRatio, false, false);
  }

  /**
   * Calculates the blowdown (reseat) pressure for a PSV.
   *
   * @param setPressure PSV set pressure [Pa]
   * @param blowdownPercent Blowdown percentage (typically 7-10%)
   * @return Blowdown/reseat pressure [Pa]
   */
  public static double calculateBlowdownPressure(double setPressure, double blowdownPercent) {
    return setPressure * (1.0 - blowdownPercent / 100.0);
  }

  /**
   * Calculates the flow coefficient Cv for a PSV at given conditions.
   *
   * <p>
   * This allows the PSV to be modeled as an equivalent control valve in process simulations.
   * </p>
   *
   * @param orificeArea Orifice area [m²]
   * @param dischargeCoefficient Discharge coefficient
   * @return Flow coefficient Cv [gpm/sqrt(psi)]
   */
  public static double calculateCv(double orificeArea, double dischargeCoefficient) {
    // Cv relationship for gas: roughly Cv = 24.6 * Cd * A[in²] for liquids
    // For gases, the relationship is more complex but this gives approximate sizing
    double A_in2 = orificeArea / 6.4516e-4;
    return 24.6 * dischargeCoefficient * A_in2;
  }

  /**
   * Validates PSV sizing against API 521 requirements.
   *
   * @param result PSV sizing result to validate
   * @param isFireCase true if this is a fire scenario
   * @return Validation message (empty if valid, otherwise describes issues)
   */
  public static String validateSizing(PSVSizingResult result, boolean isFireCase) {
    StringBuilder issues = new StringBuilder();

    // Check overpressure is appropriate
    if (isFireCase && result.getOverpressureFraction() < 0.21) {
      issues.append("Fire case should use 21% overpressure per API 521. ");
    }

    // Check back pressure
    if (result.getBackPressureFraction() > 0.5) {
      issues.append("High back pressure (>50%) - consider balanced-bellows PSV. ");
    }

    // Check selected orifice is adequate
    if (result.getSelectedAreaIn2() < result.getRequiredAreaIn2()) {
      issues.append("Selected orifice is undersized. ");
    }

    // Check Kb factor
    if (result.getBackPressureCorrectionFactor() < 0.5) {
      issues.append("Severe Kb derating - review back pressure system. ");
    }

    return issues.toString();
  }

  /**
   * Gets the next larger standard orifice size.
   *
   * @param currentOrifice Current orifice letter designation
   * @return Next larger orifice letter, or "T" if already at maximum
   */
  public static String getNextLargerOrifice(String currentOrifice) {
    for (int i = 0; i < STANDARD_ORIFICE_LETTERS.length - 1; i++) {
      if (STANDARD_ORIFICE_LETTERS[i].equals(currentOrifice)) {
        return STANDARD_ORIFICE_LETTERS[i + 1];
      }
    }
    return "T";
  }

  /**
   * Gets the orifice area for a standard API 520 orifice designation.
   *
   * @param orifice Orifice letter designation (D, E, F, G, H, J, K, L, M, N, P, Q, R, T)
   * @return Orifice area [m²]
   */
  public static double getStandardOrificeArea(String orifice) {
    for (int i = 0; i < STANDARD_ORIFICE_LETTERS.length; i++) {
      if (STANDARD_ORIFICE_LETTERS[i].equals(orifice)) {
        return STANDARD_ORIFICE_AREAS_IN2[i] * 6.4516e-4;
      }
    }
    throw new IllegalArgumentException("Unknown orifice designation: " + orifice);
  }

  // ============================================================================
  // LIQUID RELIEF SIZING - API 520 Section 5.8
  // ============================================================================

  /**
   * Result container for liquid PSV sizing calculations per API 520 Section 5.8.
   */
  public static final class LiquidPSVSizingResult {
    private final double requiredAreaM2;
    private final double requiredAreaIn2;
    private final double massFlowRate;
    private final double volumeFlowRate;
    private final String recommendedOrifice;
    private final double selectedAreaIn2;
    private final double kd;
    private final double kw;
    private final double kv;

    /**
     * Creates a liquid PSV sizing result.
     *
     * @param requiredAreaM2 Required orifice area [m2]
     * @param requiredAreaIn2 Required orifice area [in2]
     * @param massFlowRate Mass flow rate [kg/s]
     * @param volumeFlowRate Volume flow rate [m3/s]
     * @param recommendedOrifice Recommended standard orifice letter
     * @param selectedAreaIn2 Selected standard orifice area [in2]
     * @param kd Discharge coefficient
     * @param kw Back pressure correction factor
     * @param kv Viscosity correction factor
     */
    public LiquidPSVSizingResult(double requiredAreaM2, double requiredAreaIn2, double massFlowRate,
        double volumeFlowRate, String recommendedOrifice, double selectedAreaIn2, double kd,
        double kw, double kv) {
      this.requiredAreaM2 = requiredAreaM2;
      this.requiredAreaIn2 = requiredAreaIn2;
      this.massFlowRate = massFlowRate;
      this.volumeFlowRate = volumeFlowRate;
      this.recommendedOrifice = recommendedOrifice;
      this.selectedAreaIn2 = selectedAreaIn2;
      this.kd = kd;
      this.kw = kw;
      this.kv = kv;
    }

    /**
     * Gets the required orifice area in m2.
     *
     * @return required area [m2]
     */
    public double getRequiredAreaM2() {
      return requiredAreaM2;
    }

    /**
     * Gets the required orifice area in in2.
     *
     * @return required area [in2]
     */
    public double getRequiredAreaIn2() {
      return requiredAreaIn2;
    }

    /**
     * Gets the mass flow rate.
     *
     * @return mass flow rate [kg/s]
     */
    public double getMassFlowRate() {
      return massFlowRate;
    }

    /**
     * Gets the volume flow rate.
     *
     * @return volume flow rate [m3/s]
     */
    public double getVolumeFlowRate() {
      return volumeFlowRate;
    }

    /**
     * Gets the recommended standard orifice letter.
     *
     * @return orifice letter designation
     */
    public String getRecommendedOrifice() {
      return recommendedOrifice;
    }

    /**
     * Gets the selected standard orifice area in in2.
     *
     * @return selected area [in2]
     */
    public double getSelectedAreaIn2() {
      return selectedAreaIn2;
    }

    /**
     * Gets the discharge coefficient.
     *
     * @return discharge coefficient Kd
     */
    public double getDischargeCoefficient() {
      return kd;
    }

    /**
     * Gets the back pressure correction factor.
     *
     * @return back pressure correction Kw
     */
    public double getBackPressureCorrectionFactor() {
      return kw;
    }

    /**
     * Gets the viscosity correction factor.
     *
     * @return viscosity correction Kv
     */
    public double getViscosityCorrectionFactor() {
      return kv;
    }
  }

  /**
   * Calculates required PSV orifice area for liquid service per API 520 Section 5.8.
   *
   * <p>
   * Uses the API 520 liquid relief equation:
   * </p>
   *
   * <pre>
   * A = Q / (38 * Kd * Kw * Kv * Kp) * sqrt(G / (P1 - P2))
   * </pre>
   *
   * <p>
   * where Q is volume flow in US gpm, G is specific gravity, P1 is upstream relieving pressure
   * [psig], and P2 is back pressure [psig].
   * </p>
   *
   * @param volumeFlowRate Volume flow rate at relieving conditions [m3/s]
   * @param liquidDensity Liquid density at relieving conditions [kg/m3]
   * @param setPressure PSV set pressure [Pa absolute]
   * @param overpressureFraction Overpressure fraction (0.10 or 0.25 for fire)
   * @param backPressure Downstream/back pressure [Pa absolute]
   * @param viscosity Dynamic viscosity [Pa*s]
   * @param isBalancedBellows true for balanced-bellows PSV
   * @return Liquid PSV sizing result
   */
  public static LiquidPSVSizingResult calculateLiquidReliefArea(double volumeFlowRate,
      double liquidDensity, double setPressure, double overpressureFraction, double backPressure,
      double viscosity, boolean isBalancedBellows) {
    if (volumeFlowRate <= 0.0) {
      throw new IllegalArgumentException("Volume flow rate must be positive");
    }
    if (setPressure <= 0.0 || liquidDensity <= 0.0) {
      throw new IllegalArgumentException("Set pressure and density must be positive");
    }

    double waterDensity = 999.0;
    double specificGravity = liquidDensity / waterDensity;
    double relievingPressure = setPressure * (1.0 + overpressureFraction);

    // Convert to US customary for API 520 equation
    double QGpm = volumeFlowRate * 15850.3; // m3/s to US gpm
    double P1Psig = (relievingPressure - 101325.0) / 6894.76; // Pa to psig
    double P2Psig = (backPressure - 101325.0) / 6894.76;

    // Discharge coefficient for liquid
    double kd = 0.65;

    // Back pressure correction factor (Kw)
    double kw = 1.0;
    if (isBalancedBellows) {
      double bpRatio = backPressure / relievingPressure;
      if (bpRatio > 0.5) {
        kw = 1.0 - 0.5 * (bpRatio - 0.5);
        kw = Math.max(kw, 0.1);
      }
    }

    // Viscosity correction factor (Kv) per API 520 Figure 37
    // Reynolds number approach: first assume Kv=1, size, then correct
    double kv = 1.0;
    double dp = P1Psig - P2Psig;
    if (dp <= 0) {
      dp = 1.0;
    }
    double aInitial = QGpm / (38.0 * kd * kw * 1.0) * Math.sqrt(specificGravity / dp);

    if (viscosity > 0.0 && aInitial > 0.0) {
      double areaSi = aInitial * 6.4516e-4; // in2 to m2
      double equivalentDiameter = Math.sqrt(4.0 * areaSi / Math.PI);
      double velocity = volumeFlowRate / areaSi;
      double reynolds = liquidDensity * velocity * equivalentDiameter / viscosity;
      if (reynolds > 0 && reynolds < 100000) {
        // API 520 viscosity correction: Kv = (0.9935 + 2.878/Re^0.5 + 342.75/Re^1.5)^(-1)
        kv = 1.0 / (0.9935 + 2.878 / Math.sqrt(reynolds) + 342.75 / Math.pow(reynolds, 1.5));
        kv = Math.max(kv, 0.2);
        kv = Math.min(kv, 1.0);
      }
    }

    // Overpressure correction (Kp)
    double kp = 1.0;
    if (overpressureFraction < 0.25) {
      kp = 0.6 + 4.0 * overpressureFraction; // Linear from 0.6 at 0% to 1.0 at 10%
      kp = Math.min(kp, 1.0);
    }

    double aIn2 = QGpm / (38.0 * kd * kw * kv * kp) * Math.sqrt(specificGravity / dp);
    double aM2 = aIn2 * 6.4516e-4;
    double massFlowRate = volumeFlowRate * liquidDensity;

    // Select standard orifice
    String selectedOrifice = "T";
    double selectedAreaIn2 = STANDARD_ORIFICE_AREAS_IN2[STANDARD_ORIFICE_AREAS_IN2.length - 1];
    for (int i = 0; i < STANDARD_ORIFICE_AREAS_IN2.length; i++) {
      if (STANDARD_ORIFICE_AREAS_IN2[i] >= aIn2) {
        selectedOrifice = STANDARD_ORIFICE_LETTERS[i];
        selectedAreaIn2 = STANDARD_ORIFICE_AREAS_IN2[i];
        break;
      }
    }

    return new LiquidPSVSizingResult(aM2, aIn2, massFlowRate, volumeFlowRate, selectedOrifice,
        selectedAreaIn2, kd, kw, kv);
  }

  // ============================================================================
  // TWO-PHASE RELIEF SIZING - API 520 Appendix D
  // ============================================================================

  /**
   * Estimates the required relief area for two-phase (gas + liquid) service using the omega method
   * per API 520 Appendix D (Leung's omega method).
   *
   * <p>
   * The omega parameter characterizes the two-phase mixture compressibility. For flashing flow
   * through a nozzle, omega accounts for the vapour generation during depressurization.
   * </p>
   *
   * @param massFlowRate Total two-phase mass flow rate [kg/s]
   * @param setPressure PSV set pressure [Pa absolute]
   * @param overpressureFraction Overpressure fraction
   * @param backPressure Back pressure [Pa absolute]
   * @param inletTemperature Inlet temperature [K]
   * @param gasFraction Mass fraction of gas at inlet conditions
   * @param gasDensity Gas density at inlet [kg/m3]
   * @param liquidDensity Liquid density at inlet [kg/m3]
   * @param latentHeat Latent heat of vaporization [J/kg]
   * @param liquidCp Liquid heat capacity [J/(kg*K)]
   * @return Required orifice area [m2]
   */
  public static double calculateTwoPhaseReliefArea(double massFlowRate, double setPressure,
      double overpressureFraction, double backPressure, double inletTemperature, double gasFraction,
      double gasDensity, double liquidDensity, double latentHeat, double liquidCp) {
    double P0 = setPressure * (1.0 + overpressureFraction);

    // Omega parameter (Leung 1986)
    double vG = 1.0 / gasDensity;
    double vL = 1.0 / liquidDensity;
    double omega = gasFraction * vG / (gasFraction * vG + (1.0 - gasFraction) * vL)
        + liquidCp * inletTemperature * P0 * Math.pow(vG - vL, 2)
            / (Math.pow(latentHeat, 2) * (gasFraction * vG + (1.0 - gasFraction) * vL));

    // Critical pressure ratio
    double etaC;
    if (omega <= 1.0) {
      etaC = 0.55 + 0.217 * Math.log(omega);
    } else {
      etaC = 0.85 / (1.0 + omega);
    }
    etaC = Math.max(etaC, backPressure / P0);

    // Mass flux through nozzle at critical conditions
    double vIn = gasFraction * vG + (1.0 - gasFraction) * vL;
    double G2 = P0 / vIn * (2.0 * (omega * Math.log(1.0 / etaC) + (1.0 - omega) * (1.0 - etaC)));
    double massFlux = Math.sqrt(Math.max(G2, 0.0));

    // Discharge coefficient for two-phase
    double kd = 0.85;

    if (massFlux <= 0.0) {
      return 0.0;
    }
    return massFlowRate / (kd * massFlux);
  }

  // ============================================================================
  // API 521 FIRE HEAT INPUT
  // ============================================================================

  /**
   * Calculates fire heat input per API 521 Table 4 for wetted surface.
   *
   * <p>
   * Uses Q = C1 * F * Aws^alpha where C1 and alpha depend on drainage and fire-fighting facilities.
   * </p>
   *
   * @param wettedAreaM2 Wetted surface area [m2]
   * @param hasDrainage true if adequate drainage exists
   * @param hasFireFighting true if firefighting equipment available
   * @return Heat absorption rate [W]
   */
  public static double calculateAPI521FireHeatInput(double wettedAreaM2, boolean hasDrainage,
      boolean hasFireFighting) {
    double awsFt2 = wettedAreaM2 * 10.7639; // m2 to ft2
    double environmentFactor = 1.0;
    if (hasDrainage && hasFireFighting) {
      environmentFactor = 1.0;
    } else if (!hasDrainage) {
      environmentFactor = 1.0;
    }

    double qBtuHr;
    if (hasDrainage) {
      // API 521 adequate drainage: Q = 21000 * F * Aws^0.82
      qBtuHr = 21000.0 * environmentFactor * Math.pow(awsFt2, 0.82);
    } else {
      // API 521 no drainage: Q = 34500 * F * Aws^0.82
      qBtuHr = 34500.0 * environmentFactor * Math.pow(awsFt2, 0.82);
    }

    return qBtuHr * 0.29307107; // BTU/hr to W
  }
}

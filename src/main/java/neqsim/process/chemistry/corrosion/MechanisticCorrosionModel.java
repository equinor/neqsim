package neqsim.process.chemistry.corrosion;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.util.StandardsRegistry;
import neqsim.process.corrosion.NorsokM506CorrosionRate;

/**
 * Mechanistic CO2 corrosion model that combines a kinetic NORSOK M-506 baseline with Nesic-style
 * mass-transfer limitation and a Langmuir adsorption inhibitor isotherm.
 *
 * <p>
 * Three serial resistances control the observed corrosion rate of carbon steel under sweet (CO2)
 * service:
 *
 * <ol>
 * <li><b>Charge-transfer kinetics</b> — captured by the NORSOK M-506 (2005/2017) baseline rate
 * {@code CR_kinetic} which already accounts for fugacity, pH, scaling temperature and wall
 * shear.</li>
 * <li><b>Boundary-layer mass transfer</b> — described by the Berger-Hau correlation for Sherwood
 * number in turbulent pipe flow, {@code Sh = 0.0165 * Re^0.86 * Sc^0.33}, giving a mass-transfer
 * coefficient {@code k_m = Sh * D_CO2 / d}. The mass-transfer-limited rate is
 * {@code CR_MT = k_m * c_CO2 * 11.6 mm/yr per mol/L} (Nesic 2007).</li>
 * <li><b>Inhibitor surface coverage</b> — governed by a Langmuir adsorption isotherm (see
 * {@link LangmuirInhibitorIsotherm}).</li>
 * </ol>
 *
 * The mixed-control rate before inhibition is
 * 
 * <pre>
 * 1 / CR_mixed = 1 / CR_kinetic + 1 / CR_MT
 * </pre>
 * 
 * and after inhibitor coverage theta:
 * 
 * <pre>
 * CR_inhibited = CR_mixed * (1 - theta_max * theta)
 * </pre>
 *
 * <p>
 * Standards: NORSOK M-506 (2017), NACE SP0775 (corrosion coupon practice).
 *
 * @author ESOL
 * @version 1.0
 */
public class MechanisticCorrosionModel implements Serializable {

  private static final long serialVersionUID = 1000L;

  // Diffusion coefficient of CO2 in water at 25 C (m2/s)
  private static final double D_CO2_REF = 1.96e-9;
  // Conversion: 1 mol/(m2 s) Fe dissolution = 11.6 mm/yr (rho=7860 kg/m3, MM=55.85)
  private static final double FE_DISSOLUTION_MM_YR = 11.6;
  // Henry's constant for CO2 in water (bar*L/mol) at 25 C
  private static final double H_CO2_REF = 29.4;

  // Inputs
  private double temperatureC = 25.0;
  private double totalPressureBara = 1.0;
  private double co2MoleFraction = 0.0;
  private double h2sMoleFraction = 0.0;
  private double pH = -1.0; // -1 = use NORSOK equilibrium pH
  private double bicarbMgL = 0.0;
  private double ionicStrengthMolL = 0.0;
  private double velocityMs = 1.0;
  private double pipeDiameterM = 0.1;
  private double liquidDensityKgM3 = 1000.0;
  private double liquidViscosityPas = 1e-3;
  private double inhibitorDoseMgL = 0.0;
  private LangmuirInhibitorIsotherm inhibitor = new LangmuirInhibitorIsotherm();

  // Outputs
  private double kineticRateMmYr = 0.0;
  private double massTransferLimitedRateMmYr = 0.0;
  private double mixedControlRateMmYr = 0.0;
  private double inhibitedRateMmYr = 0.0;
  private double sherwoodNumber = 0.0;
  private double reynoldsNumber = 0.0;
  private double schmidtNumber = 0.0;
  private double inhibitorCoverage = 0.0;
  private double inhibitorEfficiency = 0.0;
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public MechanisticCorrosionModel() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets temperature in Celsius.
   *
   * @param tC temperature [C]
   * @return this for chaining
   */
  public MechanisticCorrosionModel setTemperatureCelsius(double tC) {
    this.temperatureC = tC;
    return this;
  }

  /**
   * Sets total system pressure in bara.
   *
   * @param p pressure [bara]
   * @return this for chaining
   */
  public MechanisticCorrosionModel setTotalPressureBara(double p) {
    this.totalPressureBara = p;
    return this;
  }

  /**
   * Sets CO2 and H2S mole fractions in the gas phase.
   *
   * @param co2 CO2 mole fraction
   * @param h2s H2S mole fraction
   * @return this for chaining
   */
  public MechanisticCorrosionModel setGasComposition(double co2, double h2s) {
    this.co2MoleFraction = co2;
    this.h2sMoleFraction = h2s;
    return this;
  }

  /**
   * Sets aqueous-phase chemistry.
   *
   * @param pH pH value (use -1 to compute equilibrium pH from CO2)
   * @param bicarbMgL bicarbonate concentration [mg/L]
   * @param ionicStrength ionic strength [mol/L]
   * @return this for chaining
   */
  public MechanisticCorrosionModel setWaterChemistry(double pH, double bicarbMgL,
      double ionicStrength) {
    this.pH = pH;
    this.bicarbMgL = bicarbMgL;
    this.ionicStrengthMolL = ionicStrength;
    return this;
  }

  /**
   * Sets pipe and flow geometry.
   *
   * @param velocityMs flow velocity [m/s]
   * @param diameterM pipe ID [m]
   * @param densityKgM3 liquid density [kg/m3]
   * @param viscosityPas dynamic viscosity [Pa.s]
   * @return this for chaining
   */
  public MechanisticCorrosionModel setFlow(double velocityMs, double diameterM, double densityKgM3,
      double viscosityPas) {
    this.velocityMs = velocityMs;
    this.pipeDiameterM = diameterM;
    this.liquidDensityKgM3 = densityKgM3;
    this.liquidViscosityPas = viscosityPas;
    return this;
  }

  /**
   * Sets the inhibitor model and dose.
   *
   * @param inhibitor Langmuir isotherm (use {@code null} to keep default imidazoline)
   * @param doseMgL inhibitor dose [mg/L]
   * @return this for chaining
   */
  public MechanisticCorrosionModel setInhibitor(LangmuirInhibitorIsotherm inhibitor,
      double doseMgL) {
    if (inhibitor != null) {
      this.inhibitor = inhibitor;
    }
    this.inhibitorDoseMgL = doseMgL;
    return this;
  }

  // ─── Calculation ────────────────────────────────────────

  /**
   * Runs the full mechanistic calculation: NORSOK kinetic baseline → mass-transfer-limited rate →
   * mixed control → Langmuir inhibition.
   *
   * @return this for chaining
   */
  public MechanisticCorrosionModel evaluate() {
    // 1. NORSOK kinetic baseline
    NorsokM506CorrosionRate norsok = new NorsokM506CorrosionRate();
    norsok.setTemperatureCelsius(temperatureC);
    norsok.setTotalPressureBara(totalPressureBara);
    norsok.setCO2MoleFraction(co2MoleFraction);
    norsok.setH2SMoleFraction(h2sMoleFraction);
    if (pH > 0.0) {
      norsok.setActualPH(pH);
    }
    norsok.setBicarbonateConcentrationMgL(bicarbMgL);
    norsok.setIonicStrengthMolL(ionicStrengthMolL);
    norsok.setFlowVelocityMs(velocityMs);
    norsok.setPipeDiameterM(pipeDiameterM);
    norsok.setLiquidDensityKgM3(liquidDensityKgM3);
    norsok.setLiquidViscosityPas(liquidViscosityPas);
    norsok.setInhibitorEfficiency(0.0); // we apply Langmuir externally
    norsok.calculate();
    kineticRateMmYr = norsok.getCorrectedCorrosionRate();

    // 2. Mass-transfer limit (Nesic / Berger-Hau)
    double tK = temperatureC + 273.15;
    double dCo2 = D_CO2_REF * Math.pow(tK / 298.15, 1.5);
    reynoldsNumber = liquidDensityKgM3 * velocityMs * pipeDiameterM / liquidViscosityPas;
    schmidtNumber = liquidViscosityPas / (liquidDensityKgM3 * dCo2);
    if (reynoldsNumber > 4000.0) {
      sherwoodNumber = 0.0165 * Math.pow(reynoldsNumber, 0.86) * Math.pow(schmidtNumber, 0.33);
    } else {
      // Laminar fully developed Sh = 3.66
      sherwoodNumber = 3.66;
    }
    double kM = sherwoodNumber * dCo2 / pipeDiameterM; // m/s
    double pco2 = co2MoleFraction * totalPressureBara;
    double cco2BulkMolL = pco2 / H_CO2_REF; // simple Henry
    massTransferLimitedRateMmYr = kM * cco2BulkMolL * 1000.0 * FE_DISSOLUTION_MM_YR; // mm/yr

    // 3. Mixed control (serial resistances)
    if (kineticRateMmYr <= 0.0) {
      mixedControlRateMmYr = 0.0;
    } else if (massTransferLimitedRateMmYr <= 0.0) {
      mixedControlRateMmYr = kineticRateMmYr;
    } else {
      mixedControlRateMmYr = 1.0 / (1.0 / kineticRateMmYr + 1.0 / massTransferLimitedRateMmYr);
    }

    // 4. Langmuir inhibition
    inhibitorCoverage = inhibitor.getCoverage(inhibitorDoseMgL, temperatureC);
    inhibitorEfficiency = inhibitor.getEfficiency(inhibitorDoseMgL, temperatureC);
    inhibitedRateMmYr = mixedControlRateMmYr * (1.0 - inhibitorEfficiency);

    evaluated = true;
    return this;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the NORSOK M-506 kinetic baseline rate.
   *
   * @return rate [mm/yr]
   */
  public double getKineticRateMmYr() {
    return kineticRateMmYr;
  }

  /**
   * Returns the mass-transfer-limited rate (Nesic / Berger-Hau).
   *
   * @return rate [mm/yr]
   */
  public double getMassTransferLimitedRateMmYr() {
    return massTransferLimitedRateMmYr;
  }

  /**
   * Returns the mixed-control rate before inhibition.
   *
   * @return rate [mm/yr]
   */
  public double getMixedControlRateMmYr() {
    return mixedControlRateMmYr;
  }

  /**
   * Returns the final inhibited rate.
   *
   * @return rate [mm/yr]
   */
  public double getInhibitedRateMmYr() {
    return inhibitedRateMmYr;
  }

  /**
   * Returns the Sherwood number from Berger-Hau (or 3.66 if laminar).
   *
   * @return Sherwood number
   */
  public double getSherwoodNumber() {
    return sherwoodNumber;
  }

  /**
   * Returns the Reynolds number.
   *
   * @return Reynolds number
   */
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /**
   * Returns the Schmidt number.
   *
   * @return Schmidt number
   */
  public double getSchmidtNumber() {
    return schmidtNumber;
  }

  /**
   * Returns the Langmuir surface coverage.
   *
   * @return theta in [0,1]
   */
  public double getInhibitorCoverage() {
    return inhibitorCoverage;
  }

  /**
   * Returns the inhibition efficiency theta_max * theta.
   *
   * @return efficiency in [0, thetaMax]
   */
  public double getInhibitorEfficiency() {
    return inhibitorEfficiency;
  }

  /**
   * Returns true once {@link #evaluate()} has been invoked.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns standards used by this model.
   *
   * @return list of standard reference maps
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(StandardsRegistry.NORSOK_M506,
        StandardsRegistry.NACE_SP0775);
  }

  /**
   * Returns a structured map representation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("temperatureC", temperatureC);
    map.put("totalPressureBara", totalPressureBara);
    map.put("co2MoleFraction", co2MoleFraction);
    map.put("velocityMs", velocityMs);
    map.put("pipeDiameterM", pipeDiameterM);
    map.put("inhibitorDoseMgL", inhibitorDoseMgL);
    map.put("reynoldsNumber", reynoldsNumber);
    map.put("schmidtNumber", schmidtNumber);
    map.put("sherwoodNumber", sherwoodNumber);
    map.put("kineticRateMmYr", kineticRateMmYr);
    map.put("massTransferLimitedRateMmYr", massTransferLimitedRateMmYr);
    map.put("mixedControlRateMmYr", mixedControlRateMmYr);
    map.put("inhibitorCoverage", inhibitorCoverage);
    map.put("inhibitorEfficiency", inhibitorEfficiency);
    map.put("inhibitedRateMmYr", inhibitedRateMmYr);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns a JSON representation.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}

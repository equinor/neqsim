package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Couples a rigorous electrolyte thermodynamic model into the {@link NorsokM506CorrosionRate} standard model.
 *
 * <p>
 * The bare {@link NorsokM506CorrosionRate} model estimates the in-situ pH of the water phase from a correlation
 * (Henry's law constant plus the apparent first dissociation constant of carbonic acid) and needs the user to supply
 * bicarbonate concentration and ionic strength separately. That correlation is a screening approximation. When a
 * calibrated electrolyte equation of state is available (for example {@code SystemElectrolyteCPAstatoil} with chemical
 * reaction equilibrium), the aqueous speciation — and therefore the in-situ pH — is known rigorously and consistently
 * with the phase behaviour.
 * </p>
 *
 * <p>
 * This bridge takes an electrolyte fluid, runs an isolated TP flash on a clone (so the caller's fluid is not mutated),
 * extracts the operating state, and feeds it into a {@link NorsokM506CorrosionRate} instance:
 * </p>
 * <ul>
 * <li>temperature and total pressure from the fluid state,</li>
 * <li>CO2 (and H2S) mole fraction from the non-aqueous phase,</li>
 * <li>the rigorous in-situ pH from the aqueous phase via {@link SystemInterface#getpH()} — this is passed as an
 * explicit override so it takes precedence over the model's own pH correlation,</li>
 * <li>the bicarbonate concentration extracted from the aqueous HCO3- speciation (informational; the pH override already
 * embeds its effect).</li>
 * </ul>
 *
 * <p>
 * This is the NORSOK M-506 analogue of {@link neqsim.pvtsimulation.flowassurance.CO2CorrosionAnalyzer}, which performs
 * the same electrolyte-to-corrosion coupling for the de Waard-Milliams model. It lets an investigation compute an
 * EOS-consistent CO2 corrosion rate directly from a produced-water brine composition, rather than from hand-entered pH.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 60.0, 100.0);
 * fluid.addComponent("CO2", 0.02);
 * fluid.addComponent("water", 0.5);
 * fluid.addComponent("methane", 0.48);
 * fluid.addComponent("Na+", 0.01);
 * fluid.addComponent("Cl-", 0.01);
 * fluid.chemicalReactionInit();
 * fluid.createDatabase(true);
 * fluid.setMixingRule(10);
 * fluid.setMultiPhaseCheck(true);
 *
 * NorsokM506ElectrolyteBridge bridge = new NorsokM506ElectrolyteBridge(fluid);
 * bridge.setFlowVelocityMs(3.0);
 * bridge.setPipeDiameterM(0.254);
 * bridge.run();
 *
 * double rate = bridge.getModel().getCorrectedCorrosionRate(); // mm/yr
 * double pH = bridge.getInSituPH();
 * boolean rigorous = bridge.isPHFromElectrolyteModel();
 * String json = bridge.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM506CorrosionRate
 * @see neqsim.pvtsimulation.flowassurance.CO2CorrosionAnalyzer
 */
public class NorsokM506ElectrolyteBridge implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Molar mass of the bicarbonate ion in g/mol, used to report HCO3- in mg/L. */
  private static final double MM_HCO3_G_MOL = 61.02;

  /** Aqueous water density assumption in kg/L used to convert aqueous mole fractions to mg/L. */
  private static final double WATER_DENSITY_KG_L = 1.0;

  /** Molar mass of water in g/mol, used to estimate aqueous solvent moles per litre. */
  private static final double MM_WATER_G_MOL = 18.015;

  // --- Inputs ---

  /** The electrolyte fluid to analyse (not mutated; a clone is flashed internally). */
  private transient SystemInterface fluid;

  /** Flow velocity in m/s (passed through to the corrosion model). */
  private double flowVelocityMs = 2.0;

  /** Pipe internal diameter in metres (passed through to the corrosion model). */
  private double pipeDiameterM = 0.254;

  /** Chemical inhibitor efficiency (0.0 = none, 1.0 = perfect). */
  private double inhibitorEfficiency = 0.0;

  /** Glycol (MEG/DEG) weight fraction in the aqueous phase (0.0 to 1.0). */
  private double glycolWeightFraction = 0.0;

  // --- Extracted state ---

  /** Temperature extracted from the fluid, in degrees Celsius. */
  private double temperatureC = Double.NaN;

  /** Total pressure extracted from the fluid, in bara. */
  private double totalPressureBara = Double.NaN;

  /** CO2 mole fraction in the non-aqueous phase. */
  private double co2MoleFraction = 0.0;

  /** H2S mole fraction in the non-aqueous phase. */
  private double h2sMoleFraction = 0.0;

  /** Rigorous in-situ pH of the aqueous phase (NaN if no free water). */
  private double inSituPH = Double.NaN;

  /** Bicarbonate concentration extracted from aqueous speciation, in mg/L. */
  private double bicarbonateMgL = 0.0;

  /** Whether a free aqueous (water) phase was found in the flashed fluid. */
  private boolean freeWaterPresent = false;

  /** Whether the pH fed to the corrosion model came from the electrolyte model (true) or the correlation. */
  private boolean pHFromElectrolyteModel = false;

  // --- Result ---

  /** The configured and calculated corrosion model. */
  private NorsokM506CorrosionRate model;

  /** Whether {@link #run()} has completed. */
  private boolean hasBeenRun = false;

  /**
   * Constructs a bridge for the supplied electrolyte fluid.
   *
   * @param fluid an electrolyte thermodynamic system (for example {@code SystemElectrolyteCPAstatoil}); must not be
   * null
   */
  public NorsokM506ElectrolyteBridge(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    this.fluid = fluid;
  }

  /**
   * Sets the flow velocity forwarded to the corrosion model.
   *
   * @param velocityMs flow velocity in m/s (valid range: 0 to 30)
   */
  public void setFlowVelocityMs(double velocityMs) {
    this.flowVelocityMs = Math.max(0.0, velocityMs);
    this.hasBeenRun = false;
  }

  /**
   * Sets the pipe internal diameter forwarded to the corrosion model.
   *
   * @param diameterM pipe inner diameter in metres (valid range: 0.01 to 2.0)
   */
  public void setPipeDiameterM(double diameterM) {
    this.pipeDiameterM = Math.max(0.001, diameterM);
    this.hasBeenRun = false;
  }

  /**
   * Sets the chemical inhibitor efficiency forwarded to the corrosion model.
   *
   * @param efficiency inhibitor efficiency factor (0.0 = no inhibitor, 1.0 = perfect)
   */
  public void setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
    this.hasBeenRun = false;
  }

  /**
   * Sets the glycol (MEG/DEG) weight fraction forwarded to the corrosion model.
   *
   * @param weightFraction glycol weight fraction (0.0 to 1.0)
   */
  public void setGlycolWeightFraction(double weightFraction) {
    this.glycolWeightFraction = Math.max(0.0, Math.min(1.0, weightFraction));
    this.hasBeenRun = false;
  }

  /**
   * Runs the coupled analysis: flashes a clone of the electrolyte fluid, extracts the in-situ pH and gas composition,
   * and calculates the NORSOK M-506 corrosion rate with the rigorous pH.
   *
   * <p>
   * The caller's fluid object is not modified; the flash is performed on {@link SystemInterface#clone()}.
   * </p>
   */
  public void run() {
    SystemInterface work = fluid.clone();
    // Re-initialise chemical reaction equilibrium on the clone. clone() does not always carry the
    // chemical reaction operations, and without them the aqueous carbonic-acid dissociation is not
    // solved, which yields an unphysically high (basic) pH for a CO2 brine.
    boolean waterPresent = work.getPhase(0).hasComponent("water");
    boolean acidGasPresent = work.getPhase(0).hasComponent("CO2") || work.getPhase(0).hasComponent("H2S");
    if (waterPresent && acidGasPresent) {
      work.chemicalReactionInit();
    }
    work.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    ops.TPflash();
    work.initProperties();

    temperatureC = work.getTemperature() - 273.15;
    totalPressureBara = work.getPressure();

    co2MoleFraction = extractGasMoleFraction(work, "CO2");
    h2sMoleFraction = extractGasMoleFraction(work, "H2S");

    freeWaterPresent = work.hasPhaseType(PhaseType.AQUEOUS);
    if (freeWaterPresent) {
      inSituPH = work.getPhase(PhaseType.AQUEOUS).getpH();
      bicarbonateMgL = extractBicarbonateMgL(work);
    } else {
      inSituPH = Double.NaN;
      bicarbonateMgL = 0.0;
    }

    model = new NorsokM506CorrosionRate();
    model.setTemperatureCelsius(temperatureC);
    model.setTotalPressureBara(totalPressureBara);
    model.setCO2MoleFraction(co2MoleFraction);
    model.setH2SMoleFraction(h2sMoleFraction);
    model.setFlowVelocityMs(flowVelocityMs);
    model.setPipeDiameterM(pipeDiameterM);
    model.setInhibitorEfficiency(inhibitorEfficiency);
    model.setGlycolWeightFraction(glycolWeightFraction);
    model.setBicarbonateConcentrationMgL(bicarbonateMgL);

    if (freeWaterPresent && !Double.isNaN(inSituPH) && inSituPH > 0.0) {
      // Rigorous in-situ pH overrides the model's own correlation.
      model.setActualPH(inSituPH);
      pHFromElectrolyteModel = true;
    } else {
      // Fall back to the model's built-in equilibrium pH correlation.
      model.setActualPH(-1.0);
      pHFromElectrolyteModel = false;
    }

    model.calculate();
    hasBeenRun = true;
  }

  /**
   * Extracts the mole fraction of a component in the first non-aqueous phase (gas or oil/dense).
   *
   * @param system the flashed fluid
   * @param componentName the component name to look up (for example "CO2")
   * @return the mole fraction in the non-aqueous phase, or 0.0 if absent
   */
  private double extractGasMoleFraction(SystemInterface system, String componentName) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        continue;
      }
      if (system.getPhase(i).hasComponent(componentName)) {
        return system.getPhase(i).getComponent(componentName).getx();
      }
    }
    return 0.0;
  }

  /**
   * Estimates the bicarbonate concentration in the aqueous phase in mg/L from the HCO3- mole fraction.
   *
   * <p>
   * The estimate uses the aqueous water mole fraction to approximate the solvent mass per litre, then converts the
   * HCO3- mole fraction to a mass concentration. This is an informational output; the pH override already embeds the
   * rigorous carbonate speciation.
   * </p>
   *
   * @param system the flashed fluid
   * @return bicarbonate concentration in mg/L, or 0.0 if HCO3- or water is absent
   */
  private double extractBicarbonateMgL(SystemInterface system) {
    if (!system.hasPhaseType(PhaseType.AQUEOUS)) {
      return 0.0;
    }
    if (!system.getPhase(PhaseType.AQUEOUS).hasComponent("HCO3-")
        || !system.getPhase(PhaseType.AQUEOUS).hasComponent("water")) {
      return 0.0;
    }
    double xHco3 = system.getPhase(PhaseType.AQUEOUS).getComponent("HCO3-").getx();
    double xWater = system.getPhase(PhaseType.AQUEOUS).getComponent("water").getx();
    if (xWater <= 0.0) {
      return 0.0;
    }
    // Moles of water solvent per litre of aqueous phase (approximate, dilute-brine assumption).
    double waterMolPerL = WATER_DENSITY_KG_L * 1000.0 / MM_WATER_G_MOL;
    double hco3MolPerL = waterMolPerL * (xHco3 / xWater);
    return hco3MolPerL * MM_HCO3_G_MOL * 1000.0; // mol/L -> mg/L
  }

  /**
   * Gets the configured and calculated corrosion model.
   *
   * @return the {@link NorsokM506CorrosionRate} instance (null until {@link #run()} is called)
   */
  public NorsokM506CorrosionRate getModel() {
    return model;
  }

  /**
   * Gets the rigorous in-situ pH extracted from the aqueous phase.
   *
   * @return in-situ pH, or NaN if no free water phase was present
   */
  public double getInSituPH() {
    return inSituPH;
  }

  /**
   * Gets the CO2 mole fraction extracted from the non-aqueous phase.
   *
   * @return CO2 mole fraction (0 to 1)
   */
  public double getCo2MoleFraction() {
    return co2MoleFraction;
  }

  /**
   * Gets the H2S mole fraction extracted from the non-aqueous phase.
   *
   * @return H2S mole fraction (0 to 1)
   */
  public double getH2sMoleFraction() {
    return h2sMoleFraction;
  }

  /**
   * Gets the bicarbonate concentration extracted from aqueous speciation.
   *
   * @return bicarbonate concentration in mg/L
   */
  public double getBicarbonateMgL() {
    return bicarbonateMgL;
  }

  /**
   * Indicates whether a free aqueous (water) phase was present in the flashed fluid.
   *
   * @return true if free water was present, false otherwise
   */
  public boolean isFreeWaterPresent() {
    return freeWaterPresent;
  }

  /**
   * Indicates whether the pH fed to the corrosion model came from the electrolyte model.
   *
   * @return true if the rigorous in-situ pH was used, false if the built-in correlation was used
   */
  public boolean isPHFromElectrolyteModel() {
    return pHFromElectrolyteModel;
  }

  /**
   * Serialises the extracted state and the corrosion result to a JSON string.
   *
   * @return a pretty-printed JSON representation of the bridge result
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("hasBeenRun", hasBeenRun);
    map.put("freeWaterPresent", freeWaterPresent);
    map.put("pHFromElectrolyteModel", pHFromElectrolyteModel);
    map.put("temperatureC", temperatureC);
    map.put("totalPressureBara", totalPressureBara);
    map.put("co2MoleFraction", co2MoleFraction);
    map.put("h2sMoleFraction", h2sMoleFraction);
    map.put("inSituPH", inSituPH);
    map.put("bicarbonateMgL", bicarbonateMgL);
    if (model != null) {
      map.put("correctedCorrosionRateMmYr", model.getCorrectedCorrosionRate());
    }
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(map);
  }
}

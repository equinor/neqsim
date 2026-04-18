package neqsim.util.nucleation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Multicomponent nucleation model for predicting droplet formation from supersaturated
 * multicomponent gas mixtures.
 *
 * <p>
 * This class handles the common case in natural gas processing where multiple heavy hydrocarbon
 * components co-condense simultaneously. Two approaches are supported:
 * </p>
 * <ul>
 * <li><b>Pseudocomponent method</b>: Lumps all condensable components into a single effective
 * species with mole-fraction-weighted properties (MW, density, surface tension). This is the
 * default and most robust approach.</li>
 * <li><b>Independent nucleation</b>: Treats each condensable component independently and sums
 * nucleation rates. Less rigorous but useful for identifying the dominant nucleating species.</li>
 * </ul>
 *
 * <p>
 * The model requires a flashed and initialized NeqSim thermodynamic system with at least one gas
 * phase and one liquid phase. Components are classified as "condensable" if their liquid mole
 * fraction exceeds a threshold.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * // Flash the system first
 * ThermodynamicOperations ops = new ThermodynamicOperations(system);
 * ops.TPflash();
 * system.initProperties();
 *
 * // Create multicomponent nucleation model
 * MulticomponentNucleation mcn = new MulticomponentNucleation(system);
 * mcn.setHeterogeneous(true, 60.0); // pipe wall nucleation
 * mcn.setResidenceTime(0.5);
 * mcn.calculate();
 *
 * double totalRate = mcn.getTotalNucleationRate();
 * double diameter = mcn.getMeanParticleDiameter();
 * String dominant = mcn.getDominantComponent();
 * }
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Reiss, H. (1950). The kinetics of phase transitions in binary systems. J. Chem. Phys. 18,
 * 840-848.</li>
 * <li>Stauffer, D. (1976). Kinetic theory of two-component nucleation. J. Aerosol Sci. 7,
 * 319-333.</li>
 * <li>Wilemski, G. (1984). Composition of the critical nucleus in multicomponent vapor nucleation.
 * J. Chem. Phys. 80, 1370-1372.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class MulticomponentNucleation {

  /**
   * Enumeration of multicomponent nucleation approaches.
   */
  public enum NucleationMode {
    /** Lump condensables into single pseudocomponent. */
    PSEUDOCOMPONENT,
    /** Treat each condensable independently. */
    INDEPENDENT
  }

  /** The NeqSim thermodynamic system (must be flashed and initialized). */
  private SystemInterface system;

  /** Nucleation approach mode. */
  private NucleationMode mode = NucleationMode.PSEUDOCOMPONENT;

  /** Minimum liquid mole fraction to classify a component as condensable. */
  private double condensableThreshold = 1.0e-6;

  /** Whether to use heterogeneous nucleation. */
  private boolean heterogeneous = false;

  /** Contact angle for heterogeneous nucleation in degrees. */
  private double contactAngleDegrees = 90.0;

  /** Residence time in seconds. */
  private double residenceTime = 1.0;

  /** List of condensable component indices. */
  private List<Integer> condensableIndices = new ArrayList<Integer>();

  /** List of condensable component names. */
  private List<String> condensableNames = new ArrayList<String>();

  /** Per-component supersaturation ratios. */
  private List<Double> componentSupersaturations = new ArrayList<Double>();

  /** Per-component nucleation rates (independent mode). */
  private List<Double> componentNucleationRates = new ArrayList<Double>();

  /** Per-component condensation fractions (mole-fraction contribution to condensation). */
  private List<Double> componentCondensationFractions = new ArrayList<Double>();

  /** The pseudocomponent CNT model (used in PSEUDOCOMPONENT mode). */
  private ClassicalNucleationTheory pseudoCNT;

  /** Per-component CNT models (used in INDEPENDENT mode). */
  private List<ClassicalNucleationTheory> componentCNTs =
      new ArrayList<ClassicalNucleationTheory>();

  /** Total nucleation rate in particles/(m3*s). */
  private double totalNucleationRate = 0.0;

  /** Mean particle diameter in m. */
  private double meanParticleDiameter = 0.0;

  /** Name of the dominant condensing component. */
  private String dominantComponent = "";

  /** Effective pseudocomponent molecular weight in kg/mol. */
  private double effectiveMW = 0.0;

  /** Effective pseudocomponent density in kg/m3. */
  private double effectiveDensity = 0.0;

  /** Effective pseudocomponent surface tension in N/m. */
  private double effectiveSurfaceTension = 0.0;

  /** Effective supersaturation ratio. */
  private double effectiveSupersaturation = 1.0;

  /** Whether calculation has been performed. */
  private boolean calculated = false;

  /**
   * Creates a multicomponent nucleation model from a NeqSim thermodynamic system.
   *
   * <p>
   * The system must have been flashed (TPflash) and properties initialized (initProperties) before
   * calling this constructor. The system should have at least two phases (gas + liquid) for
   * meaningful nucleation analysis.
   * </p>
   *
   * @param system the NeqSim thermodynamic system
   */
  public MulticomponentNucleation(SystemInterface system) {
    this.system = system;
  }

  /**
   * Sets the nucleation approach mode.
   *
   * @param mode PSEUDOCOMPONENT or INDEPENDENT
   */
  public void setMode(NucleationMode mode) {
    this.mode = mode;
    this.calculated = false;
  }

  /**
   * Gets the current nucleation approach mode.
   *
   * @return the nucleation mode
   */
  public NucleationMode getMode() {
    return mode;
  }

  /**
   * Sets the minimum liquid mole fraction threshold for classifying a component as condensable.
   *
   * @param threshold minimum liquid mole fraction (default 1e-6)
   */
  public void setCondensableThreshold(double threshold) {
    this.condensableThreshold = threshold;
    this.calculated = false;
  }

  /**
   * Enables or disables heterogeneous nucleation with a specified contact angle.
   *
   * @param isHeterogeneous true to enable heterogeneous nucleation
   * @param angleDegrees contact angle in degrees (0 to 180)
   */
  public void setHeterogeneous(boolean isHeterogeneous, double angleDegrees) {
    this.heterogeneous = isHeterogeneous;
    this.contactAngleDegrees = angleDegrees;
    this.calculated = false;
  }

  /**
   * Sets the residence time in the supersaturated zone.
   *
   * @param timeSeconds residence time in seconds
   */
  public void setResidenceTime(double timeSeconds) {
    this.residenceTime = timeSeconds;
    this.calculated = false;
  }

  /**
   * Performs the multicomponent nucleation calculation.
   *
   * <p>
   * Steps:
   * </p>
   * <ol>
   * <li>Identify condensable components from gas-liquid fugacity comparison</li>
   * <li>Compute per-component supersaturation from fugacity ratio</li>
   * <li>Either create pseudocomponent (PSEUDOCOMPONENT mode) or per-component CNT models
   * (INDEPENDENT mode)</li>
   * <li>Run nucleation calculations</li>
   * <li>Determine dominant component and total nucleation rate</li>
   * </ol>
   */
  public void calculate() {
    identifyCondensableComponents();

    if (condensableIndices.isEmpty()) {
      resetResults();
      calculated = true;
      return;
    }

    if (mode == NucleationMode.PSEUDOCOMPONENT) {
      calculatePseudocomponent();
    } else {
      calculateIndependent();
    }

    calculated = true;
  }

  /**
   * Identifies condensable components by comparing fugacities between gas and liquid phases.
   */
  private void identifyCondensableComponents() {
    condensableIndices.clear();
    condensableNames.clear();
    componentSupersaturations.clear();
    componentCondensationFractions.clear();

    if (system.getNumberOfPhases() < 2) {
      return;
    }

    int gasPhaseIndex = -1;
    int liquidPhaseIndex = -1;

    try {
      gasPhaseIndex = system.getPhaseNumberOfPhase("gas");
    } catch (Exception e) {
      return;
    }

    try {
      liquidPhaseIndex = system.getPhaseNumberOfPhase("oil");
    } catch (Exception e) {
      // Try aqueous
    }
    if (liquidPhaseIndex < 0) {
      try {
        liquidPhaseIndex = system.getPhaseNumberOfPhase("aqueous");
      } catch (Exception e) {
        return;
      }
    }

    if (gasPhaseIndex < 0 || liquidPhaseIndex < 0) {
      return;
    }

    PhaseInterface gasPhase = system.getPhase(gasPhaseIndex);
    PhaseInterface liquidPhase = system.getPhase(liquidPhaseIndex);
    int numComp = gasPhase.getNumberOfComponents();

    double totalLiquidFraction = 0.0;

    for (int i = 0; i < numComp; i++) {
      double xLiq = liquidPhase.getComponent(i).getx();

      if (xLiq > condensableThreshold) {
        // Compute supersaturation from fugacity ratio
        double fugGas = gasPhase.getFugacity(i);
        double fugLiq = liquidPhase.getFugacity(i);

        double supersaturation = 1.0;
        if (fugLiq > 0.0 && fugGas > 0.0) {
          supersaturation = fugGas / fugLiq;
        }

        condensableIndices.add(i);
        condensableNames.add(gasPhase.getComponent(i).getComponentName());
        componentSupersaturations.add(supersaturation);
        totalLiquidFraction += xLiq;
      }
    }

    // Calculate condensation fraction (each component's contribution)
    for (int idx = 0; idx < condensableIndices.size(); idx++) {
      int i = condensableIndices.get(idx);
      double xLiq = liquidPhase.getComponent(i).getx();
      if (totalLiquidFraction > 0.0) {
        componentCondensationFractions.add(xLiq / totalLiquidFraction);
      } else {
        componentCondensationFractions.add(1.0 / condensableIndices.size());
      }
    }
  }

  /**
   * Calculates nucleation using the pseudocomponent approach.
   *
   * <p>
   * Creates an effective single component with mole-fraction-weighted properties from all
   * condensable components, then delegates to ClassicalNucleationTheory.
   * </p>
   */
  private void calculatePseudocomponent() {
    PhaseInterface gasPhase = system.getPhase(system.getPhaseNumberOfPhase("gas"));
    int liquidPhaseIndex;
    try {
      liquidPhaseIndex = system.getPhaseNumberOfPhase("oil");
    } catch (Exception e) {
      liquidPhaseIndex = system.getPhaseNumberOfPhase("aqueous");
    }
    PhaseInterface liquidPhase = system.getPhase(liquidPhaseIndex);

    // Compute mole-fraction-weighted pseudocomponent properties
    effectiveMW = 0.0;
    effectiveDensity = 0.0;
    effectiveSupersaturation = 0.0;
    double totalWeight = 0.0;

    for (int idx = 0; idx < condensableIndices.size(); idx++) {
      int i = condensableIndices.get(idx);
      double w = componentCondensationFractions.get(idx);
      double mw = gasPhase.getComponent(i).getMolarMass();
      double tc = gasPhase.getComponent(i).getTC();
      double pc = gasPhase.getComponent(i).getPC() * 1e5; // bara to Pa

      effectiveMW += w * mw;

      // Estimate density from critical properties
      double rhoC = pc * mw / (ClassicalNucleationTheory.R_GAS * tc);
      double tr = Math.min(system.getTemperature() / tc, 0.99);
      double rhoEst = rhoC * (1.0 + 0.85 * (1.0 - tr));
      effectiveDensity += w * rhoEst;

      // Weighted supersaturation (geometric mean)
      double si = componentSupersaturations.get(idx);
      if (si > 1.0) {
        effectiveSupersaturation += w * Math.log(si);
      }
      totalWeight += w;
    }

    if (totalWeight > 0.0) {
      effectiveSupersaturation = Math.exp(effectiveSupersaturation / totalWeight);
    } else {
      effectiveSupersaturation = 1.0;
    }

    // Get surface tension from system interphase properties
    try {
      effectiveSurfaceTension = system.getInterphaseProperties()
          .getSurfaceTension(system.getPhaseNumberOfPhase("gas"), liquidPhaseIndex);
    } catch (Exception e) {
      // Estimate from Eotvos
      effectiveSurfaceTension = 0.025; // Typical hydrocarbon value
    }

    // Create pseudocomponent CNT
    pseudoCNT =
        new ClassicalNucleationTheory(effectiveMW, effectiveDensity, effectiveSurfaceTension);
    pseudoCNT.setSubstanceName("Pseudocomponent (" + condensableNames.size() + " components)");
    pseudoCNT.setTemperature(system.getTemperature());
    pseudoCNT.setTotalPressure(system.getPressure() * 1e5);
    pseudoCNT.setSupersaturationRatio(effectiveSupersaturation);
    pseudoCNT.setResidenceTime(residenceTime);

    // Set gas transport properties
    try {
      int gasIdx = system.getPhaseNumberOfPhase("gas");
      double gasVisc = system.getPhase(gasIdx).getViscosity("kg/msec");
      if (gasVisc > 0.0) {
        pseudoCNT.setGasViscosity(gasVisc);
      }
      double gasMw = system.getPhase(gasIdx).getMolarMass();
      if (gasMw > 0.0) {
        pseudoCNT.setCarrierGasMolarMass(gasMw);
      }
    } catch (Exception e) {
      // Use defaults
    }

    // Set heterogeneous parameters
    if (heterogeneous) {
      pseudoCNT.setHeterogeneous(true);
      pseudoCNT.setContactAngle(contactAngleDegrees);
    }

    pseudoCNT.calculate();

    totalNucleationRate = pseudoCNT.getNucleationRate();
    meanParticleDiameter = pseudoCNT.getMeanParticleDiameter();

    // Find dominant component (highest individual supersaturation)
    findDominantComponent();
  }

  /**
   * Calculates nucleation treating each condensable component independently.
   *
   * <p>
   * Creates separate CNT models for each condensable component and sums the nucleation rates. The
   * mean particle diameter is taken from the dominant component.
   * </p>
   */
  private void calculateIndependent() {
    componentCNTs.clear();
    componentNucleationRates.clear();
    totalNucleationRate = 0.0;

    double maxRate = 0.0;
    int maxIdx = 0;

    for (int idx = 0; idx < condensableIndices.size(); idx++) {
      ClassicalNucleationTheory cnt =
          ClassicalNucleationTheory.fromThermoSystem(system, condensableNames.get(idx));

      if (cnt == null) {
        componentCNTs.add(null);
        componentNucleationRates.add(0.0);
        continue;
      }

      cnt.setResidenceTime(residenceTime);

      // Override supersaturation with component-specific value
      double si = componentSupersaturations.get(idx);
      if (si > 1.0) {
        cnt.setSupersaturationRatio(si);
      }

      if (heterogeneous) {
        cnt.setHeterogeneous(true);
        cnt.setContactAngle(contactAngleDegrees);
      }

      cnt.calculate();

      componentCNTs.add(cnt);
      double rate = cnt.getNucleationRate();
      componentNucleationRates.add(rate);
      totalNucleationRate += rate;

      if (rate > maxRate) {
        maxRate = rate;
        maxIdx = idx;
      }
    }

    // Mean diameter from dominant component
    if (maxIdx < componentCNTs.size() && componentCNTs.get(maxIdx) != null) {
      meanParticleDiameter = componentCNTs.get(maxIdx).getMeanParticleDiameter();
    }

    findDominantComponent();
  }

  /**
   * Finds the dominant condensing component (highest supersaturation ratio).
   */
  private void findDominantComponent() {
    double maxS = 0.0;
    dominantComponent = "";
    for (int idx = 0; idx < condensableNames.size(); idx++) {
      double si = componentSupersaturations.get(idx);
      if (si > maxS) {
        maxS = si;
        dominantComponent = condensableNames.get(idx);
      }
    }
  }

  /**
   * Resets all calculated results.
   */
  private void resetResults() {
    totalNucleationRate = 0.0;
    meanParticleDiameter = 0.0;
    dominantComponent = "";
    effectiveMW = 0.0;
    effectiveDensity = 0.0;
    effectiveSurfaceTension = 0.0;
    effectiveSupersaturation = 1.0;
    componentNucleationRates.clear();
    componentCNTs.clear();
    pseudoCNT = null;
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Returns the total nucleation rate summed over all condensable components.
   *
   * @return total nucleation rate in particles/(m3*s)
   */
  public double getTotalNucleationRate() {
    return totalNucleationRate;
  }

  /**
   * Returns the mean particle diameter.
   *
   * @return mean particle diameter in m
   */
  public double getMeanParticleDiameter() {
    return meanParticleDiameter;
  }

  /**
   * Returns the name of the dominant condensing component.
   *
   * @return dominant component name
   */
  public String getDominantComponent() {
    return dominantComponent;
  }

  /**
   * Returns the number of condensable components identified.
   *
   * @return number of condensable components
   */
  public int getNumberOfCondensableComponents() {
    return condensableIndices.size();
  }

  /**
   * Returns the list of condensable component names.
   *
   * @return list of component names
   */
  public List<String> getCondensableComponentNames() {
    return new ArrayList<String>(condensableNames);
  }

  /**
   * Returns the per-component supersaturation ratios.
   *
   * @return list of supersaturation ratios
   */
  public List<Double> getComponentSupersaturations() {
    return new ArrayList<Double>(componentSupersaturations);
  }

  /**
   * Returns the per-component condensation fractions (mole fraction contributions).
   *
   * @return list of condensation fractions (sum to 1.0)
   */
  public List<Double> getComponentCondensationFractions() {
    return new ArrayList<Double>(componentCondensationFractions);
  }

  /**
   * Returns the per-component nucleation rates (INDEPENDENT mode only).
   *
   * @return list of nucleation rates in particles/(m3*s)
   */
  public List<Double> getComponentNucleationRates() {
    return new ArrayList<Double>(componentNucleationRates);
  }

  /**
   * Returns the effective pseudocomponent molecular weight.
   *
   * @return effective MW in kg/mol
   */
  public double getEffectiveMW() {
    return effectiveMW;
  }

  /**
   * Returns the effective pseudocomponent condensed-phase density.
   *
   * @return effective density in kg/m3
   */
  public double getEffectiveDensity() {
    return effectiveDensity;
  }

  /**
   * Returns the effective pseudocomponent supersaturation ratio.
   *
   * @return effective supersaturation ratio
   */
  public double getEffectiveSupersaturation() {
    return effectiveSupersaturation;
  }

  /**
   * Returns the effective pseudocomponent surface tension.
   *
   * @return effective surface tension in N/m
   */
  public double getEffectiveSurfaceTension() {
    return effectiveSurfaceTension;
  }

  /**
   * Returns the pseudocomponent CNT model (PSEUDOCOMPONENT mode only).
   *
   * @return the pseudocomponent CNT model, or null if not available
   */
  public ClassicalNucleationTheory getPseudocomponentCNT() {
    return pseudoCNT;
  }

  /**
   * Returns whether the calculation has been performed.
   *
   * @return true if calculated
   */
  public boolean isCalculated() {
    return calculated;
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * Returns all results as a Map for serialization.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("mode", mode.name());
    result.put("heterogeneous", heterogeneous);
    if (heterogeneous) {
      result.put("contactAngle_degrees", contactAngleDegrees);
    }
    result.put("temperature_K", system.getTemperature());
    result.put("pressure_bara", system.getPressure());
    result.put("residenceTime_s", residenceTime);

    // Condensable components
    List<Map<String, Object>> compList = new ArrayList<Map<String, Object>>();
    for (int idx = 0; idx < condensableNames.size(); idx++) {
      Map<String, Object> comp = new LinkedHashMap<String, Object>();
      comp.put("name", condensableNames.get(idx));
      comp.put("supersaturationRatio", componentSupersaturations.get(idx));
      comp.put("condensationFraction", componentCondensationFractions.get(idx));
      if (idx < componentNucleationRates.size()) {
        comp.put("nucleationRate_per_m3_s", componentNucleationRates.get(idx));
      }
      compList.add(comp);
    }
    result.put("condensableComponents", compList);
    result.put("numberOfCondensableComponents", condensableIndices.size());
    result.put("dominantComponent", dominantComponent);

    if (mode == NucleationMode.PSEUDOCOMPONENT) {
      Map<String, Object> pseudo = new LinkedHashMap<String, Object>();
      pseudo.put("effectiveMW_kg_mol", effectiveMW);
      pseudo.put("effectiveDensity_kg_m3", effectiveDensity);
      pseudo.put("effectiveSurfaceTension_N_m", effectiveSurfaceTension);
      pseudo.put("effectiveSupersaturation", effectiveSupersaturation);
      result.put("pseudocomponent", pseudo);
    }

    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("totalNucleationRate_per_m3_s", totalNucleationRate);
    results.put("meanParticleDiameter_m", meanParticleDiameter);
    results.put("meanParticleDiameter_um", meanParticleDiameter * 1e6);
    result.put("results", results);

    return result;
  }

  /**
   * Returns a JSON report of all results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(toMap());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!calculated) {
      return "MulticomponentNucleation [not calculated]";
    }
    return String.format(
        "MulticomponentNucleation[%s]: %d condensables, dominant=%s, J=%.2e /m3s, d=%.2f um",
        mode.name(), condensableIndices.size(), dominantComponent, totalNucleationRate,
        meanParticleDiameter * 1e6);
  }
}

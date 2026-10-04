package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable model-explicit station snapshot. EOS-backed factories use total mass divided by thermodynamic volume;
 * analytical models must declare their alternative property basis.
 */
public final class ReleaseState implements Serializable {
  private static final long serialVersionUID = 1L;
  private final double pressurePa;
  private final double temperatureK;
  private final double densityKgM3;
  private final double enthalpyJkg;
  private final double entropyJkgK;
  private final double velocityMs;
  private final Map<String, Double> componentMoleFractions;
  private final Map<String, Double> componentMassFractions;
  private final Map<String, Double> phaseMassFractions;
  private final Map<String, Map<String, Double>> phaseComponentMassFractions;
  private final Map<String, Double> phaseDensitiesKgM3;
  private final Map<String, Double> phaseVelocitiesMs;

  private ReleaseState(SystemInterface fluid, double velocityMs) {
    pressurePa = ReleaseFlowRequest.positive(fluid.getPressure() * 1e5, "pressurePa");
    temperatureK = ReleaseFlowRequest.positive(fluid.getTemperature(), "temperatureK");
    double mass = ReleaseFlowRequest.positive(fluid.getMass("kg"), "mass");
    densityKgM3 = ReleaseFlowRequest.positive(mass / fluid.getVolume("m3"), "EOS density");
    enthalpyJkg = fluid.getEnthalpy("J/kg");
    entropyJkgK = fluid.getEntropy("J/kgK");
    if (!Double.isFinite(enthalpyJkg) || !Double.isFinite(entropyJkgK) || !Double.isFinite(velocityMs)
        || velocityMs < 0.0) {
      throw new IllegalStateException("Nonfinite caloric property or invalid station velocity");
    }
    this.velocityMs = velocityMs;
    Map<String, Double> mole = new TreeMap<String, Double>();
    Map<String, Double> weight = new TreeMap<String, Double>();
    Map<String, Double> phase = new TreeMap<String, Double>();
    Map<String, Map<String, Double>> phaseComponents = new TreeMap<String, Map<String, Double>>();
    Map<String, Double> phaseDensities = new TreeMap<String, Double>();
    double moles = ReleaseFlowRequest.positive(fluid.getTotalNumberOfMoles(), "total moles");
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getComponentName();
      double amount = fluid.getComponent(i).getNumberOfmoles();
      mole.put(name, amount / moles);
      weight.put(name, amount * fluid.getComponent(i).getMolarMass() / mass);
    }
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      String type = fluid.getPhase(i).getType().name();
      double fraction = fluid.getPhase(i).getMass() / mass;
      phase.put(type, fraction + (phase.containsKey(type) ? phase.get(type) : 0.0));
      if (phaseDensities.containsKey(type)) {
        throw new IllegalStateException("Duplicate native phase type cannot expose one phase density: " + type);
      }
      Map<String, Double> phaseWeights = new TreeMap<String, Double>();
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        phaseWeights.put(fluid.getPhase(i).getComponent(component).getComponentName(),
            fluid.getPhase(i).getWtFrac(component));
      }
      phaseComponents.put(type, fractions(phaseWeights));
      phaseDensities.put(type, ReleaseFlowRequest
          .positive(fluid.getPhase(i).getMass() / fluid.getPhase(i).getVolume("m3"), type + " phase density"));
    }
    componentMoleFractions = fractions(mole);
    componentMassFractions = fractions(weight);
    phaseMassFractions = fractions(phase);
    phaseComponentMassFractions = phaseFractions(phaseComponents, phaseMassFractions.keySet());
    phaseDensitiesKgM3 = positiveValues(phaseDensities, "phase density");
    phaseVelocitiesMs = Collections.emptyMap();
  }

  private ReleaseState(SystemInterface compositionReference, double pressurePa, double temperatureK, double densityKgM3,
      double enthalpyJkg, double entropyJkgK, double velocityMs) {
    this.pressurePa = ReleaseFlowRequest.positive(pressurePa, "pressurePa");
    this.temperatureK = ReleaseFlowRequest.positive(temperatureK, "temperatureK");
    this.densityKgM3 = ReleaseFlowRequest.positive(densityKgM3, "ideal-gas density");
    if (!Double.isFinite(enthalpyJkg) || !Double.isFinite(entropyJkgK) || !Double.isFinite(velocityMs)
        || velocityMs < 0.0) {
      throw new IllegalStateException("Nonfinite caloric property or invalid station velocity");
    }
    this.enthalpyJkg = enthalpyJkg;
    this.entropyJkgK = entropyJkgK;
    this.velocityMs = velocityMs;
    Map<String, Double> mole = new TreeMap<String, Double>();
    Map<String, Double> weight = new TreeMap<String, Double>();
    double moles = ReleaseFlowRequest.positive(compositionReference.getTotalNumberOfMoles(), "total moles");
    double mass = ReleaseFlowRequest.positive(compositionReference.getMass("kg"), "mass");
    for (int i = 0; i < compositionReference.getNumberOfComponents(); i++) {
      String name = compositionReference.getComponent(i).getComponentName();
      double amount = compositionReference.getComponent(i).getNumberOfmoles();
      mole.put(name, amount / moles);
      weight.put(name, amount * compositionReference.getComponent(i).getMolarMass() / mass);
    }
    componentMoleFractions = fractions(mole);
    componentMassFractions = fractions(weight);
    Map<String, Double> phase = new TreeMap<String, Double>();
    phase.put(PhaseType.GAS.name(), 1.0);
    phaseMassFractions = fractions(phase);
    Map<String, Map<String, Double>> phaseComponents = new TreeMap<String, Map<String, Double>>();
    phaseComponents.put(PhaseType.GAS.name(), componentMassFractions);
    phaseComponentMassFractions = phaseFractions(phaseComponents, phaseMassFractions.keySet());
    Map<String, Double> phaseDensities = new TreeMap<String, Double>();
    phaseDensities.put(PhaseType.GAS.name(), densityKgM3);
    phaseDensitiesKgM3 = positiveValues(phaseDensities, "phase density");
    phaseVelocitiesMs = Collections.emptyMap();
  }

  private ReleaseState(ReleaseState reference, double bulkVelocityMs, Map<String, Double> phaseVelocitiesMs) {
    if (!Double.isFinite(bulkVelocityMs) || bulkVelocityMs < 0.0 || phaseVelocitiesMs == null
        || phaseVelocitiesMs.isEmpty()) {
      throw new IllegalArgumentException("Finite bulk velocity and phase velocities required");
    }
    if (!reference.phaseMassFractions.keySet().equals(phaseVelocitiesMs.keySet())) {
      throw new IllegalArgumentException("Phase velocity keys must match phase mass fractions");
    }
    pressurePa = reference.pressurePa;
    temperatureK = reference.temperatureK;
    densityKgM3 = reference.densityKgM3;
    enthalpyJkg = reference.enthalpyJkg;
    entropyJkgK = reference.entropyJkgK;
    velocityMs = bulkVelocityMs;
    componentMoleFractions = reference.componentMoleFractions;
    componentMassFractions = reference.componentMassFractions;
    phaseMassFractions = reference.phaseMassFractions;
    phaseComponentMassFractions = reference.phaseComponentMassFractions;
    phaseDensitiesKgM3 = reference.phaseDensitiesKgM3;
    this.phaseVelocitiesMs = positiveValues(phaseVelocitiesMs, "phase velocity");
  }

  /**
   * Creates a nonequilibrium phase-basis snapshot while retaining overall composition and caloric properties.
   *
   * @param reference immutable reference station
   * @param phaseMassFractions replacement phase mass fractions
   * @param phaseDensitiesKgM3 replacement phase densities in kg/m3
   */
  private ReleaseState(ReleaseState reference, Map<String, Double> phaseMassFractions,
      Map<String, Double> phaseDensitiesKgM3) {
    if (phaseMassFractions == null || phaseDensitiesKgM3 == null || phaseMassFractions.isEmpty()
        || !phaseMassFractions.keySet().equals(phaseDensitiesKgM3.keySet())) {
      throw new IllegalArgumentException("Matching phase mass fractions and densities required");
    }
    this.phaseMassFractions = fractions(phaseMassFractions);
    this.phaseDensitiesKgM3 = positiveValues(phaseDensitiesKgM3, "phase density");
    double specificVolumeM3Kg = 0.0;
    for (Map.Entry<String, Double> entry : this.phaseMassFractions.entrySet()) {
      specificVolumeM3Kg += entry.getValue() / this.phaseDensitiesKgM3.get(entry.getKey());
    }
    pressurePa = reference.pressurePa;
    temperatureK = reference.temperatureK;
    densityKgM3 = ReleaseFlowRequest.positive(1.0 / specificVolumeM3Kg, "relaxed mixture density");
    enthalpyJkg = reference.enthalpyJkg;
    entropyJkgK = reference.entropyJkgK;
    velocityMs = reference.velocityMs;
    componentMoleFractions = reference.componentMoleFractions;
    componentMassFractions = reference.componentMassFractions;
    phaseComponentMassFractions = Collections.emptyMap();
    phaseVelocitiesMs = Collections.emptyMap();
  }

  /**
   * Creates a nonequilibrium phase-basis snapshot with explicit component mass fractions in each phase.
   *
   * @param reference immutable reference station
   * @param phaseMassFractions replacement phase mass fractions
   * @param phaseDensitiesKgM3 replacement phase densities in kg/m3
   * @param phaseComponentMassFractions component mass fractions indexed first by phase and then component
   */
  private ReleaseState(ReleaseState reference, Map<String, Double> phaseMassFractions,
      Map<String, Double> phaseDensitiesKgM3, Map<String, Map<String, Double>> phaseComponentMassFractions) {
    if (phaseMassFractions == null || phaseDensitiesKgM3 == null || phaseComponentMassFractions == null
        || phaseMassFractions.isEmpty() || !phaseMassFractions.keySet().equals(phaseDensitiesKgM3.keySet())
        || !phaseMassFractions.keySet().equals(phaseComponentMassFractions.keySet())) {
      throw new IllegalArgumentException("Matching phase fractions, densities and component bases required");
    }
    this.phaseMassFractions = fractions(phaseMassFractions);
    this.phaseDensitiesKgM3 = positiveValues(phaseDensitiesKgM3, "phase density");
    this.phaseComponentMassFractions = phaseFractions(phaseComponentMassFractions, this.phaseMassFractions.keySet());
    double specificVolumeM3Kg = 0.0;
    for (Map.Entry<String, Double> entry : this.phaseMassFractions.entrySet()) {
      specificVolumeM3Kg += entry.getValue() / this.phaseDensitiesKgM3.get(entry.getKey());
    }
    pressurePa = reference.pressurePa;
    temperatureK = reference.temperatureK;
    densityKgM3 = ReleaseFlowRequest.positive(1.0 / specificVolumeM3Kg, "relaxed mixture density");
    enthalpyJkg = reference.enthalpyJkg;
    entropyJkgK = reference.entropyJkgK;
    velocityMs = reference.velocityMs;
    componentMoleFractions = reference.componentMoleFractions;
    componentMassFractions = reference.componentMassFractions;
    phaseVelocitiesMs = Collections.emptyMap();
  }

  /**
   * Snapshots an initialized fluid without flashing or modifying it.
   *
   * @param fluid initialized, equilibrated station state
   * @param velocityMs axial velocity in m/s
   * @return immutable state
   * @throws IllegalArgumentException for nonpositive pressure, temperature or inventory
   * @throws IllegalStateException for nonfinite properties or unclosed fractions
   */
  public static ReleaseState fromFluid(SystemInterface fluid, double velocityMs) {
    if (fluid == null) {
      throw new IllegalArgumentException("Fluid required");
    }
    return new ReleaseState(fluid, velocityMs);
  }

  /**
   * Creates an analytical ideal-gas station while retaining the supplied overall composition.
   *
   * <p>
   * Package-private by design: release models, rather than callers, own the property basis of a station.
   */
  static ReleaseState idealGas(SystemInterface compositionReference, double pressurePa, double temperatureK,
      double densityKgM3, double enthalpyJkg, double entropyJkgK, double velocityMs) {
    if (compositionReference == null) {
      throw new IllegalArgumentException("Composition reference required");
    }
    return new ReleaseState(compositionReference, pressurePa, temperatureK, densityKgM3, enthalpyJkg, entropyJkgK,
        velocityMs);
  }

  /**
   * Returns the same thermodynamic snapshot with an explicit slip-flow velocity basis.
   *
   * <p>
   * The scalar velocity is the bulk superficial velocity, so density times velocity remains the total mass flux. Phase
   * velocities are absolute axial velocities and must be supplied for every reported phase.
   *
   * @param bulkVelocityMs total mass flux divided by EOS mixture density, in m/s
   * @param phaseVelocitiesMs phase velocities in m/s indexed by native phase type
   * @return immutable hydrodynamic snapshot
   */
  ReleaseState withPhaseVelocities(double bulkVelocityMs, Map<String, Double> phaseVelocitiesMs) {
    return new ReleaseState(this, bulkVelocityMs, phaseVelocitiesMs);
  }

  /**
   * Returns the same caloric and overall-composition snapshot with a caller-resolved nonequilibrium phase basis.
   *
   * <p>
   * The mixture density is recomputed from phase mass fractions and phase specific volumes. This method is
   * package-private so release models retain responsibility for component, energy and applicability checks.
   *
   * @param phaseMassFractions phase mass fractions summing to one
   * @param phaseDensitiesKgM3 positive phase densities in kg/m3 on the same key set
   * @return immutable state with no phase-velocity assignment
   */
  ReleaseState withPhaseBasis(Map<String, Double> phaseMassFractions, Map<String, Double> phaseDensitiesKgM3) {
    return new ReleaseState(this, phaseMassFractions, phaseDensitiesKgM3);
  }

  /**
   * Returns the same caloric and overall-composition snapshot with an explicit component-resolved phase basis.
   *
   * @param phaseMassFractions phase mass fractions summing to one
   * @param phaseDensitiesKgM3 positive phase densities in kg/m3 on the same key set
   * @param phaseComponentMassFractions normalized component mass fractions for every phase
   * @return immutable component-resolved nonequilibrium state
   */
  ReleaseState withPhaseBasis(Map<String, Double> phaseMassFractions, Map<String, Double> phaseDensitiesKgM3,
      Map<String, Map<String, Double>> phaseComponentMassFractions) {
    return new ReleaseState(this, phaseMassFractions, phaseDensitiesKgM3, phaseComponentMassFractions);
  }

  private static Map<String, Double> fractions(Map<String, Double> values) {
    double sum = 0.0;
    for (double value : values.values()) {
      if (!Double.isFinite(value) || value < 0.0 || value > 1.0 + 1e-8) {
        throw new IllegalStateException("Invalid composition or phase mass fraction");
      }
      sum += value;
    }
    if (Math.abs(sum - 1.0) > 1e-8) {
      throw new IllegalStateException("Composition or phase mass fractions do not close");
    }
    return Collections.unmodifiableMap(new TreeMap<String, Double>(values));
  }

  private static Map<String, Double> positiveValues(Map<String, Double> values, String description) {
    Map<String, Double> copy = new TreeMap<String, Double>();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      if (entry.getKey() == null || entry.getKey().trim().isEmpty() || !Double.isFinite(entry.getValue())
          || entry.getValue() <= 0.0) {
        throw new IllegalStateException("Invalid " + description);
      }
      copy.put(entry.getKey(), entry.getValue());
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Map<String, Map<String, Double>> phaseFractions(
      Map<String, Map<String, Double>> phaseComponentMassFractions, java.util.Set<String> phaseNames) {
    if (!phaseNames.equals(phaseComponentMassFractions.keySet())) {
      throw new IllegalStateException("Phase component basis must match phase mass fractions");
    }
    Map<String, Map<String, Double>> copy = new TreeMap<String, Map<String, Double>>();
    java.util.Set<String> components = null;
    for (Map.Entry<String, Map<String, Double>> entry : phaseComponentMassFractions.entrySet()) {
      Map<String, Double> normalized = fractions(entry.getValue());
      if (components == null) {
        components = normalized.keySet();
      } else if (!components.equals(normalized.keySet())) {
        throw new IllegalStateException("Every phase must use the same component basis");
      }
      copy.put(entry.getKey(), normalized);
    }
    return Collections.unmodifiableMap(copy);
  }

  /** @return absolute pressure in Pa */
  public double getPressurePa() {
    return pressurePa;
  }

  /** @return temperature in K */
  public double getTemperatureK() {
    return temperatureK;
  }

  /** @return EOS total density in kg/m3 */
  public double getDensityKgM3() {
    return densityKgM3;
  }

  /** @return specific enthalpy in J/kg */
  public double getEnthalpyJkg() {
    return enthalpyJkg;
  }

  /** @return specific entropy in J/(kg K) */
  public double getEntropyJkgK() {
    return entropyJkgK;
  }

  /** @return axial velocity in m/s, or bulk superficial velocity for a slip-flow state */
  public double getVelocityMs() {
    return velocityMs;
  }

  /** @return inviscid local mass flux in kg/(m2 s), before the discharge coefficient */
  public double getMassFluxKgM2s() {
    return densityKgM3 * velocityMs;
  }

  /** @return immutable sorted overall mole fractions */
  public Map<String, Double> getComponentMoleFractions() {
    return componentMoleFractions;
  }

  /** @return immutable sorted overall mass fractions */
  public Map<String, Double> getComponentMassFractions() {
    return componentMassFractions;
  }

  /** @return immutable mass fractions indexed by native phase type */
  public Map<String, Double> getPhaseMassFractions() {
    return phaseMassFractions;
  }

  /**
   * Returns component mass fractions within each native phase.
   *
   * <p>
   * The outer map is indexed by phase type and each inner map sums to one. It is empty only for a nonequilibrium
   * phase-basis snapshot whose model did not resolve component partitioning.
   *
   * @return immutable nested phase-component mass-fraction map
   */
  public Map<String, Map<String, Double>> getPhaseComponentMassFractions() {
    return phaseComponentMassFractions;
  }

  /** @return immutable native-phase densities in kg/m3 */
  public Map<String, Double> getPhaseDensitiesKgM3() {
    return phaseDensitiesKgM3;
  }

  /** @return immutable native-phase velocities in m/s; empty for a homogeneous-flow state */
  public Map<String, Double> getPhaseVelocitiesMs() {
    return phaseVelocitiesMs;
  }

  /** @return gas mass fraction (not molar phase fraction) */
  public double getGasMassFraction() {
    return phaseMassFractions.containsKey("GAS") ? phaseMassFractions.get("GAS") : 0.0;
  }
}

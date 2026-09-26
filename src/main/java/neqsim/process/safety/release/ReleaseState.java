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
      phaseDensities.put(type, ReleaseFlowRequest
          .positive(fluid.getPhase(i).getMass() / fluid.getPhase(i).getVolume("m3"), type + " phase density"));
    }
    componentMoleFractions = fractions(mole);
    componentMassFractions = fractions(weight);
    phaseMassFractions = fractions(phase);
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
    phaseDensitiesKgM3 = reference.phaseDensitiesKgM3;
    this.phaseVelocitiesMs = positiveValues(phaseVelocitiesMs, "phase velocity");
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

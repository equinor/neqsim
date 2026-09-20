package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import neqsim.thermo.system.SystemInterface;

/** Immutable EOS station snapshot. Density is total mass divided by thermodynamic EOS volume. */
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
    }
    componentMoleFractions = fractions(mole);
    componentMassFractions = fractions(weight);
    phaseMassFractions = fractions(phase);
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

  /** @return velocity in m/s */
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

  /** @return gas mass fraction (not molar phase fraction) */
  public double getGasMassFraction() {
    return phaseMassFractions.containsKey("GAS") ? phaseMassFractions.get("GAS") : 0.0;
  }
}

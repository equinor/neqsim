package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable, provenance-required two-way film transfer for a lumped gas/liquid release inventory.
 *
 * <p>
 * Each caller-selected coefficient has units m/s. At the common inventory pressure, the gas-directed component mass
 * flux is {@code k c_g M_i (x_i phi_i^l / phi_i^g - y_i)}. The phases share mechanical pressure but may have separate
 * temperatures. Positive mass therefore evaporates from liquid to gas and negative mass condenses from gas to liquid.
 * Each explicit step is bounded to the configured fraction of its donor component; no absent component, equilibrium
 * fallback or inferred coefficient is used. The closure is a sensitivity model with prescribed coefficients, not an
 * independently qualified predictive correlation.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class ReleaseInventoryInterphaseTransfer implements Serializable {
  private static final long serialVersionUID = 1L;
  private final Map<String, Double> coefficientsMS;
  private final double maximumDonorFraction;
  private final String provenance;

  /**
   * Creates a two-way component film closure.
   *
   * @param coefficientsMS unique component names and finite nonnegative coefficients in m/s
   * @param maximumDonorFraction maximum fraction of one donor component transferred per substep, in (0, 0.5]
   * @param provenance source of coefficients and applicability assumptions
   */
  public ReleaseInventoryInterphaseTransfer(Map<String, Double> coefficientsMS, double maximumDonorFraction,
      String provenance) {
    if (coefficientsMS == null || coefficientsMS.isEmpty() || !Double.isFinite(maximumDonorFraction)
        || maximumDonorFraction <= 0.0 || maximumDonorFraction > 0.5) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_TRANSFER_CONFIGURATION_INVALID");
    }
    TreeMap<String, Double> copy = new TreeMap<String, Double>();
    for (Map.Entry<String, Double> entry : coefficientsMS.entrySet()) {
      String name = entry.getKey();
      Double coefficient = entry.getValue();
      if (name == null || name.trim().isEmpty() || coefficient == null || !Double.isFinite(coefficient)
          || coefficient < 0.0) {
        throw new IllegalArgumentException("INVENTORY_INTERPHASE_TRANSFER_COEFFICIENT_INVALID");
      }
      copy.put(name, coefficient);
    }
    if (provenance == null || provenance.trim().isEmpty()) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_TRANSFER_PROVENANCE_REQUIRED");
    }
    this.coefficientsMS = Collections.unmodifiableMap(copy);
    this.maximumDonorFraction = maximumDonorFraction;
    this.provenance = provenance;
  }

  /**
   * Evaluates one donor-limited transfer step without mutating either phase.
   *
   * @param gas forced single gas phase at the common pressure
   * @param liquid forced single liquid phase at the common pressure
   * @param gasComponentMassKg current gas-held component masses in kg
   * @param liquidComponentMassKg current liquid-held component masses in kg
   * @param interfacialAreaM2 live gas/liquid interface area in m2
   * @param durationS positive explicit substep duration in s
   * @return immutable signed component transfers; positive values move liquid to gas
   */
  public Step calculate(SystemInterface gas, SystemInterface liquid, Map<String, Double> gasComponentMassKg,
      Map<String, Double> liquidComponentMassKg, double interfacialAreaM2, double durationS) {
    requirePhases(gas, liquid);
    Objects.requireNonNull(gasComponentMassKg, "gasComponentMassKg");
    Objects.requireNonNull(liquidComponentMassKg, "liquidComponentMassKg");
    ReleaseFlowRequest.nonnegative(interfacialAreaM2, "inventoryInterfacialAreaM2");
    ReleaseFlowRequest.positive(durationS, "inventoryInterphaseDurationS");
    if (!gasComponentMassKg.keySet().equals(liquidComponentMassKg.keySet())) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_COMPONENT_SLATE_MISMATCH");
    }
    PhaseInterface gasPhase = gas.getPhase(0);
    PhaseInterface liquidPhase = liquid.getPhase(0);
    double molarConcentration = gasPhase.getDensity("kg/m3") / gasPhase.getMolarMass();
    if (!(molarConcentration > 0.0) || !Double.isFinite(molarConcentration)) {
      throw new IllegalStateException("INVENTORY_INTERPHASE_GAS_CONCENTRATION_INVALID");
    }
    TreeMap<String, Double> transferKg = new TreeMap<String, Double>();
    int limitedComponents = 0;
    double latentEnergyJ = 0.0;
    double gasEnthalpyJkg = gas.getEnthalpy("J/kg");
    double liquidEnthalpyJkg = liquid.getEnthalpy("J/kg");
    for (String name : gasComponentMassKg.keySet()) {
      double amount = 0.0;
      Double coefficient = coefficientsMS.get(name);
      if (coefficient != null && coefficient > 0.0 && interfacialAreaM2 > 0.0) {
        double phiGas = gasPhase.getComponent(name).getFugacityCoefficient();
        double phiLiquid = liquidPhase.getComponent(name).getFugacityCoefficient();
        if (!(phiGas > 0.0) || !(phiLiquid > 0.0) || !Double.isFinite(phiGas) || !Double.isFinite(phiLiquid)) {
          throw new IllegalStateException("INVENTORY_INTERPHASE_FUGACITY_INVALID: " + name);
        }
        double equilibriumGasFraction = liquidPhase.getComponent(name).getx() * phiLiquid / phiGas;
        double fluxKgM2S = coefficient * molarConcentration
            * (equilibriumGasFraction - gasPhase.getComponent(name).getx())
            * gasPhase.getComponent(name).getMolarMass();
        amount = fluxKgM2S * interfacialAreaM2 * durationS;
        if (!Double.isFinite(amount)) {
          throw new IllegalStateException("INVENTORY_INTERPHASE_TRANSFER_INVALID: " + name);
        }
        double donorMass = amount >= 0.0 ? value(liquidComponentMassKg, name) : value(gasComponentMassKg, name);
        double limit = maximumDonorFraction * donorMass;
        if (Math.abs(amount) > limit) {
          amount = Math.copySign(limit, amount);
          limitedComponents++;
        }
        latentEnergyJ += amount * (gasEnthalpyJkg - liquidEnthalpyJkg);
      }
      transferKg.put(name, amount);
    }
    if (!Double.isFinite(latentEnergyJ)) {
      throw new IllegalStateException("INVENTORY_INTERPHASE_LATENT_ENERGY_INVALID");
    }
    return new Step(transferKg, latentEnergyJ, limitedComponents);
  }

  /**
   * Verifies phase identities and common mechanical pressure.
   *
   * @param gas candidate gas phase
   * @param liquid candidate liquid phase
   */
  private static void requirePhases(SystemInterface gas, SystemInterface liquid) {
    if (gas == null || liquid == null || gas.getNumberOfPhases() != 1 || liquid.getNumberOfPhases() != 1
        || !gas.hasPhaseType("gas") || gas.hasPhaseType("oil") || gas.hasPhaseType("aqueous")
        || liquid.hasPhaseType("gas")
        || (!liquid.hasPhaseType("oil") && !liquid.hasPhaseType("liquid") && !liquid.hasPhaseType("aqueous"))
        || Math.abs(gas.getPressure() - liquid.getPressure()) > 1.0e-8) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_COMMON_PRESSURE_REQUIRED");
    }
  }

  /**
   * Reads a finite nonnegative component inventory.
   *
   * @param values component map
   * @param name component name
   * @return component mass in kg
   */
  private static double value(Map<String, Double> values, String name) {
    Double value = values.get(name);
    if (value == null || !Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_COMPONENT_INVENTORY_INVALID: " + name);
    }
    return value;
  }

  /** @return immutable component coefficients in m/s */
  public Map<String, Double> getCoefficientsMS() {
    return coefficientsMS;
  }

  /** @return maximum donor fraction per explicit substep */
  public double getMaximumDonorFraction() {
    return maximumDonorFraction;
  }

  /** @return caller-supplied coefficient and applicability provenance */
  public String getProvenance() {
    return provenance;
  }

  /** Immutable result of one transfer evaluation. */
  public static final class Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Double> gasDirectedComponentMassKg;
    private final double latentEnergyJ;
    private final int donorLimitedComponents;

    /**
     * Creates one immutable transfer result.
     *
     * @param gasDirectedComponentMassKg signed component masses in kg
     * @param latentEnergyJ signed phase-enthalpy difference associated with transfer in J
     * @param donorLimitedComponents number of component rates clipped by the donor bound
     */
    private Step(Map<String, Double> gasDirectedComponentMassKg, double latentEnergyJ, int donorLimitedComponents) {
      this.gasDirectedComponentMassKg = Collections
          .unmodifiableMap(new TreeMap<String, Double>(gasDirectedComponentMassKg));
      this.latentEnergyJ = latentEnergyJ;
      this.donorLimitedComponents = donorLimitedComponents;
    }

    /** @return signed component masses in kg; positive values move liquid to gas */
    public Map<String, Double> getGasDirectedComponentMassKg() {
      return gasDirectedComponentMassKg;
    }

    /** @return signed phase-enthalpy difference associated with transfer in J */
    public double getLatentEnergyJ() {
      return latentEnergyJ;
    }

    /** @return number of donor-limited component rates */
    public int getDonorLimitedComponents() {
      return donorLimitedComponents;
    }
  }
}

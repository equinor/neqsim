package neqsim.thermodynamicoperations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.WaterPhaseBoundaryResult.BoundaryResult;
import neqsim.thermodynamicoperations.WaterPhaseBoundaryResult.Status;
import neqsim.thermodynamicoperations.flashops.saturationops.FreezingPointResult;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateFormationTemperatureFlash;

/**
 * Calculates three CPA water-phase boundaries on detached copies of a fully configured fluid.
 *
 * <p>
 * Characterization, inhibitors, component amounts and fitted parameters are retained. No non-CPA model conversion or
 * gas-only extraction is performed. A liquid-water gas dew point is reported as not applicable when the supplied state
 * already contains a liquid; the ice and hydrate calculations still retain the complete fluid inventory.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class CpaWaterPhaseBoundaryCalculator {
  private final SystemInterface reference;
  private final Map<String, Double> composition;

  /**
   * Captures the configured Statoil CPA fluid without altering its state or fitted parameters.
   *
   * @param fluid fluid configured with Statoil CPA and mixing rule 10
   * @throws IllegalArgumentException if the model, mixing rule, pressure or component amounts are invalid
   */
  public CpaWaterPhaseBoundaryCalculator(SystemInterface fluid) {
    if (!(fluid instanceof SystemSrkCPAstatoil) || fluid.getMixingRule() == null
        || fluid.getMixingRule().getValue() != 10) {
      throw new IllegalArgumentException("Configure SystemSrkCPAstatoil with mixing rule 10; automatic conversion "
          + "could discard characterization or fitted parameters");
    }
    if (!Double.isFinite(fluid.getPressure()) || fluid.getPressure() <= 0.0 || !Double.isFinite(fluid.getTemperature())
        || fluid.getTemperature() <= 0.0) {
      throw new IllegalArgumentException("CPA water boundaries require positive finite pressure and temperature");
    }
    double total = 0.0;
    composition = new LinkedHashMap<String, Double>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double amount = fluid.getPhase(0).getComponent(i).getNumberOfmoles();
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalArgumentException("Component amounts must be finite and nonnegative");
      }
      total += amount;
      composition.put(fluid.getPhase(0).getComponent(i).getComponentName(), amount);
    }
    if (!Double.isFinite(total) || total <= 0.0 || !composition.containsKey("water")
        || !(composition.get("water") > 0.0)) {
      throw new IllegalArgumentException("CPA water boundaries require positive finite water content");
    }
    for (Map.Entry<String, Double> component : composition.entrySet()) {
      component.setValue(component.getValue() / total);
    }
    // A serialized snapshot also detaches nested association/reference-phase state retained by some clone paths.
    reference = SerializationUtils.clone(fluid);
  }

  /**
   * Evaluates each boundary independently so a failed boundary cannot remove another valid result.
   *
   * @return immutable comparison with per-boundary convergence and reference-fluid information
   */
  public WaterPhaseBoundaryResult calculate() {
    return new WaterPhaseBoundaryResult(reference.getPressure(), reference.getModelName(), composition,
        calculateLiquidWater(), calculateIce(), calculateHydrate());
  }

  /** @return isolated liquid-water gas-dew-point result, or an explicit applicability/failure diagnostic */
  private BoundaryResult calculateLiquidWater() {
    try {
      SystemInterface trial = fluidOnlyCopy();
      trial.setMultiPhaseCheck(true);
      new ThermodynamicOperations(trial).TPflash();
      if (trial.getNumberOfPhases() != 1 || trial.getPhase(0).getType() != PhaseType.GAS) {
        return unavailable(Status.NOT_APPLICABLE,
            "A gas dew point is not defined from the overall composition of an already liquid/multiphase fluid. "
                + "Use a defined gas-phase sample or a dedicated multiphase water-phase boundary calculation.",
            phaseBasis(trial, "liquid-water gas dew point"));
      }
      new ThermodynamicOperations(trial).waterDewPointTemperatureFlash(true);
      double residual = Math.log(trial.getPhase(0).getFugacity("water") / trial.getPhase(1).getFugacity("water"));
      return converged(trial, residual, 1.0e-7,
          "Fixed-feed gas against an incipient water-rich liquid (>50 mol% water), including dissolved components. "
              + "Metastable liquid below freezing; other liquid branches are not globally stability-tested.",
          "incipient water-rich liquid");
    } catch (CancellationException ex) {
      throw ex;
    } catch (Exception ex) {
      return unavailable(Status.FAILED, failure(ex), "incipient water-rich liquid");
    }
  }

  /** @return independent ice boundary against the complete fluid inventory */
  private BoundaryResult calculateIce() {
    try {
      SystemInterface trial = fluidOnlyCopy();
      trial.setMultiPhaseCheck(true);
      trial.setSolidPhaseCheck("water");
      FreezingPointResult result = new ThermodynamicOperations(trial).freezingPointTemperatureFlashResult();
      if (!result.isConverged()) {
        return unavailable(Status.FAILED, result.getFailureReason(), "pure water ice");
      }
      return converged(trial, result.getResidual(), 1.0e-8,
          "Ice equilibrium against the complete coexisting fluid, retaining inhibitors and oil. "
              + "The fusion/heat-capacity reference is an approximation, not the full IAPWS ice EOS.",
          "pure water ice");
    } catch (CancellationException ex) {
      throw ex;
    } catch (Exception ex) {
      return unavailable(Status.FAILED, failure(ex), "pure water ice");
    }
  }

  /** @return independent finite-inventory hydrate boundary with the actual fluid-water fugacity */
  private BoundaryResult calculateHydrate() {
    try {
      boolean hasGuest = false;
      for (int i = 0; i < reference.getNumberOfComponents(); i++) {
        hasGuest |= reference.getPhase(0).getComponent(i).isHydrateFormer()
            && reference.getPhase(0).getComponent(i).getNumberOfmoles() > 0.0;
      }
      if (!hasGuest) {
        return unavailable(Status.NOT_APPLICABLE, "No positive hydrate-former inventory is present; "
            + "a guest-free water reference is not a gas-hydrate onset.", "water/guest hydrate");
      }
      SystemInterface trial = fluidOnlyCopy();
      trial.setMultiPhaseCheck(true);
      trial.setHydrateCheck(true);
      ThermodynamicOperations operations = new ThermodynamicOperations(trial);
      operations.hydrateFormationTemperature();
      HydrateFormationTemperatureFlash operation = (HydrateFormationTemperatureFlash) operations.getOperation();
      if (!operation.isConverged()) {
        return unavailable(Status.FAILED, "Hydrate solver did not converge", "water/guest hydrate");
      }
      return converged(trial, operation.getLastResidual(), 1.0e-6,
          "Hydrate-water equilibrium uses the actual aqueous water fugacity, or gas water fugacity when no aqueous "
              + "phase exists. Oil, dissolved guests and inhibitors remain in the fluid calculation; no free water is added.",
          "water/guest hydrate");
    } catch (CancellationException ex) {
      throw ex;
    } catch (Exception ex) {
      return unavailable(Status.FAILED, failure(ex), "water/guest hydrate");
    }
  }

  /** @return detached complete fluid with competing incipient solid/hydrate checks disabled */
  private SystemInterface fluidOnlyCopy() {
    SystemInterface copy = SerializationUtils.clone(reference);
    copy.setHydrateCheck(false);
    copy.setSolidPhaseCheck(false);
    return copy;
  }

  /**
   * Publishes only a finite root that satisfies its own residual acceptance criterion.
   *
   * @param fluid converged fluid state
   * @param residual boundary-specific dimensionless equilibrium residual
   * @param tolerance absolute residual tolerance
   * @param diagnostic boundary interpretation
   * @param condensedReference condensed reference identity
   * @return checked boundary outcome
   */
  private BoundaryResult converged(SystemInterface fluid, double residual, double tolerance, String diagnostic,
      String condensedReference) {
    if (!Double.isFinite(fluid.getTemperature()) || !Double.isFinite(residual) || Math.abs(residual) > tolerance) {
      return unavailable(Status.FAILED, "Boundary did not meet finite-temperature and residual criteria",
          condensedReference);
    }
    return new BoundaryResult(Status.CONVERGED, fluid.getTemperature("C"), residual, waterActivity(fluid), diagnostic,
        phaseBasis(fluid, condensedReference));
  }

  /**
   * Creates a missing result without retaining a previous trial temperature.
   *
   * @param status failure or applicability status
   * @param diagnostic explanation
   * @param basis reference-fluid description
   * @return unavailable boundary outcome
   */
  private BoundaryResult unavailable(Status status, String diagnostic, String basis) {
    return new BoundaryResult(status, Double.NaN, Double.NaN, Double.NaN, diagnostic, basis);
  }

  /**
   * Describes the material phase split and aqueous inhibitor composition actually present at the root.
   *
   * @param fluid evaluated fluid
   * @param condensedReference condensed reference identity
   * @return phase basis with water and inhibitor mole fractions
   */
  private String phaseBasis(SystemInterface fluid, String condensedReference) {
    StringBuilder basis = new StringBuilder(condensedReference).append(" against ");
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      if (phase.getType() == PhaseType.SOLID || phase.getType() == PhaseType.HYDRATE) {
        continue;
      }
      if (i > 0) {
        basis.append("; ");
      }
      basis.append(phase.getType()).append(" [x_water=").append(phase.getComponent("water").getx());
      for (String inhibitor : new String[] {"MEG", "methanol"}) {
        if (phase.hasComponent(inhibitor)) {
          basis.append(", x_").append(inhibitor).append("=").append(phase.getComponent(inhibitor).getx());
        }
      }
      basis.append("]");
    }
    return basis.toString();
  }

  /**
   * Calculates water activity relative to pure liquid water with the same CPA component and parameter basis.
   *
   * @param fluid evaluated complete fluid
   * @return water fugacity ratio, or NaN if that reference cannot be evaluated
   */
  private double waterActivity(SystemInterface fluid) {
    try {
      int referencePhase = 0;
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
          referencePhase = i;
          break;
        }
        if (fluid.getPhase(i).getType() == PhaseType.GAS) {
          referencePhase = i;
        }
      }
      double waterFugacity = fluid.getPhase(referencePhase).getFugacity("water");
      SystemInterface pure = SerializationUtils.clone(fluid);
      pure.setHydrateCheck(false);
      pure.setSolidPhaseCheck(false);
      double[] pureComposition = new double[pure.getNumberOfComponents()];
      pureComposition[pure.getPhase(0).getComponent("water").getComponentNumber()] = 1.0;
      pure.setMolarComposition(pureComposition);
      pure.init(0);
      pure.setNumberOfPhases(1);
      pure.setBeta(0, 1.0);
      pure.setPhaseType(0, PhaseType.LIQUID);
      pure.init(1);
      double activity = waterFugacity / pure.getPhase(0).getFugacity("water");
      return Double.isFinite(activity) && activity > 0.0 ? activity : Double.NaN;
    } catch (CancellationException ex) {
      throw ex;
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Returns a useful boundary failure explanation without a stack trace.
   *
   * @param exception calculation failure
   * @return diagnostic text
   */
  private String failure(Exception exception) {
    return exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
  }
}

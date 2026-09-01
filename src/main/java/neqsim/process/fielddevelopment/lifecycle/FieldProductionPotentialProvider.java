package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;

/** Supplies unconstrained new-field potential from detailed wells, a reservoir schedule, or a calibrated surrogate. */
public interface FieldProductionPotentialProvider extends Serializable {

  /**
   * Calculates new-field potential before facility allocation.
   *
   * <p>
   * Implementations may run a detailed NeqSim well/network model, read a reservoir-simulator schedule, or evaluate a
   * calibrated proxy. A zero gas entry tells the lifecycle simulator to derive produced gas from the detailed process
   * and live PVT model.
   * </p>
   *
   * @param model assembled lifecycle model
   * @param configuration lifecycle assumptions
   * @param fieldAgeYears elapsed lifecycle time in years
   * @param reservoirPressureBara live tank/reservoir pressure
   * @return unconstrained standard-condition oil, gas and water potential
   */
  FacilityProductionRate getPotential(FieldLifecycleModel model, FieldLifecycleConfiguration configuration,
      double fieldAgeYears, double reservoirPressureBara);
}

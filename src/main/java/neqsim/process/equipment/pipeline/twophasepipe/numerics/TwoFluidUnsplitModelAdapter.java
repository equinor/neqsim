package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Transactional bridge between {@link UnsplitTransientSolver} and the two-fluid finite-volume operator.
 *
 * <p>
 * Every residual probe starts from cloned accepted section templates. Trial pressure-dependent densities are installed
 * before conservative variables are recovered, and the closure densities are evaluated at the end-state pressure used
 * by the solver's volume equation. A prescribed outlet pressure is applied at the external boundary face only.
 * </p>
 *
 * <p>
 * This adapter is intentionally not selected by {@code TwoFluidPipe.runTransient}. It establishes the evaluator
 * contract needed for subsequent pipe integration and severe-slugging qualification without changing a production
 * default.
 * </p>
 */
public final class TwoFluidUnsplitModelAdapter implements UnsplitTransientSolver.Model, Serializable {
  private static final long serialVersionUID = 1L;
  private static final int PHASE_COUNT = 3;
  private static final int STATE_SIZE = 7;

  /** Pressure-dependent gas, oil, and water density closure. */
  @FunctionalInterface
  public interface PhaseDensityModel extends Serializable {
    /**
     * Evaluate phase densities for one cell.
     *
     * @param cell cell index
     * @param conservativeState seven-column conservative state
     * @param pressure cell pressure in Pa
     * @param time evaluation time in s
     * @return gas, oil, and water densities in kg/m3
     */
    double[] calculate(int cell, double[] conservativeState, double pressure, double time);
  }

  private final TwoFluidConservationEquations equations;
  private final TwoFluidSection[] acceptedTemplates;
  private final double spatialStep;
  private final PhaseDensityModel densityModel;

  /**
   * Create a transactional adapter.
   *
   * @param equations configured finite-volume operator
   * @param acceptedTemplates accepted section state used as the trial template
   * @param spatialStep representative cell size in m
   * @param densityModel pressure-dependent phase density model
   */
  public TwoFluidUnsplitModelAdapter(TwoFluidConservationEquations equations, TwoFluidSection[] acceptedTemplates,
      double spatialStep, PhaseDensityModel densityModel) {
    if (equations == null || densityModel == null) {
      throw new IllegalArgumentException("Equations and density model cannot be null");
    }
    if (acceptedTemplates == null || acceptedTemplates.length == 0) {
      throw new IllegalArgumentException("At least one accepted section template is required");
    }
    if (!(spatialStep > 0.0) || !Double.isFinite(spatialStep)) {
      throw new IllegalArgumentException("Spatial step must be positive and finite");
    }
    this.equations = equations;
    this.acceptedTemplates = cloneSections(acceptedTemplates);
    this.spatialStep = spatialStep;
    this.densityModel = densityModel;
  }

  @Override
  public synchronized UnsplitTransientSolver.Evaluation evaluate(double[][] state, double[] pressure,
      double[][] closureState, double[] closurePressure, double time, double outletPressure,
      boolean outletPressureFixed) {
    validateShape(state, pressure, "midpoint");
    validateShape(closureState, closurePressure, "closure");
    if (outletPressureFixed && (!(outletPressure > 0.0) || !Double.isFinite(outletPressure))) {
      throw new IllegalArgumentException("A fixed outlet pressure must be positive and finite");
    }

    TwoFluidSection[] trialSections = cloneSections(acceptedTemplates);
    for (int cell = 0; cell < trialSections.length; cell++) {
      double[] densities = densities(cell, state[cell], pressure[cell], time);
      trialSections[cell].setPressure(pressure[cell]);
      trialSections[cell].setGasDensity(densities[0]);
      trialSections[cell].setOilDensity(densities[1]);
      trialSections[cell].setWaterDensity(densities[2]);
    }
    equations.applyState(trialSections, state);

    double[][] closureDensities = new double[PHASE_COUNT][trialSections.length];
    for (int cell = 0; cell < trialSections.length; cell++) {
      double[] densities = densities(cell, closureState[cell], closurePressure[cell], time);
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        closureDensities[phase][cell] = densities[phase];
      }
    }

    double savedOutletPressure = equations.getOutletBoundaryPressure();
    double[][] rates;
    synchronized (equations) {
      try {
        equations.setOutletBoundaryPressure(outletPressureFixed ? outletPressure : Double.NaN);
        rates = equations.calcRHSTransactional(trialSections, spatialStep);
      } finally {
        equations.setOutletBoundaryPressure(savedOutletPressure);
      }
    }
    return new UnsplitTransientSolver.Evaluation(rates, closureDensities);
  }

  private double[] densities(int cell, double[] state, double pressure, double time) {
    double[] values = densityModel.calculate(cell, state.clone(), pressure, time);
    if (values == null || values.length != PHASE_COUNT) {
      throw new IllegalArgumentException("Density model must return gas, oil, and water densities");
    }
    for (double value : values) {
      if (!(value > 0.0) || !Double.isFinite(value)) {
        throw new IllegalArgumentException("Phase densities must be positive and finite");
      }
    }
    return values.clone();
  }

  private void validateShape(double[][] state, double[] pressure, String label) {
    if (state == null || pressure == null || state.length != acceptedTemplates.length
        || pressure.length != acceptedTemplates.length) {
      throw new IllegalArgumentException(label + " arrays must match the accepted section count");
    }
    for (int cell = 0; cell < state.length; cell++) {
      if (state[cell] == null || state[cell].length < STATE_SIZE) {
        throw new IllegalArgumentException(label + " state requires seven variables per cell");
      }
      if (!(pressure[cell] > 0.0) || !Double.isFinite(pressure[cell])) {
        throw new IllegalArgumentException(label + " pressures must be positive and finite");
      }
    }
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      if (sections[cell] == null) {
        throw new IllegalArgumentException("Section templates cannot contain null entries");
      }
      copy[cell] = sections[cell].clone();
    }
    return copy;
  }
}

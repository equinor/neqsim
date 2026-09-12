package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verified state and phase ledgers for atomic publication of a complete frozen-phase unsplit interval.
 *
 * <p>
 * This class prepares defensive endpoint sections and report inputs; it never changes a pipe, stream or clock. Boundary
 * amounts come from the accepted time-level face fluxes, including their signs, rather than endpoint velocities. The
 * caller retains ownership of the final complete-pipe transaction and must supply the accepted sections used to prepare
 * the interval. Closure diagnostics still require a separate endpoint refresh.
 * </p>
 *
 * <p>
 * Downstream fluid publication is deliberately narrower than hydrodynamic preparation. Without component advection,
 * each gas/oil/water composition must be uniform across the initial cells and prescribed inlet. The comparison uses an
 * absolute component mass-fraction tolerance of 1e-10; this is a compatibility check, not a physical mixing model. An
 * outlet with a negative phase transfer cannot be represented by one forward process stream and is rejected.
 * </p>
 */
public final class TwoFluidUnsplitPublication implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int PHASE_COUNT = 3;
  private static final double COMPOSITION_TOLERANCE = 1.0e-10;
  private final TwoFluidSection[] initialSections;
  private final TwoFluidSection[] endpointSections;
  private final double startTime;
  private final double endTime;
  private final int acceptedSubsteps;
  private final double[] initialMassKg;
  private final double[] finalMassKg;
  private final double[] inletMassKg;
  private final double[] outletMassKg;
  private final double[] sourceMassKg;

  private TwoFluidUnsplitPublication(PreparedInterval interval, TwoFluidSection[] initial, double tolerance) {
    initialSections = cloneSections(initial);
    endpointSections = interval.getEndpointSections();
    if (endpointSections.length != initialSections.length) {
      throw new IllegalArgumentException("Prepared endpoint must match the accepted mesh");
    }
    startTime = interval.getStartTimeSeconds();
    endTime = interval.getEndTimeSeconds();
    List<PreparedStep> steps = interval.getSubsteps();
    acceptedSubsteps = steps.size();
    double[][] faces = interval.getPhaseMassFaceTransferKg();
    double[][] sources = interval.getPhaseMassSourceTransferKg();
    requireMatrix(faces, initialSections.length + 1, PHASE_COUNT, "face transfers");
    requireMatrix(sources, initialSections.length, PHASE_COUNT, "source transfers");
    initialMassKg = new double[PHASE_COUNT];
    finalMassKg = new double[PHASE_COUNT];
    inletMassKg = faces[0].clone();
    outletMassKg = faces[faces.length - 1].clone();
    sourceMassKg = new double[PHASE_COUNT];
    double[][] momentumChange = new double[initialSections.length][PHASE_COUNT];
    double time = startTime;
    for (PreparedStep step : steps) {
      double dt = step.getTimeStepSeconds();
      if (step.getStartTimeSeconds() != time || !(dt > 0.0) || !Double.isFinite(dt)) {
        throw new IllegalStateException("Prepared substeps must cover the requested interval consecutively");
      }
      time += dt;
      double[][] rates = step.getEvaluation().getRates();
      requireMatrix(rates, initialSections.length, 7, "conservative rates");
      for (int cell = 0; cell < initialSections.length; cell++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          momentumChange[cell][phase] += dt * rates[cell][phase + PHASE_COUNT];
        }
      }
    }
    if (acceptedSubsteps == 0 || time != endTime) {
      throw new IllegalStateException("Prepared substeps must complete the whole requested interval");
    }
    for (int cell = 0; cell < initialSections.length; cell++) {
      TwoFluidSection before = initialSections[cell];
      TwoFluidSection after = endpointSections[cell];
      validateSection(before, tolerance, true);
      validateSection(after, tolerance, false);
      if (before.getLength() != after.getLength() || before.getArea() != after.getArea()
          || before.getPosition() != after.getPosition() || before.getInclination() != after.getInclination()
          || before.getTemperature() != after.getTemperature()) {
        throw new IllegalStateException("Frozen-phase publication cannot change mesh or cell temperature");
      }
      double[] previous = before.getStateVector();
      double[] endpoint = after.getStateVector();
      if (endpoint[6] != previous[6]) {
        throw new IllegalStateException("An isothermal interval cannot change the retained energy variable");
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double massChange = (faces[cell][phase] - faces[cell + 1][phase] + sources[cell][phase]) / before.getLength();
        requireResidual(endpoint[phase] - previous[phase] - massChange, previous[phase], tolerance, "cell phase mass");
        requireResidual(endpoint[phase + PHASE_COUNT] - previous[phase + PHASE_COUNT] - momentumChange[cell][phase],
            previous[phase + PHASE_COUNT], tolerance, "cell phase momentum");
        initialMassKg[phase] += previous[phase] * before.getLength();
        finalMassKg[phase] += endpoint[phase] * after.getLength();
        sourceMassKg[phase] += sources[cell][phase];
      }
    }
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (!Double.isFinite(initialMassKg[phase]) || !Double.isFinite(finalMassKg[phase])
          || !Double.isFinite(sourceMassKg[phase])) {
        throw new IllegalStateException("Published phase inventories and transfers must be finite");
      }
      requireResidual(
          finalMassKg[phase] - initialMassKg[phase] - inletMassKg[phase] + outletMassKg[phase] - sourceMassKg[phase],
          initialMassKg[phase], tolerance, "domain phase mass");
      if (!Double.isFinite(outletMassKg[phase] / getElapsedTimeSeconds())) {
        throw new IllegalStateException("Published interval-average phase flow must be finite");
      }
    }
  }

  /**
   * Verify complete-interval report inputs and recover endpoint primitives without modifying accepted state.
   *
   * @param interval completely prepared interval
   * @param initialSections same accepted cells used for preparation
   * @param startTime accepted simulation time in s
   * @param duration requested duration in s; the endpoint is its representable sum with startTime
   * @param relativeTolerance positive finite independent conservation and volume tolerance
   * @return immutable publication candidate
   * @throws IllegalArgumentException for invalid inputs, time mismatch or inadmissible section state
   * @throws IllegalStateException for incompatible geometry or failed conservative verification
   */
  public static TwoFluidUnsplitPublication prepare(PreparedInterval interval, TwoFluidSection[] initialSections,
      double startTime, double duration, double relativeTolerance) {
    double end = startTime + duration;
    if (interval == null || initialSections == null || initialSections.length == 0 || !Double.isFinite(startTime)
        || !(duration > 0.0) || !Double.isFinite(duration) || !Double.isFinite(end) || !(end > startTime)
        || !(relativeTolerance > 0.0) || !Double.isFinite(relativeTolerance)
        || interval.getStartTimeSeconds() != startTime || interval.getEndTimeSeconds() != end) {
      throw new IllegalArgumentException("Publication requires matching accepted time and a complete finite interval");
    }
    return new TwoFluidUnsplitPublication(interval, initialSections, relativeTolerance);
  }

  /** @return defensive endpoint cells with solved pressure, density and exact conserved state */
  public TwoFluidSection[] getEndpointSections() {
    return cloneSections(endpointSections);
  }

  /** @return accepted interval start in s */
  public double getStartTimeSeconds() {
    return startTime;
  }

  /** @return complete interval endpoint in s */
  public double getEndTimeSeconds() {
    return endTime;
  }

  /** @return actual representable interval duration in s */
  public double getElapsedTimeSeconds() {
    return endTime - startTime;
  }

  /** @return accepted internal step count */
  public int getAcceptedSubsteps() {
    return acceptedSubsteps;
  }

  /** @return defensive initial gas/oil/water inventory in kg */
  public double[] getInitialMassKg() {
    return initialMassKg.clone();
  }

  /** @return defensive final gas/oil/water inventory in kg */
  public double[] getFinalMassKg() {
    return finalMassKg.clone();
  }

  /** @return defensive signed gas/oil/water inlet transfers in kg */
  public double[] getInletMassKg() {
    return inletMassKg.clone();
  }

  /** @return defensive signed gas/oil/water outlet transfers in kg */
  public double[] getOutletMassKg() {
    return outletMassKg.clone();
  }

  /** @return defensive signed gas/oil/water source transfers in kg */
  public double[] getSourceMassKg() {
    return sourceMassKg.clone();
  }

  /** @return defensive signed gas/oil/water interval-average outlet flows in kg/s */
  public double[] getOutletMassFlowKgPerSecond() {
    double[] flow = outletMassKg.clone();
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      flow[phase] /= getElapsedTimeSeconds();
    }
    return flow;
  }

  /**
   * Prepare a downstream fluid using the exact accepted phase transfers and uniform frozen phase compositions.
   *
   * <p>
   * Reference clones are TP-flashed at every initial cell's pressure and temperature, matching construction of the
   * pipe's anchored density model. Every present phase must have the same named-component mass fractions across these
   * cells and the prescribed inlet. A TP flash of the weighted outlet preserves component totals but may change the
   * equilibrium phase split. Zero outlet transfer produces a zero-flow fluid with reference composition as its carrier.
   * All supplied fluids, sections and ledgers remain unchanged on success or failure.
   * </p>
   *
   * @param referenceFluid same initialized thermodynamic reference used to build the frozen density model
   * @param prescribedInletFluid existing initialized inlet phase composition, or null for a closed inlet
   * @param pressurePa outlet absolute pressure in Pa
   * @param temperatureK outlet temperature in K
   * @return independent interval-average outlet fluid
   * @throws IllegalArgumentException for invalid thermodynamic inputs or incompatible component slate
   * @throws IllegalStateException for nonuniform frozen phase composition, negative phase transfer or failed flash
   */
  public SystemInterface createOutletFluid(SystemInterface referenceFluid, SystemInterface prescribedInletFluid,
      double pressurePa, double temperatureK) {
    return createOutletFluid(referenceFluid, prescribedInletFluid, initialSections, pressurePa, temperatureK);
  }

  /**
   * Prepare the outlet using persistent composition reference conditions across consecutive accepted intervals.
   *
   * <p>
   * The same reference sections must be retained while the frozen density model remains active. Their pressure fixes
   * the original phase compositions; changing current pressure must not trigger an unaccounted redistribution of
   * component inventory. Reference cell geometry and temperature must match the current isothermal interval.
   * </p>
   *
   * @param referenceFluid persistent thermodynamic reference used for the anchored density model
   * @param prescribedInletFluid existing inlet phase composition, or null for a closed inlet
   * @param frozenCompositionSections original cell pressure/temperature conditions used to freeze phase compositions
   * @param pressurePa outlet absolute pressure in Pa
   * @param temperatureK outlet temperature in K
   * @return independent interval-average outlet fluid
   * @throws IllegalArgumentException for invalid reference geometry or thermodynamic inputs
   * @throws IllegalStateException for nonuniform composition, negative phase transfer or failed flash
   */
  public SystemInterface createOutletFluid(SystemInterface referenceFluid, SystemInterface prescribedInletFluid,
      TwoFluidSection[] frozenCompositionSections, double pressurePa, double temperatureK) {
    if (referenceFluid == null || !(pressurePa > 0.0) || !Double.isFinite(pressurePa) || !(temperatureK > 0.0)
        || !Double.isFinite(temperatureK) || referenceFluid.getNumberOfComponents() == 0
        || frozenCompositionSections == null || frozenCompositionSections.length != initialSections.length) {
      throw new IllegalArgumentException(
          "Frozen-phase outlet requires a fluid and positive finite pressure and temperature");
    }
    double totalMass = 0.0;
    for (double mass : outletMassKg) {
      if (mass < 0.0) {
        throw new IllegalStateException(
            "A forward outlet stream cannot publish negative phase transfer; use preparation-only diagnostics");
      }
      totalMass += mass;
    }
    if (!Double.isFinite(totalMass) || !Double.isFinite(totalMass / getElapsedTimeSeconds())) {
      throw new IllegalStateException("Frozen-phase outlet mass flow must be finite");
    }
    int count = referenceFluid.getNumberOfComponents();
    String[] names = new String[count];
    double[] molarMasses = new double[count];
    for (int component = 0; component < count; component++) {
      ComponentInterface value = referenceFluid.getPhase(0).getComponent(component);
      names[component] = value.getComponentName();
      molarMasses[component] = value.getMolarMass();
      if (!(molarMasses[component] > 0.0) || !Double.isFinite(molarMasses[component])) {
        throw new IllegalArgumentException("Component molar masses must be positive and finite");
      }
    }
    double[][] uniformFractions = new double[PHASE_COUNT][];
    for (int cell = 0; cell < initialSections.length; cell++) {
      TwoFluidSection frozen = frozenCompositionSections[cell];
      TwoFluidSection initial = initialSections[cell];
      if (frozen == null || !(frozen.getPressure() > 0.0) || !Double.isFinite(frozen.getPressure())
          || frozen.getLength() != initial.getLength() || frozen.getArea() != initial.getArea()
          || frozen.getPosition() != initial.getPosition() || frozen.getInclination() != initial.getInclination()
          || frozen.getTemperature() != initial.getTemperature()) {
        throw new IllegalArgumentException(
            "Frozen composition references must retain the accepted mesh and temperature");
      }
      SystemInterface local = flashReference(referenceFluid, frozen.getPressure(), frozen.getTemperature());
      double[][] fractions = phaseMassFractions(local, names, molarMasses);
      double[] state = initial.getStateVector();
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (state[phase] > 0.0 && fractions[phase] == null) {
          throw new IllegalStateException("Positive phase inventory requires a frozen composition in cell " + cell);
        }
      }
      mergeUniformFractions(uniformFractions, fractions);
    }
    if (prescribedInletFluid != null) {
      mergeUniformFractions(uniformFractions, phaseMassFractions(prescribedInletFluid, names, molarMasses));
    }
    double[] componentMoles = new double[count];
    double totalMoles = 0.0;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (outletMassKg[phase] > 0.0 && uniformFractions[phase] == null) {
        throw new IllegalStateException("Exported phase has no frozen composition");
      }
      if (uniformFractions[phase] != null) {
        for (int component = 0; component < count; component++) {
          componentMoles[component] += outletMassKg[phase] * uniformFractions[phase][component]
              / molarMasses[component];
        }
      }
    }
    for (double moles : componentMoles) {
      totalMoles += moles;
    }
    if (!Double.isFinite(totalMoles) || totalMass > 0.0 && !(totalMoles > 0.0)) {
      throw new IllegalStateException(
          "Frozen-phase outlet component amounts must be finite and positive for nonzero flow");
    }
    SystemInterface outlet = positiveCarrier(referenceFluid);
    if (totalMass > 0.0) {
      for (int component = 0; component < count; component++) {
        componentMoles[component] /= totalMoles;
      }
      outlet.setMolarComposition(componentMoles);
    }
    outlet.setPressure(pressurePa, "Pa");
    outlet.setTemperature(temperatureK, "K");
    try {
      new ThermodynamicOperations(outlet).TPflash();
      outlet.init(3);
      outlet.setTotalFlowRate(totalMass / getElapsedTimeSeconds(), "kg/sec");
    } catch (RuntimeException failure) {
      throw new IllegalStateException("Cannot flash the accepted frozen-phase outlet", failure);
    }
    return outlet;
  }

  private static SystemInterface flashReference(SystemInterface reference, double pressure, double temperature) {
    SystemInterface fluid = positiveCarrier(reference);
    fluid.setPressure(pressure, "Pa");
    fluid.setTemperature(temperature, "K");
    try {
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(3);
      return fluid;
    } catch (RuntimeException failure) {
      throw new IllegalStateException("Cannot prepare frozen cell phase composition", failure);
    }
  }

  private static SystemInterface positiveCarrier(SystemInterface template) {
    SystemInterface carrier = SerializationUtils.clone(template);
    double[] composition = template.getMolarComposition().clone();
    carrier.setTotalFlowRate(1.0, "mol/sec");
    carrier.setMolarComposition(composition);
    return carrier;
  }

  private static double[][] phaseMassFractions(SystemInterface fluid, String[] names, double[] molarMasses) {
    if (fluid.getNumberOfComponents() != names.length) {
      throw new IllegalArgumentException("Frozen fluids must have the same component slate");
    }
    double[][] result = new double[PHASE_COUNT][];
    for (int index = 0; index < fluid.getNumberOfPhases(); index++) {
      PhaseInterface source = fluid.getPhase(index);
      if (source == null || !Double.isFinite(source.getNumberOfMolesInPhase())
          || source.getNumberOfMolesInPhase() < 0.0) {
        throw new IllegalArgumentException("Frozen phase amounts must be finite and nonnegative");
      }
      if (source.getNumberOfMolesInPhase() == 0.0) {
        continue;
      }
      int phase = phaseSlot(source.getType());
      if (result[phase] != null) {
        throw new IllegalArgumentException("One thermodynamic phase per gas/oil/water inventory is required");
      }
      double[] masses = new double[names.length];
      double molarMass = 0.0;
      double fractionSum = 0.0;
      for (int component = 0; component < names.length; component++) {
        if (!source.hasComponent(names[component])) {
          throw new IllegalArgumentException("Frozen fluids must have the same component names");
        }
        ComponentInterface value = source.getComponent(names[component]);
        double fraction = value.getx();
        if (!Double.isFinite(fraction) || fraction < 0.0 || !Double.isFinite(value.getMolarMass())
            || Math.abs(value.getMolarMass() / molarMasses[component] - 1.0) > 1.0e-12) {
          throw new IllegalArgumentException(
              "Frozen fluids require finite normalized fractions and unchanged molar masses");
        }
        fractionSum += fraction;
        masses[component] = fraction * molarMasses[component];
        molarMass += masses[component];
      }
      if (Math.abs(fractionSum - 1.0) > COMPOSITION_TOLERANCE || !(molarMass > 0.0) || !Double.isFinite(molarMass)) {
        throw new IllegalArgumentException("Frozen phase composition must be normalized");
      }
      for (int component = 0; component < names.length; component++) {
        masses[component] /= molarMass;
      }
      result[phase] = masses;
    }
    return result;
  }

  private static void mergeUniformFractions(double[][] uniform, double[][] candidate) {
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (candidate[phase] == null) {
        continue;
      }
      if (uniform[phase] == null) {
        uniform[phase] = candidate[phase].clone();
      } else {
        for (int component = 0; component < uniform[phase].length; component++) {
          if (Math.abs(uniform[phase][component] - candidate[phase][component]) > COMPOSITION_TOLERANCE) {
            throw new IllegalStateException(
                "Nonuniform frozen phase composition requires component advection; use preparation-only diagnostics");
          }
        }
      }
    }
  }

  private static int phaseSlot(PhaseType type) {
    if (type == PhaseType.GAS) {
      return 0;
    }
    if (type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.LIQUID_ASPHALTENE) {
      return 1;
    }
    if (type == PhaseType.AQUEOUS) {
      return 2;
    }
    throw new IllegalArgumentException("Only gas, hydrocarbon-liquid and aqueous compositions are supported");
  }

  private static void validateSection(TwoFluidSection section, double tolerance, boolean allowAbsentDensityExtension) {
    if (!(section.getLength() > 0.0) || !Double.isFinite(section.getLength()) || !(section.getTemperature() > 0.0)
        || !Double.isFinite(section.getTemperature())) {
      throw new IllegalArgumentException("Publication requires positive finite cell length and temperature");
    }
    TwoFluidSection checked = section;
    if (allowAbsentDensityExtension) {
      // An absent accepted phase may have no physical density. Only this validation copy receives the same positive
      // algebraic extension used by the frozen density model; no accepted inventory, momentum or density is published.
      checked = section.clone();
      if (checked.getGasMassPerLength() == 0.0 && checked.getGasDensity() == 0.0) {
        checked.setGasDensity(1.0);
      }
      if (checked.getOilMassPerLength() == 0.0 && checked.getOilDensity() == 0.0) {
        checked.setOilDensity(1.0);
      }
      if (checked.getWaterMassPerLength() == 0.0 && checked.getWaterDensity() == 0.0) {
        checked.setWaterDensity(1.0);
      }
    }
    checked.setConservativeEndpoint(checked.getStateVector(), tolerance);
  }

  private static void requireResidual(double residual, double initial, double tolerance, String quantity) {
    if (!Double.isFinite(residual) || Math.abs(residual) / Math.max(1.0, Math.abs(initial)) > tolerance) {
      throw new IllegalStateException("Publication failed independent " + quantity + " verification");
    }
  }

  private static void requireMatrix(double[][] values, int rows, int columns, String name) {
    if (values == null || values.length != rows) {
      throw new IllegalArgumentException("Invalid " + name + " dimensions");
    }
    for (double[] row : values) {
      if (row == null || row.length != columns) {
        throw new IllegalArgumentException("Invalid " + name + " dimensions");
      }
      for (double value : row) {
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException(name + " must be finite");
        }
      }
    }
  }

  private static TwoFluidSection[] cloneSections(TwoFluidSection[] sections) {
    TwoFluidSection[] result = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      if (sections[cell] == null) {
        throw new IllegalArgumentException("Every publication cell is required");
      }
      result[cell] = sections[cell].clone();
    }
    return result;
  }
}

package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PhaseDensityModel;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Isothermal SRK/PR phase-density closure anchored to accepted cell densities.
 *
 * <p>
 * For each cell and supplied gas, oil or aqueous phase, the accepted phase composition and cell temperature are frozen.
 * At a trial absolute pressure {@code p}, the mass-specific volume is
 * {@code v(p) = 1/rhoEOS(p) + 1/rhoAccepted - 1/rhoEOS(pAccepted)} and the returned density is {@code 1/v(p)}. This
 * constant specific-volume offset preserves the cubic EOS volume derivative and exactly reproduces the accepted
 * density. It is an anchored density model, not a new equilibrium flash or an implementation of a general translated
 * EOS. The offset is constant only for this cell's frozen composition and temperature.
 * </p>
 *
 * <p>
 * Only the concrete {@link PhaseSrkEos} and {@link PhasePrEos} classes are supported. Phase snapshots, including mixing
 * parameters, are copied independently on construction. Each calculation initializes a fresh phase clone at the
 * requested pressure using the accepted gas or liquid root selection; no flash, phase transfer, or accepted-fluid
 * mutation occurs. Nonpositive EOS compressibility, density or translated volume is rejected. Mechanical stability
 * alone does not establish equilibrium stability or experimental validity. Root continuation through a critical point
 * or spinodal is not qualified by this closure.
 * </p>
 *
 * <p>
 * An unavailable phase retains the caller's explicit positive accepted density for algebraic residual and Jacobian
 * probes. That density does not establish a physical phase or its composition. Callers must use {@link #hasPhase} to
 * reject accepted appearance or inflow of an unavailable phase. Component transport, phase-composition mixing and
 * thermal evolution are outside this model: in particular, different frozen compositions in adjacent cells do not imply
 * that their later mixing is modeled. Rebuild the closure when the accepted thermodynamic description changes.
 * </p>
 */
public final class AnchoredIsothermalDensityModel implements PhaseDensityModel, Cloneable {
  private static final long serialVersionUID = 1L;
  private static final int PHASE_COUNT = 3;
  private static final int STATE_SIZE = 7;
  private static final double PASCALS_PER_BAR = 1.0e5;

  private final PhaseInterface[][] templates;
  private final double[][] acceptedDensities;
  private final double[][] specificVolumeOffsets;
  private final double[][] referenceCompressibilities;
  private final double[] acceptedPressures;

  /**
   * Snapshot local accepted phase compositions and calibrate their density response.
   *
   * <p>
   * Systems must already contain the intended local phase compositions; this constructor performs no equilibrium flash.
   * The corresponding section supplies the reference pressure, fixed temperature, three reference densities, and
   * accepted phase inventories. A single thermodynamic phase per gas/oil/water inventory is supported. All three
   * reference densities must be supplied explicitly, including those for absent phases.
   * </p>
   *
   * @param acceptedSections accepted cells with pressure in Pa, temperature in K and densities in kg/m3
   * @param acceptedCellFluids local initialized phase compositions, one system per accepted cell
   * @throws IllegalArgumentException for invalid cells, densities, compositions, unsupported EOS/phase types, duplicate
   * phase inventories, or a positive accepted inventory without a phase template
   */
  public AnchoredIsothermalDensityModel(TwoFluidSection[] acceptedSections, SystemInterface[] acceptedCellFluids) {
    if (acceptedSections == null || acceptedSections.length == 0 || acceptedCellFluids == null
        || acceptedCellFluids.length != acceptedSections.length) {
      throw new IllegalArgumentException("Accepted sections and local fluids must have the same nonzero length");
    }
    int cells = acceptedSections.length;
    templates = new PhaseInterface[cells][PHASE_COUNT];
    acceptedDensities = new double[cells][PHASE_COUNT];
    specificVolumeOffsets = new double[cells][PHASE_COUNT];
    referenceCompressibilities = new double[cells][PHASE_COUNT];
    acceptedPressures = new double[cells];

    for (int cell = 0; cell < cells; cell++) {
      TwoFluidSection section = acceptedSections[cell];
      SystemInterface fluid = acceptedCellFluids[cell];
      if (section == null || fluid == null || fluid.getNumberOfPhases() < 1) {
        throw new IllegalArgumentException("Every accepted cell requires a section and initialized local fluid");
      }
      requirePositiveFinite(section.getPressure(), "Accepted pressure");
      requirePositiveFinite(section.getTemperature(), "Accepted temperature");
      acceptedPressures[cell] = section.getPressure();
      acceptedDensities[cell] = new double[] { section.getGasDensity(), section.getOilDensity(),
          section.getWaterDensity() };
      for (double density : acceptedDensities[cell]) {
        requirePositiveFinite(density, "Every explicit accepted phase density");
      }

      for (int index = 0; index < fluid.getNumberOfPhases(); index++) {
        PhaseInterface source = fluid.getPhase(index);
        if (source == null || !Double.isFinite(source.getNumberOfMolesInPhase())
            || source.getNumberOfMolesInPhase() < 0.0) {
          throw new IllegalArgumentException("Local fluid phases must have finite nonnegative mole inventories");
        }
        if (source.getNumberOfMolesInPhase() == 0.0) {
          continue;
        }
        int phase = phaseSlot(source.getType());
        if (templates[cell][phase] != null) {
          throw new IllegalArgumentException("Multiple thermodynamic phases in one conserved phase are unsupported");
        }
        if (source.getClass() != PhaseSrkEos.class && source.getClass() != PhasePrEos.class) {
          throw new IllegalArgumentException("Only concrete PhaseSrkEos and PhasePrEos phases are supported");
        }
        PhaseInterface template = snapshot(source, section.getPressure(), section.getTemperature());
        double eosDensity = template.getDensity();
        double eosCompressibility = template.getIsothermalCompressibility() / PASCALS_PER_BAR;
        double density = acceptedDensities[cell][phase];
        specificVolumeOffsets[cell][phase] = 1.0 / density - 1.0 / eosDensity;
        referenceCompressibilities[cell][phase] = density / eosDensity * eosCompressibility;
        if (!Double.isFinite(specificVolumeOffsets[cell][phase])) {
          throw new IllegalArgumentException("The accepted specific-volume offset must be finite");
        }
        requirePositiveFinite(referenceCompressibilities[cell][phase], "Anchored isothermal compressibility");
        templates[cell][phase] = template;
      }

      double[] state = section.getStateVector();
      validateState(state);
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        if (state[phase] < 0.0 || state[phase] > 0.0 && templates[cell][phase] == null) {
          throw new IllegalArgumentException("Positive accepted phase mass requires a local phase composition");
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[] calculate(int cell, double[] conservativeState, double pressure, double time) {
    checkCell(cell);
    validateState(conservativeState);
    requirePositiveFinite(pressure, "Trial pressure");
    if (!Double.isFinite(time)) {
      throw new IllegalArgumentException("Coefficient-evaluation time must be finite");
    }
    double[] result = acceptedDensities[cell].clone();
    if (pressure == acceptedPressures[cell]) {
      return result;
    }
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      PhaseInterface template = templates[cell][phase];
      if (template == null) {
        continue;
      }
      PhaseInterface trial = template.clone();
      initialize(trial, pressure, template.getTemperature(), template.getType());
      double volume = 1.0 / trial.getDensity() + specificVolumeOffsets[cell][phase];
      requirePositiveFinite(volume, "Trial anchored specific volume");
      result[phase] = 1.0 / volume;
      requirePositiveFinite(result[phase], "Trial anchored density");
    }
    return result;
  }

  /**
   * Return the number of snapshotted cells.
   *
   * @return number of cells
   */
  public int getNumberOfCells() {
    return templates.length;
  }

  /**
   * Check whether a cell has an actual composition template for one conserved phase.
   *
   * @param cell cell index
   * @param phase conserved phase index: 0 gas, 1 oil, 2 water
   * @return whether pressure-dependent EOS density is available for the phase
   * @throws IllegalArgumentException for an invalid cell or phase index
   */
  public boolean hasPhase(int cell, int phase) {
    checkIndices(cell, phase);
    return templates[cell][phase] != null;
  }

  /**
   * Return the reference derivative {@code (1/rho) d(rho)/d(p)} of the anchored model at fixed temperature.
   *
   * @param cell cell index
   * @param phase conserved phase index: 0 gas, 1 oil, 2 water
   * @return reference isothermal compressibility in 1/Pa, or zero for an unavailable phase's constant residual density
   * @throws IllegalArgumentException for an invalid cell or phase index
   */
  public double getReferenceIsothermalCompressibility(int cell, int phase) {
    checkIndices(cell, phase);
    return referenceCompressibilities[cell][phase];
  }

  /**
   * Return an independent copy, including all thermodynamic snapshots and mixing parameters.
   *
   * @return independent density model
   */
  @Override
  public AnchoredIsothermalDensityModel clone() {
    return independentCopy(this);
  }

  /**
   * Create a one-mole snapshot without changing its phase composition.
   *
   * @param source initialized source phase
   * @param pressure reference pressure in Pa
   * @param temperature fixed cell temperature in K
   * @return independent initialized snapshot
   */
  private static PhaseInterface snapshot(PhaseInterface source, double pressure, double temperature) {
    PhaseInterface snapshot = source.clone();
    snapshot.resetPhysicalProperties();
    snapshot = independentCopy(snapshot);
    int components = snapshot.getNumberOfComponents();
    double moleFractionSum = 0.0;
    for (int component = 0; component < components; component++) {
      ComponentInterface value = snapshot.getComponent(component);
      double fraction = value.getx();
      if (!Double.isFinite(fraction) || fraction < 0.0) {
        throw new IllegalArgumentException("Phase mole fractions must be nonnegative and finite");
      }
      moleFractionSum += fraction;
      value.setz(fraction);
      value.setNumberOfmoles(fraction);
      value.setNumberOfMolesInPhase(fraction);
    }
    if (components == 0 || Math.abs(moleFractionSum - 1.0) > 1.0e-8) {
      throw new IllegalArgumentException("Local phase composition must already be normalized");
    }
    snapshot.setConstantPhaseVolume(false);
    snapshot.calcMolarVolume(true);
    initialize(snapshot, pressure, temperature, source.getType());
    return snapshot;
  }

  /**
   * Recalculate only the frozen-composition phase's EOS response and check mechanical stability.
   *
   * @param phase owned phase clone
   * @param pressure absolute pressure in Pa
   * @param temperature fixed temperature in K
   * @param identity accepted thermodynamic phase identity
   */
  private static void initialize(PhaseInterface phase, double pressure, double temperature, PhaseType identity) {
    double pressureBar = pressure / PASCALS_PER_BAR;
    requirePositiveFinite(pressureBar, "EOS pressure in bar");
    phase.setPressure(pressureBar);
    phase.setTemperature(temperature);
    // One mole avoids the EOS's near-empty-phase ideal-gas fallback without changing composition.
    phase.init(1.0, phase.getNumberOfComponents(), 1, identity, 1.0);
    // PhaseEos may relabel its root; the conserved inventory retains its accepted identity.
    phase.setType(identity);
    requirePositiveFinite(phase.getDensity(), "Frozen-composition EOS density");
    requirePositiveFinite(phase.getIsothermalCompressibility() / PASCALS_PER_BAR,
        "Frozen-composition EOS isothermal compressibility");
  }

  /**
   * Map a thermodynamic identity to the conserved gas/oil/water inventory.
   *
   * @param type thermodynamic phase identity
   * @return conserved phase index
   */
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
    throw new IllegalArgumentException("Only gas, hydrocarbon-liquid and aqueous phase inventories are supported");
  }

  /**
   * Check a seven-column conservative state without restricting algebraic mass probes.
   *
   * @param state conservative probe or accepted state
   */
  private static void validateState(double[] state) {
    if (state == null || state.length != STATE_SIZE) {
      throw new IllegalArgumentException("A seven-column conservative state is required");
    }
    for (double value : state) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Conservative state must be finite");
      }
    }
  }

  /**
   * Check a cell index.
   *
   * @param cell cell index
   */
  private void checkCell(int cell) {
    if (cell < 0 || cell >= templates.length) {
      throw new IllegalArgumentException("Cell index is outside the density model");
    }
  }

  /**
   * Check a cell and conserved-phase index.
   *
   * @param cell cell index
   * @param phase conserved-phase index
   */
  private void checkIndices(int cell, int phase) {
    checkCell(cell);
    if (phase < 0 || phase >= PHASE_COUNT) {
      throw new IllegalArgumentException("Phase index must be 0 (gas), 1 (oil), or 2 (water)");
    }
  }

  /**
   * Check an explicit positive finite physical quantity.
   *
   * @param value quantity
   * @param name descriptive quantity name
   */
  private static void requirePositiveFinite(double value, String name) {
    if (!(value > 0.0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Copy a serializable object graph, including fields shallow-copied by ordinary EOS clones.
   *
   * @param value source object
   * @param <T> serializable object type
   * @return independent object graph
   */
  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T independentCopy(T value) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(value);
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        return (T) input.readObject();
      }
    } catch (IOException | ClassNotFoundException ex) {
      throw new IllegalArgumentException("Could not create an independent thermodynamic snapshot", ex);
    }
  }
}

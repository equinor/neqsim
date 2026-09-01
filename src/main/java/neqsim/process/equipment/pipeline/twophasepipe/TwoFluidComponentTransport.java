package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Conservative per-cell, per-phase named-component transport coupled to accepted TwoFluidPipe phase fluxes. */
public final class TwoFluidComponentTransport implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int GAS = 0;
  private static final int OIL = 1;
  private static final int WATER = 2;
  private static final int PHASE_COUNT = 3;
  private static final double MASS_FLOOR_KG = 1.0e-12;
  private static final double FLOW_DIRECTION_TOLERANCE_KG_S = 1.0e-12;

  private final String[] componentNames;
  private final double[] componentMolarMassKgMol;
  private final int cellCount;
  private double[][][] componentInventoryKg;
  private double[] intervalInitialInventoryKg;
  private double[] intervalInletMassKg;
  private double[] intervalOutletMassKg;
  private double[][] intervalInterphaseTransferKg;
  private double[][][] intervalCellInterphaseTransferKg;
  private double intervalLatentHeatEnergyJ;
  private double maximumPhaseMassSynchronizationErrorKg;

  /**
   * Initialize component inventories from phase identities in a flashed thermodynamic template.
   *
   * @param fluidTemplate inlet/reference fluid
   * @param sections initialized hydrodynamic cells
   */
  public TwoFluidComponentTransport(SystemInterface fluidTemplate, TwoFluidSection[] sections) {
    if (fluidTemplate == null || sections == null || sections.length == 0) {
      throw new IllegalArgumentException("Component transport requires a fluid template and at least one section");
    }
    SystemInterface prepared = prepareFluid(fluidTemplate, "initial component transport state");
    componentNames = sortedComponentNames(prepared);
    if (componentNames.length < 2) {
      throw new IllegalArgumentException("Component-resolved transport requires at least two components");
    }
    componentMolarMassKgMol = componentMolarMasses(prepared, componentNames);
    cellCount = sections.length;
    componentInventoryKg = new double[cellCount][PHASE_COUNT][componentNames.length];
    for (int cell = 0; cell < cellCount; cell++) {
      SystemInterface localFluid = prepared.clone();
      localFluid.setPressure(sections[cell].getPressure() / 1.0e5, "bara");
      localFluid.setTemperature(sections[cell].getTemperature(), "K");
      localFluid = prepareFluid(localFluid, "initial component state for cell " + cell);
      double[][] initialPhaseFractions = phaseMassFractions(localFluid);
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double phaseMassKg = phaseMassKg(sections[cell], phase);
        if (phaseMassKg > MASS_FLOOR_KG && sum(initialPhaseFractions[phase]) <= 0.0) {
          throw new IllegalArgumentException("Unsupported initial phase/component slate in cell " + cell
              + ": hydrodynamic phase " + phaseName(phase) + " has mass but no matching thermodynamic phase");
        }
        for (int component = 0; component < componentNames.length; component++) {
          componentInventoryKg[cell][phase][component] = phaseMassKg * initialPhaseFractions[phase][component];
        }
      }
    }
    beginInterval();
  }

  /** Reset per-call boundary and interphase ledgers while retaining distributed inventories. */
  public void beginInterval() {
    intervalInitialInventoryKg = totalComponentInventory();
    intervalInletMassKg = new double[componentNames.length];
    intervalOutletMassKg = new double[componentNames.length];
    intervalInterphaseTransferKg = new double[PHASE_COUNT][componentNames.length];
    intervalCellInterphaseTransferKg = new double[cellCount][PHASE_COUNT][componentNames.length];
    intervalLatentHeatEnergyJ = 0.0;
    maximumPhaseMassSynchronizationErrorKg = 0.0;
  }

  /**
   * Advance components through one accepted hydrodynamic substep.
   *
   * @param timeStepSeconds accepted substep duration
   * @param phaseMassFaceFluxKgS stage-weighted gas/oil/water face mass flows
   * @param phaseMassSourceKgPerMetreSecond stage-weighted cell phase sources
   * @param sections accepted hydrodynamic cell states
   * @param inletFluid current inlet boundary fluid
   * @param fluidTemplate thermodynamic template used for cell flashes
   * @param tolerance relative conservation/synchronization tolerance
   * @return composition-dependent latent heat added in each cell over the accepted step, in joules
   */
  public double[] advance(double timeStepSeconds, double[][] phaseMassFaceFluxKgS,
      double[][] phaseMassSourceKgPerMetreSecond, TwoFluidSection[] sections, SystemInterface inletFluid,
      SystemInterface fluidTemplate, double tolerance) {
    validateAdvanceArguments(timeStepSeconds, phaseMassFaceFluxKgS, phaseMassSourceKgPerMetreSecond, sections,
        tolerance);
    if (fluidTemplate == null) {
      throw new IllegalArgumentException("Fluid template cannot be null for component transport");
    }
    SystemInterface preparedInlet = prepareFluid(inletFluid, "component inlet boundary");
    validateComponentSlate(preparedInlet);
    double[][] inletPhaseFractions = phaseMassFractions(preparedInlet);
    double[][][] oldFractions = currentMassFractions();
    double[][][] updated = copy(componentInventoryKg);
    double[] latentHeatEnergyByCellJ = new double[cellCount];

    for (int face = 0; face <= cellCount; face++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double phaseFlowKgS = phaseMassFaceFluxKgS[face][phase];
        if (phaseFlowKgS >= -FLOW_DIRECTION_TOLERANCE_KG_S) {
          advectPositiveFace(timeStepSeconds, face, phase, Math.max(0.0, phaseFlowKgS), inletPhaseFractions,
              oldFractions, updated);
        } else {
          advectNegativeFace(timeStepSeconds, face, phase, -phaseFlowKgS, oldFractions, updated);
        }
      }
    }

    for (int cell = 0; cell < cellCount; cell++) {
      double[] phaseTransferKg = new double[PHASE_COUNT];
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        phaseTransferKg[phase] = phaseMassSourceKgPerMetreSecond[cell][phase] * sections[cell].getLength()
            * timeStepSeconds;
      }
      double transferScale = Math.max(MASS_FLOOR_KG, Math.max(Math.abs(phaseTransferKg[GAS]),
          Math.max(Math.abs(phaseTransferKg[OIL]), Math.abs(phaseTransferKg[WATER]))));
      if (Math.abs(sum(phaseTransferKg)) > tolerance * transferScale) {
        throw new IllegalStateException("Hydrodynamic interphase sources do not sum to zero in cell " + cell);
      }
      double[][] componentTransfer = allocateComponentTransfer(cell, phaseTransferKg, updated, sections, fluidTemplate);
      latentHeatEnergyByCellJ[cell] = calculateLatentHeatEnergyJ(cell, componentTransfer, updated, sections,
          fluidTemplate);
      intervalLatentHeatEnergyJ += latentHeatEnergyByCellJ[cell];
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        for (int component = 0; component < componentNames.length; component++) {
          updated[cell][phase][component] += componentTransfer[phase][component];
          intervalInterphaseTransferKg[phase][component] += componentTransfer[phase][component];
          intervalCellInterphaseTransferKg[cell][phase][component] += componentTransfer[phase][component];
        }
      }
    }

    synchronizeWithHydrodynamicPhaseMass(updated, sections, tolerance);
    componentInventoryKg = updated;
    return latentHeatEnergyByCellJ;
  }

  private void advectPositiveFace(double timeStepSeconds, int face, int phase, double phaseFlowKgS,
      double[][] inletPhaseFractions, double[][][] oldFractions, double[][][] updated) {
    double[] donorFractions = face == 0 ? inletPhaseFractions[phase] : oldFractions[face - 1][phase];
    validateDonorComposition(donorFractions, phaseFlowKgS, face, phase);
    for (int component = 0; component < componentNames.length; component++) {
      double transportedMassKg = timeStepSeconds * phaseFlowKgS * donorFractions[component];
      if (face > 0) {
        updated[face - 1][phase][component] -= transportedMassKg;
      } else {
        intervalInletMassKg[component] += transportedMassKg;
      }
      if (face < cellCount) {
        updated[face][phase][component] += transportedMassKg;
      } else {
        intervalOutletMassKg[component] += transportedMassKg;
      }
    }
  }

  private void advectNegativeFace(double timeStepSeconds, int face, int phase, double phaseFlowKgS,
      double[][][] oldFractions, double[][][] updated) {
    if (face == 0) {
      throw new IllegalStateException("Unsupported component outflow through the inlet boundary in " + phaseName(phase)
          + ": positive inlet flow is required when component transport is enabled");
    }
    if (face == cellCount) {
      throw new IllegalStateException("Unsupported component inflow through the outlet boundary in " + phaseName(phase)
          + ": provide a validated outlet composition before enabling reverse boundary flow");
    }
    double[] donorFractions = oldFractions[face][phase];
    validateDonorComposition(donorFractions, phaseFlowKgS, face, phase);
    for (int component = 0; component < componentNames.length; component++) {
      double transportedMassKg = timeStepSeconds * phaseFlowKgS * donorFractions[component];
      updated[face][phase][component] -= transportedMassKg;
      if (face > 0) {
        updated[face - 1][phase][component] += transportedMassKg;
      }
    }
  }

  private void validateDonorComposition(double[] donorFractions, double phaseFlowKgS, int face, int phase) {
    if (phaseFlowKgS > FLOW_DIRECTION_TOLERANCE_KG_S && sum(donorFractions) <= 0.0) {
      throw new IllegalStateException(
          "Boundary/face flow has no " + phaseName(phase) + " component composition at face " + face);
    }
  }

  /**
   * Construct a flashed thermodynamic state from the conservative total cell component inventory.
   *
   * @param cellIndex cell index
   * @param fluidTemplate compatible NeqSim thermodynamic template
   * @param pressurePa cell pressure in Pa
   * @param temperatureK cell temperature in K
   * @return flashed cell state with component identity taken from conservative inventories
   */
  public SystemInterface createThermodynamicState(int cellIndex, SystemInterface fluidTemplate, double pressurePa,
      double temperatureK) {
    if (cellIndex < 0 || cellIndex >= cellCount) {
      throw new IllegalArgumentException("Cell index is outside the component grid");
    }
    if (fluidTemplate == null) {
      throw new IllegalArgumentException("Fluid template cannot be null for thermodynamic synchronization");
    }
    validateComponentSlate(fluidTemplate);
    double[] componentMassKg = new double[componentNames.length];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int component = 0; component < componentNames.length; component++) {
        componentMassKg[component] += componentInventoryKg[cellIndex][phase][component];
      }
    }
    return flashWithComponentMasses(fluidTemplate, componentMassKg, pressurePa, temperatureK,
        "thermodynamic synchronization for cell " + cellIndex);
  }

  /**
   * Build the immutable diagnostic report for the current outer transient call.
   *
   * @param elapsedTimeSeconds accepted elapsed time
   * @param acceptedSubsteps number of accepted hydrodynamic substeps
   * @param tolerance conservation and boundedness tolerance
   * @return immutable component report
   */
  public TwoFluidComponentConservationReport createReport(double elapsedTimeSeconds, int acceptedSubsteps,
      double tolerance) {
    double[] finalInventory = totalComponentInventory();
    double[] residual = new double[componentNames.length];
    double[] relativeResidual = new double[componentNames.length];
    double maximumRelativeResidual = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      residual[component] = finalInventory[component] - intervalInitialInventoryKg[component]
          - intervalInletMassKg[component] + intervalOutletMassKg[component];
      double scale = Math.max(MASS_FLOOR_KG,
          Math.max(Math.max(Math.abs(intervalInitialInventoryKg[component]), Math.abs(finalInventory[component])),
              Math.max(Math.abs(intervalInletMassKg[component]), Math.abs(intervalOutletMassKg[component]))));
      relativeResidual[component] = residual[component] / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, Math.abs(relativeResidual[component]));
    }

    double maximumTransferResidualKg = 0.0;
    for (int cell = 0; cell < cellCount; cell++) {
      for (int component = 0; component < componentNames.length; component++) {
        double transferResidual = 0.0;
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          transferResidual += intervalCellInterphaseTransferKg[cell][phase][component];
        }
        maximumTransferResidualKg = Math.max(maximumTransferResidualKg, Math.abs(transferResidual));
      }
    }

    double[][] finalPhaseInventory = phaseComponentInventories();
    double[][][] profiles = reportProfiles();
    double minimumFraction = Double.POSITIVE_INFINITY;
    double maximumFraction = Double.NEGATIVE_INFINITY;
    double maximumSumError = 0.0;
    boolean foundNonEmptyPhase = false;
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double phaseMass = 0.0;
        for (int component = 0; component < componentNames.length; component++) {
          phaseMass += componentInventoryKg[cell][phase][component];
        }
        if (phaseMass <= MASS_FLOOR_KG) {
          continue;
        }
        foundNonEmptyPhase = true;
        double fractionSum = 0.0;
        for (int component = 0; component < componentNames.length; component++) {
          double fraction = profiles[phase][component][cell];
          minimumFraction = Math.min(minimumFraction, fraction);
          maximumFraction = Math.max(maximumFraction, fraction);
          fractionSum += fraction;
        }
        maximumSumError = Math.max(maximumSumError, Math.abs(fractionSum - 1.0));
      }
    }
    if (!foundNonEmptyPhase) {
      minimumFraction = Double.NaN;
      maximumFraction = Double.NaN;
    }
    boolean bounded = foundNonEmptyPhase && minimumFraction >= -tolerance && maximumFraction <= 1.0 + tolerance
        && maximumSumError <= tolerance;
    double transferScale = Math.max(MASS_FLOOR_KG, maximumAbsolute(intervalInterphaseTransferKg));
    boolean transferConverged = maximumTransferResidualKg <= tolerance * transferScale;
    double synchronizationScale = Math.max(MASS_FLOOR_KG, sum(finalInventory));
    boolean synchronizationConverged = maximumPhaseMassSynchronizationErrorKg <= tolerance * synchronizationScale;
    boolean converged = maximumRelativeResidual <= tolerance && transferConverged && synchronizationConverged
        && bounded;
    String message = "TwoFluidPipe component transport " + (converged ? "converged" : "failed")
        + "; maximum relative component residual=" + maximumRelativeResidual + ", maximum cell interphase residual="
        + maximumTransferResidualKg + " kg, maximum phase synchronization error="
        + maximumPhaseMassSynchronizationErrorKg + " kg.";
    return new TwoFluidComponentConservationReport(elapsedTimeSeconds, acceptedSubsteps, componentNames,
        intervalInitialInventoryKg, finalInventory, intervalInletMassKg, intervalOutletMassKg, residual,
        relativeResidual, maximumRelativeResidual, finalPhaseInventory, intervalInterphaseTransferKg,
        maximumTransferResidualKg, profiles, minimumFraction, maximumFraction, maximumSumError,
        maximumPhaseMassSynchronizationErrorKg, intervalLatentHeatEnergyJ, converged, message);
  }

  /**
   * Get one current phase/component profile.
   *
   * @param phaseIndex gas=0, oil=1, water=2
   * @param componentName component name
   * @return defensive mass-fraction profile
   */
  public double[] getMassFractionProfile(int phaseIndex, String componentName) {
    if (phaseIndex < 0 || phaseIndex >= PHASE_COUNT) {
      throw new IllegalArgumentException("Phase index must be gas=0, oil=1, or water=2");
    }
    int component = componentIndex(componentName);
    double[] result = new double[cellCount];
    for (int cell = 0; cell < cellCount; cell++) {
      double phaseMass = phaseInventory(componentInventoryKg[cell][phaseIndex]);
      result[cell] = phaseMass <= MASS_FLOOR_KG ? 0.0 : componentInventoryKg[cell][phaseIndex][component] / phaseMass;
    }
    return result;
  }

  /** @return defensive deterministic component-name array */
  public String[] getComponentNames() {
    return Arrays.copyOf(componentNames, componentNames.length);
  }

  private double[][] allocateComponentTransfer(int cell, double[] phaseTransferKg, double[][][] updated,
      TwoFluidSection[] sections, SystemInterface fluidTemplate) {
    double[][] transfer = new double[PHASE_COUNT][componentNames.length];
    double gasTransfer = phaseTransferKg[GAS];
    if ((phaseTransferKg[OIL] > MASS_FLOOR_KG && phaseTransferKg[WATER] < -MASS_FLOOR_KG)
        || (phaseTransferKg[OIL] < -MASS_FLOOR_KG && phaseTransferKg[WATER] > MASS_FLOOR_KG)) {
      throw new IllegalStateException("Direct oil-water component transfer is outside the validated closure");
    }
    if (gasTransfer > 0.0) {
      for (int donorPhase : new int[] { OIL, WATER }) {
        double withdrawalKg = Math.max(0.0, -phaseTransferKg[donorPhase]);
        if (withdrawalKg <= 0.0) {
          continue;
        }
        double donorMass = phaseInventory(updated[cell][donorPhase]);
        if (donorMass <= MASS_FLOOR_KG) {
          throw new IllegalStateException(
              "Unsupported evaporation from empty " + phaseName(donorPhase) + " component inventory in cell " + cell);
        }
        for (int component = 0; component < componentNames.length; component++) {
          double componentMass = withdrawalKg * updated[cell][donorPhase][component] / donorMass;
          transfer[donorPhase][component] -= componentMass;
          transfer[GAS][component] += componentMass;
        }
      }
    } else if (gasTransfer < 0.0) {
      SystemInterface equilibrium = createThermodynamicStateFrom(updated, cell, fluidTemplate,
          sections[cell].getPressure(), sections[cell].getTemperature(), "condensation allocation in cell " + cell);
      double[][] equilibriumFractions = phaseMassFractions(equilibrium);
      for (int receivingPhase : new int[] { OIL, WATER }) {
        double additionKg = Math.max(0.0, phaseTransferKg[receivingPhase]);
        if (additionKg <= 0.0) {
          continue;
        }
        if (sum(equilibriumFractions[receivingPhase]) <= 0.0) {
          throw new IllegalStateException("Unsupported phase appearance: flash provides no " + phaseName(receivingPhase)
              + " composition in cell " + cell);
        }
        for (int component = 0; component < componentNames.length; component++) {
          double componentMass = additionKg * equilibriumFractions[receivingPhase][component];
          transfer[receivingPhase][component] += componentMass;
          transfer[GAS][component] -= componentMass;
        }
      }
    } else if (Math.abs(phaseTransferKg[OIL]) > MASS_FLOOR_KG || Math.abs(phaseTransferKg[WATER]) > MASS_FLOOR_KG) {
      throw new IllegalStateException("Direct oil-water component transfer is outside the validated closure");
    }
    return transfer;
  }

  private double calculateLatentHeatEnergyJ(int cell, double[][] componentTransferKg, double[][][] inventories,
      TwoFluidSection[] sections, SystemInterface fluidTemplate) {
    double transferredMassScaleKg = maximumAbsolute(componentTransferKg);
    if (transferredMassScaleKg <= MASS_FLOOR_KG) {
      return 0.0;
    }
    SystemInterface localFluid = createThermodynamicStateFrom(inventories, cell, fluidTemplate,
        sections[cell].getPressure(), sections[cell].getTemperature(), "latent-enthalpy closure in cell " + cell);
    double[][] componentSpecificEnthalpyJkg = componentSpecificEnthalpies(localFluid);
    double phaseFormationEnthalpyJ = 0.0;
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int component = 0; component < componentNames.length; component++) {
        double transferredComponentMassKg = componentTransferKg[phase][component];
        if (Math.abs(transferredComponentMassKg) <= MASS_FLOOR_KG) {
          continue;
        }
        if (!Double.isFinite(componentSpecificEnthalpyJkg[phase][component])) {
          throw new IllegalStateException("Unsupported latent-enthalpy closure: component '" + componentNames[component]
              + "' has no partial enthalpy in thermodynamic " + phaseName(phase)
              + " phase while mass transfers in cell " + cell);
        }
        phaseFormationEnthalpyJ += transferredComponentMassKg * componentSpecificEnthalpyJkg[phase][component];
      }
    }
    // A negative isothermal phase-formation enthalpy releases positive sensible heat.
    return -phaseFormationEnthalpyJ;
  }

  private double[][] componentSpecificEnthalpies(SystemInterface fluid) {
    double[][] result = new double[PHASE_COUNT][componentNames.length];
    for (double[] phaseValues : result) {
      Arrays.fill(phaseValues, Double.NaN);
    }
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      int identity = phaseIndex(phase.getType());
      if (identity >= 0 && phase.getNumberOfMolesInPhase() > 1.0e-20) {
        for (int phaseComponent = 0; phaseComponent < phase.getNumberOfComponents(); phaseComponent++) {
          int component = componentIndex(phase.getComponent(phaseComponent).getComponentName());
          double partialMolarEnthalpyJmol = phase.getComponent(phaseComponent).getHID(phase.getTemperature())
              + phase.getComponent(phaseComponent).getHresTP(phase.getTemperature());
          result[identity][component] = partialMolarEnthalpyJmol / componentMolarMassKgMol[component];
        }
      }
    }
    return result;
  }

  private void synchronizeWithHydrodynamicPhaseMass(double[][][] updated, TwoFluidSection[] sections,
      double tolerance) {
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double targetMassKg = phaseMassKg(sections[cell], phase);
        double componentMassKg = phaseInventory(updated[cell][phase]);
        double errorKg = componentMassKg - targetMassKg;
        maximumPhaseMassSynchronizationErrorKg = Math.max(maximumPhaseMassSynchronizationErrorKg, Math.abs(errorKg));
        double scale = Math.max(MASS_FLOOR_KG, Math.max(Math.abs(componentMassKg), Math.abs(targetMassKg)));
        if (Math.abs(errorKg) > tolerance * scale) {
          throw new IllegalStateException("Component sum and hydrodynamic " + phaseName(phase) + " mass differ in cell "
              + cell + " by " + errorKg + " kg");
        }
        for (int component = 0; component < componentNames.length; component++) {
          if (updated[cell][phase][component] < -tolerance * scale) {
            throw new IllegalStateException("Negative component inventory for '" + componentNames[component] + "' in "
                + phaseName(phase) + " cell " + cell);
          }
          updated[cell][phase][component] = Math.max(0.0, updated[cell][phase][component]);
        }
        componentMassKg = phaseInventory(updated[cell][phase]);
        if (targetMassKg <= MASS_FLOOR_KG) {
          Arrays.fill(updated[cell][phase], 0.0);
        } else if (componentMassKg > 0.0) {
          double correction = targetMassKg / componentMassKg;
          for (int component = 0; component < componentNames.length; component++) {
            updated[cell][phase][component] *= correction;
          }
        } else {
          throw new IllegalStateException(
              "Positive hydrodynamic " + phaseName(phase) + " mass has no component inventory in cell " + cell);
        }
      }
    }
  }

  private SystemInterface createThermodynamicStateFrom(double[][][] inventories, int cell, SystemInterface template,
      double pressurePa, double temperatureK, String context) {
    double[] componentMassKg = new double[componentNames.length];
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      for (int component = 0; component < componentNames.length; component++) {
        componentMassKg[component] += inventories[cell][phase][component];
      }
    }
    return flashWithComponentMasses(template, componentMassKg, pressurePa, temperatureK, context);
  }

  private SystemInterface flashWithComponentMasses(SystemInterface template, double[] componentMassKg,
      double pressurePa, double temperatureK, String context) {
    validateComponentSlate(template);
    double[] componentMoles = new double[componentNames.length];
    double totalMoles = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      componentMoles[component] = componentMassKg[component] / componentMolarMassKgMol[component];
      totalMoles += componentMoles[component];
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      throw new IllegalStateException("Cannot flash an empty/non-finite component inventory for " + context);
    }
    SystemInterface fluid = template.clone();
    double[] templateComposition = new double[fluid.getNumberOfComponents()];
    for (int templateIndex = 0; templateIndex < fluid.getNumberOfComponents(); templateIndex++) {
      String componentName = fluid.getPhase(0).getComponent(templateIndex).getComponentName();
      templateComposition[templateIndex] = componentMoles[componentIndex(componentName)] / totalMoles;
    }
    fluid.setMolarComposition(templateComposition);
    fluid.setPressure(pressurePa / 1.0e5, "bara");
    fluid.setTemperature(temperatureK, "K");
    return prepareFluid(fluid, context);
  }

  private double[][] phaseMassFractions(SystemInterface fluid) {
    double[][] componentMass = new double[PHASE_COUNT][componentNames.length];
    double[] phaseMass = new double[PHASE_COUNT];
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      int identity = phaseIndex(phase.getType());
      double moles = Math.max(0.0, phase.getNumberOfMolesInPhase());
      if (identity < 0) {
        if (moles > 1.0e-20) {
          throw new IllegalArgumentException("Unsupported thermodynamic phase identity " + phase.getType());
        }
        continue;
      }
      double[] x = phase.getMolarComposition();
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        String componentName = phase.getComponent(componentIndex).getComponentName();
        int component = componentIndex(componentName);
        double mass = moles * x[componentIndex] * phase.getComponent(componentIndex).getMolarMass();
        componentMass[identity][component] += mass;
        phaseMass[identity] += mass;
      }
    }
    for (int phase = 0; phase < PHASE_COUNT; phase++) {
      if (phaseMass[phase] > 0.0) {
        for (int component = 0; component < componentNames.length; component++) {
          componentMass[phase][component] /= phaseMass[phase];
        }
      }
    }
    return componentMass;
  }

  private void validateAdvanceArguments(double timeStepSeconds, double[][] faces, double[][] sources,
      TwoFluidSection[] sections, double tolerance) {
    if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0 || !Double.isFinite(tolerance)
        || tolerance <= 0.0) {
      throw new IllegalArgumentException("Component timestep and tolerance must be positive and finite");
    }
    if (sections == null || sections.length != cellCount || faces == null || faces.length != cellCount + 1
        || sources == null || sources.length != cellCount) {
      throw new IllegalArgumentException("Component transport arrays must match the hydrodynamic grid");
    }
    for (double[] face : faces) {
      if (face == null || face.length != PHASE_COUNT) {
        throw new IllegalArgumentException("Every component face must contain gas, oil, and water mass flow");
      }
    }
    for (double[] source : sources) {
      if (source == null || source.length != PHASE_COUNT) {
        throw new IllegalArgumentException("Every component cell must contain gas, oil, and water source rates");
      }
    }
  }

  private static SystemInterface prepareFluid(SystemInterface source, String context) {
    if (source == null) {
      throw new IllegalArgumentException("Fluid cannot be null for " + context);
    }
    SystemInterface fluid = source.clone();
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
      fluid.initProperties();
      return fluid;
    } catch (Exception exception) {
      throw new IllegalStateException("Thermodynamic flash failed for " + context + ": " + exception.getMessage(),
          exception);
    }
  }

  private void validateComponentSlate(SystemInterface fluid) {
    String[] candidate = sortedComponentNames(fluid);
    if (!Arrays.equals(componentNames, candidate)) {
      throw new IllegalArgumentException("Unsupported component slate change: expected "
          + Arrays.toString(componentNames) + " but received " + Arrays.toString(candidate));
    }
  }

  private static String[] sortedComponentNames(SystemInterface fluid) {
    TreeSet<String> names = new TreeSet<String>();
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      names.add(fluid.getPhase(0).getComponent(component).getComponentName());
    }
    return names.toArray(new String[names.size()]);
  }

  private static double[] componentMolarMasses(SystemInterface fluid, String[] names) {
    Map<String, Double> molarMasses = new TreeMap<String, Double>();
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      molarMasses.put(fluid.getPhase(0).getComponent(component).getComponentName(),
          fluid.getPhase(0).getComponent(component).getMolarMass());
    }
    double[] result = new double[names.length];
    for (int component = 0; component < names.length; component++) {
      Double molarMass = molarMasses.get(names[component]);
      if (molarMass == null || !Double.isFinite(molarMass) || molarMass <= 0.0) {
        throw new IllegalArgumentException("Invalid molar mass for component '" + names[component] + "'");
      }
      result[component] = molarMass;
    }
    return result;
  }

  private double[][][] currentMassFractions() {
    double[][][] result = new double[cellCount][PHASE_COUNT][componentNames.length];
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double phaseMass = phaseInventory(componentInventoryKg[cell][phase]);
        if (phaseMass > MASS_FLOOR_KG) {
          for (int component = 0; component < componentNames.length; component++) {
            result[cell][phase][component] = componentInventoryKg[cell][phase][component] / phaseMass;
          }
        }
      }
    }
    return result;
  }

  private double[] totalComponentInventory() {
    double[] result = new double[componentNames.length];
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        for (int component = 0; component < componentNames.length; component++) {
          result[component] += componentInventoryKg[cell][phase][component];
        }
      }
    }
    return result;
  }

  private double[][] phaseComponentInventories() {
    double[][] result = new double[PHASE_COUNT][componentNames.length];
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        for (int component = 0; component < componentNames.length; component++) {
          result[phase][component] += componentInventoryKg[cell][phase][component];
        }
      }
    }
    return result;
  }

  private double[][][] reportProfiles() {
    double[][][] result = new double[PHASE_COUNT][componentNames.length][cellCount];
    for (int cell = 0; cell < cellCount; cell++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double phaseMass = phaseInventory(componentInventoryKg[cell][phase]);
        if (phaseMass > MASS_FLOOR_KG) {
          for (int component = 0; component < componentNames.length; component++) {
            result[phase][component][cell] = componentInventoryKg[cell][phase][component] / phaseMass;
          }
        }
      }
    }
    return result;
  }

  private int componentIndex(String componentName) {
    for (int component = 0; component < componentNames.length; component++) {
      if (componentNames[component].equals(componentName)) {
        return component;
      }
    }
    throw new IllegalArgumentException("Component '" + componentName + "' is not in the tracked slate");
  }

  private static int phaseIndex(PhaseType type) {
    if (type == PhaseType.GAS) {
      return GAS;
    }
    if (type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.LIQUID_ASPHALTENE) {
      return OIL;
    }
    if (type == PhaseType.AQUEOUS) {
      return WATER;
    }
    return -1;
  }

  private static String phaseName(int phase) {
    switch (phase) {
    case GAS:
      return "gas";
    case OIL:
      return "oil";
    case WATER:
      return "water";
    default:
      return "unknown";
    }
  }

  private static double phaseMassKg(TwoFluidSection section, int phase) {
    double massPerLength;
    switch (phase) {
    case GAS:
      massPerLength = section.getGasMassPerLength();
      break;
    case OIL:
      massPerLength = section.getOilMassPerLength();
      break;
    case WATER:
      massPerLength = section.getWaterMassPerLength();
      break;
    default:
      throw new IllegalArgumentException("Unsupported phase index " + phase);
    }
    return Math.max(0.0, massPerLength * section.getLength());
  }

  private static double phaseInventory(double[] componentInventory) {
    return sum(componentInventory);
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  private static double maximumAbsolute(double[][] values) {
    double maximum = 0.0;
    for (double[] row : values) {
      for (double value : row) {
        maximum = Math.max(maximum, Math.abs(value));
      }
    }
    return maximum;
  }

  private static double[][][] copy(double[][][] values) {
    double[][][] result = new double[values.length][][];
    for (int first = 0; first < values.length; first++) {
      result[first] = new double[values[first].length][];
      for (int second = 0; second < values[first].length; second++) {
        result[first][second] = Arrays.copyOf(values[first][second], values[first][second].length);
      }
    }
    return result;
  }
}

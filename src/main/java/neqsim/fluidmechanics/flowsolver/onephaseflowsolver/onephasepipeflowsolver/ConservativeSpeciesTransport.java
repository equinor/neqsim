package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.util.Arrays;
import neqsim.fluidmechanics.flowsolver.AxialDispersionModel;
import neqsim.fluidmechanics.flowsolver.NoAxialDispersion;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport.ConservationReason;

/** Positive-flow conservative finite-volume transport for n-1 species. */
final class ConservativeSpeciesTransport {
  static final double INVENTORY_RELATIVE_TOLERANCE = 1.0e-8;
  static final double MASS_FRACTION_TOLERANCE = 1.0e-12;
  private static final int MAXIMUM_TRANSPORT_SUBSTEPS = 10000;

  private ConservativeSpeciesTransport() {
  }

  static OnePhaseSpeciesConservationReport solve(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds) {
    return solve(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, null,
        NoAxialDispersion.INSTANCE);
  }

  static OnePhaseSpeciesConservationReport solve(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds, SpeciesAdvectionScheme scheme, double[] cellLengthM) {
    return solve(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, scheme, cellLengthM, NoAxialDispersion.INSTANCE);
  }

  static OnePhaseSpeciesConservationReport solve(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds, SpeciesAdvectionScheme scheme, double[] cellLengthM,
      AxialDispersionModel axialDispersionModel) {
    String validation = validate(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, scheme, cellLengthM, axialDispersionModel);
    if (validation != null) {
      return failed(ConservationReason.INVALID_STATE, componentNames, validation);
    }
    for (double faceFlow : faceMassFlowKgPerSecond) {
      if (faceFlow <= 0.0) {
        return failed(ConservationReason.UNSUPPORTED_FLOW, componentNames,
            "Conservative species transport currently requires strictly positive face mass flow; "
                + "zero and reversed flow require an explicit external upwind state.");
      }
    }

    SpeciesTransportDiagnostics diagnostics = createDiagnostics(scheme, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, cellLengthM, axialDispersionModel);
    if (diagnostics.getSubsteps() > MAXIMUM_TRANSPORT_SUBSTEPS) {
      return failed(ConservationReason.INVALID_STATE, componentNames,
          "High-resolution species transport requires " + diagnostics.getSubsteps()
              + " substeps; reduce the hydraulic timestep so no more than " + MAXIMUM_TRANSPORT_SUBSTEPS
              + " bounded transport substeps are required.");
    }
    if (scheme == SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2) {
      return solveTvdVanLeer(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
          faceMassFlowKgPerSecond, timeStepSeconds, cellLengthM, diagnostics);
    }
    return solveFirstOrderImplicit(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, cellLengthM, diagnostics);
  }

  private static OnePhaseSpeciesConservationReport solveFirstOrderImplicit(String[] componentNames,
      double[][] oldMassFraction, double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg,
      double[] faceMassFlowKgPerSecond, double timeStepSeconds, double[] cellLengthM,
      SpeciesTransportDiagnostics diagnostics) {
    int components = componentNames.length;
    int cells = oldCellMassKg.length;
    double[][] massFraction = new double[components][cells];
    double[] averagedCellMassKg = interpolate(oldCellMassKg, newCellMassKg, 0.5);
    double[] dispersionConductanceKgPerSecond = dispersionConductance(averagedCellMassKg, cellLengthM,
        diagnostics.getPhysicalAxialDispersionM2PerSecond());
    double[] inletBoundaryMassKg = new double[components];
    double[] outletBoundaryMassKg = new double[components];

    for (int component = 0; component < components - 1; component++) {
      double[] lower = new double[cells];
      double[] diagonal = new double[cells];
      double[] upper = new double[cells];
      double[] rightHandSide = new double[cells];
      for (int cell = 0; cell < cells; cell++) {
        double westDispersion = dispersionConductanceKgPerSecond[cell];
        double eastDispersion = dispersionConductanceKgPerSecond[cell + 1];
        diagonal[cell] = newCellMassKg[cell]
            + timeStepSeconds * (faceMassFlowKgPerSecond[cell + 1] + westDispersion + eastDispersion);
        rightHandSide[cell] = oldCellMassKg[cell] * oldMassFraction[component][cell];
        upper[cell] = -timeStepSeconds * eastDispersion;
        if (cell == 0) {
          rightHandSide[cell] += timeStepSeconds * (faceMassFlowKgPerSecond[0] + westDispersion)
              * inletMassFraction[component];
        } else {
          lower[cell] = -timeStepSeconds * (faceMassFlowKgPerSecond[cell] + westDispersion);
        }
      }
      massFraction[component] = solveTridiagonal(lower, diagonal, upper, rightHandSide);
      if (massFraction[component] == null) {
        return failed(ConservationReason.INVALID_STATE, componentNames,
            "Component transport produced a non-finite or non-positive implicit tridiagonal coefficient at component "
                + component + ".");
      }
      inletBoundaryMassKg[component] = timeStepSeconds * (faceMassFlowKgPerSecond[0] * inletMassFraction[component]
          + dispersionConductanceKgPerSecond[0] * (inletMassFraction[component] - massFraction[component][0]));
      outletBoundaryMassKg[component] = timeStepSeconds * faceMassFlowKgPerSecond[cells]
          * massFraction[component][cells - 1];
    }

    for (int cell = 0; cell < cells; cell++) {
      double independentSum = 0.0;
      for (int component = 0; component < components - 1; component++) {
        independentSum += massFraction[component][cell];
      }
      massFraction[components - 1][cell] = 1.0 - independentSum;
    }
    inletBoundaryMassKg[components - 1] = timeStepSeconds * faceMassFlowKgPerSecond[0];
    outletBoundaryMassKg[components - 1] = timeStepSeconds * faceMassFlowKgPerSecond[cells];
    for (int component = 0; component < components - 1; component++) {
      inletBoundaryMassKg[components - 1] -= inletBoundaryMassKg[component];
      outletBoundaryMassKg[components - 1] -= outletBoundaryMassKg[component];
    }

    return createReport(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, massFraction, inletBoundaryMassKg, outletBoundaryMassKg, diagnostics);
  }

  private static OnePhaseSpeciesConservationReport solveTvdVanLeer(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds, double[] cellLengthM, SpeciesTransportDiagnostics diagnostics) {
    int components = componentNames.length;
    int independentComponents = components - 1;
    int cells = oldCellMassKg.length;
    int substeps = diagnostics.getSubsteps();
    double substepSeconds = timeStepSeconds / substeps;
    double[][] componentMassKg = new double[independentComponents][cells];
    double[] inletBoundaryMassKg = new double[components];
    double[] outletBoundaryMassKg = new double[components];
    double[] physicalDispersionM2PerSecond = diagnostics.getPhysicalAxialDispersionM2PerSecond();

    for (int component = 0; component < independentComponents; component++) {
      for (int cell = 0; cell < cells; cell++) {
        componentMassKg[component][cell] = oldCellMassKg[cell] * oldMassFraction[component][cell];
      }
    }

    for (int substep = 0; substep < substeps; substep++) {
      double startFraction = (double) substep / substeps;
      double endFraction = (double) (substep + 1) / substeps;
      double[] startCellMassKg = interpolate(oldCellMassKg, newCellMassKg, startFraction);
      double[] endCellMassKg = interpolate(oldCellMassKg, newCellMassKg, endFraction);
      double[] startDispersionConductanceKgPerSecond = dispersionConductance(startCellMassKg, cellLengthM,
          physicalDispersionM2PerSecond);
      double[] endDispersionConductanceKgPerSecond = dispersionConductance(endCellMassKg, cellLengthM,
          physicalDispersionM2PerSecond);

      for (int component = 0; component < independentComponents; component++) {
        double[] startMassFraction = divide(componentMassKg[component], startCellMassKg);
        double[] startFaceMassFraction = reconstructPositiveFlowFaces(startMassFraction, inletMassFraction[component]);
        double[] startDispersiveFluxKgPerSecond = physicalDispersiveFlux(startMassFraction,
            inletMassFraction[component], startDispersionConductanceKgPerSecond);
        double[] eulerComponentMassKg = conservativeEulerStep(componentMassKg[component], startFaceMassFraction,
            faceMassFlowKgPerSecond, startDispersiveFluxKgPerSecond, substepSeconds);
        double[] endEulerMassFraction = divide(eulerComponentMassKg, endCellMassKg);
        double[] endFaceMassFraction = reconstructPositiveFlowFaces(endEulerMassFraction, inletMassFraction[component]);
        double[] endDispersiveFluxKgPerSecond = physicalDispersiveFlux(endEulerMassFraction,
            inletMassFraction[component], endDispersionConductanceKgPerSecond);
        double[] secondEulerComponentMassKg = conservativeEulerStep(eulerComponentMassKg, endFaceMassFraction,
            faceMassFlowKgPerSecond, endDispersiveFluxKgPerSecond, substepSeconds);

        for (int cell = 0; cell < cells; cell++) {
          componentMassKg[component][cell] = 0.5
              * (componentMassKg[component][cell] + secondEulerComponentMassKg[cell]);
        }
        inletBoundaryMassKg[component] += substepSeconds * (faceMassFlowKgPerSecond[0] * inletMassFraction[component]
            + 0.5 * (startDispersiveFluxKgPerSecond[0] + endDispersiveFluxKgPerSecond[0]));
        outletBoundaryMassKg[component] += 0.5 * substepSeconds
            * (faceMassFlowKgPerSecond[cells] * (startFaceMassFraction[cells] + endFaceMassFraction[cells])
                + startDispersiveFluxKgPerSecond[cells] + endDispersiveFluxKgPerSecond[cells]);
      }
    }

    double[][] massFraction = new double[components][cells];
    for (int component = 0; component < independentComponents; component++) {
      massFraction[component] = divide(componentMassKg[component], newCellMassKg);
    }
    for (int cell = 0; cell < cells; cell++) {
      double independentSum = 0.0;
      for (int component = 0; component < independentComponents; component++) {
        independentSum += massFraction[component][cell];
      }
      massFraction[components - 1][cell] = 1.0 - independentSum;
    }
    inletBoundaryMassKg[components - 1] = timeStepSeconds * faceMassFlowKgPerSecond[0];
    outletBoundaryMassKg[components - 1] = timeStepSeconds * faceMassFlowKgPerSecond[cells];
    for (int component = 0; component < independentComponents; component++) {
      inletBoundaryMassKg[components - 1] -= inletBoundaryMassKg[component];
      outletBoundaryMassKg[components - 1] -= outletBoundaryMassKg[component];
    }

    return createReport(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, massFraction, inletBoundaryMassKg, outletBoundaryMassKg, diagnostics);
  }

  private static OnePhaseSpeciesConservationReport createReport(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds, double[][] massFraction, double[] integratedInletMassKg, double[] integratedOutletMassKg,
      SpeciesTransportDiagnostics diagnostics) {
    int components = componentNames.length;
    int cells = oldCellMassKg.length;
    double[] initialInventory = new double[components];
    double[] finalInventory = new double[components];
    double[] inletMass = new double[components];
    double[] outletMass = new double[components];
    double[] residual = new double[components];
    double[] relativeResidual = new double[components];
    double maximumRelativeResidual = 0.0;
    double minimumMassFraction = Double.POSITIVE_INFINITY;
    double maximumMassFraction = Double.NEGATIVE_INFINITY;
    double maximumSumError = 0.0;

    for (int component = 0; component < components; component++) {
      for (int cell = 0; cell < cells; cell++) {
        initialInventory[component] += oldCellMassKg[cell] * oldMassFraction[component][cell];
        finalInventory[component] += newCellMassKg[cell] * massFraction[component][cell];
        minimumMassFraction = Math.min(minimumMassFraction, massFraction[component][cell]);
        maximumMassFraction = Math.max(maximumMassFraction, massFraction[component][cell]);
      }
      inletMass[component] = integratedInletMassKg == null
          ? timeStepSeconds * faceMassFlowKgPerSecond[0] * inletMassFraction[component]
          : integratedInletMassKg[component];
      outletMass[component] = integratedOutletMassKg == null
          ? timeStepSeconds * faceMassFlowKgPerSecond[cells] * massFraction[component][cells - 1]
          : integratedOutletMassKg[component];
      residual[component] = finalInventory[component] - initialInventory[component] - inletMass[component]
          + outletMass[component];
      double scale = Math.max(
          Math.max(Math.abs(initialInventory[component]), Math.abs(inletMass[component] - outletMass[component])), 1.0);
      relativeResidual[component] = Math.abs(residual[component]) / scale;
      maximumRelativeResidual = Math.max(maximumRelativeResidual, relativeResidual[component]);
    }

    for (int cell = 0; cell < cells; cell++) {
      double sum = 0.0;
      for (int component = 0; component < components; component++) {
        sum += massFraction[component][cell];
      }
      maximumSumError = Math.max(maximumSumError, Math.abs(sum - 1.0));
    }

    ConservationReason reason = ConservationReason.CONVERGED;
    if (!Double.isFinite(minimumMassFraction) || !Double.isFinite(maximumMassFraction)
        || minimumMassFraction < -MASS_FRACTION_TOLERANCE || maximumMassFraction > 1.0 + MASS_FRACTION_TOLERANCE
        || maximumSumError > MASS_FRACTION_TOLERANCE) {
      reason = ConservationReason.COMPOSITION_BOUNDS_FAILED;
    } else if (!Double.isFinite(maximumRelativeResidual) || maximumRelativeResidual > INVENTORY_RELATIVE_TOLERANCE) {
      reason = ConservationReason.COMPONENT_BALANCE_FAILED;
    }

    String message = "One-phase conservative species transport " + reason
        + ": maximum relative component inventory residual=" + maximumRelativeResidual + " (tolerance "
        + INVENTORY_RELATIVE_TOLERANCE + "), mass-fraction range=[" + minimumMassFraction + ", " + maximumMassFraction
        + "], maximum sum error=" + maximumSumError + " (tolerance " + MASS_FRACTION_TOLERANCE + ").";
    return new OnePhaseSpeciesConservationReport(reason, componentNames, massFraction, newCellMassKg, initialInventory,
        finalInventory, inletMass, outletMass, residual, relativeResidual, maximumRelativeResidual, minimumMassFraction,
        maximumMassFraction, maximumSumError, Double.NaN, diagnostics, message);
  }

  private static String validate(String[] componentNames, double[][] oldMassFraction, double[] inletMassFraction,
      double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond, double timeStepSeconds,
      SpeciesAdvectionScheme scheme, double[] cellLengthM, AxialDispersionModel axialDispersionModel) {
    if (scheme == null) {
      return "A conservative species advection scheme is required.";
    }
    if (axialDispersionModel == null) {
      return "A non-null physical axial-dispersion model is required; use NoAxialDispersion for pure advection.";
    }
    if (axialDispersionModel.getName() == null || axialDispersionModel.getName().trim().isEmpty()) {
      return "A physical axial-dispersion model must expose a non-empty diagnostic name.";
    }
    if (componentNames == null || componentNames.length < 2) {
      return "At least two named components are required for n-1 transport.";
    }
    if (oldMassFraction == null || oldMassFraction.length != componentNames.length || inletMassFraction == null
        || inletMassFraction.length != componentNames.length || oldCellMassKg == null || newCellMassKg == null
        || oldCellMassKg.length == 0 || newCellMassKg.length != oldCellMassKg.length || faceMassFlowKgPerSecond == null
        || faceMassFlowKgPerSecond.length != oldCellMassKg.length + 1 || !Double.isFinite(timeStepSeconds)
        || timeStepSeconds <= 0.0) {
      return "Species transport dimensions or timestep are invalid.";
    }

    double inletSum = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      if (componentNames[component] == null || componentNames[component].trim().isEmpty()
          || oldMassFraction[component] == null || oldMassFraction[component].length != oldCellMassKg.length
          || !isBounded(inletMassFraction[component])) {
        return "Component names, profiles, or inlet fractions are invalid at component " + component + ".";
      }
      inletSum += inletMassFraction[component];
      for (double value : oldMassFraction[component]) {
        if (!isBounded(value)) {
          return "Previous-time mass fractions must be finite and bounded without clipping.";
        }
      }
    }
    if (Math.abs(inletSum - 1.0) > MASS_FRACTION_TOLERANCE) {
      return "Inlet mass fractions must sum to one without normalization; sum=" + inletSum + ".";
    }
    for (int cell = 0; cell < oldCellMassKg.length; cell++) {
      if (!Double.isFinite(oldCellMassKg[cell]) || oldCellMassKg[cell] <= 0.0 || !Double.isFinite(newCellMassKg[cell])
          || newCellMassKg[cell] <= 0.0) {
        return "Cell masses must be finite and positive at cell " + cell + ".";
      }
      double sum = 0.0;
      for (int component = 0; component < componentNames.length; component++) {
        sum += oldMassFraction[component][cell];
      }
      if (Math.abs(sum - 1.0) > MASS_FRACTION_TOLERANCE) {
        return "Previous-time cell mass fractions must sum to one without normalization; cell=" + cell + ", sum=" + sum
            + ".";
      }
    }
    for (double faceFlow : faceMassFlowKgPerSecond) {
      if (!Double.isFinite(faceFlow)) {
        return "Face mass flows must be finite.";
      }
    }
    if (cellLengthM != null) {
      if (cellLengthM.length != oldCellMassKg.length) {
        return "Cell-length diagnostics must contain one value per physical cell.";
      }
      for (double cellLength : cellLengthM) {
        if (!Double.isFinite(cellLength) || cellLength <= 0.0) {
          return "Cell lengths must be finite and positive when supplied.";
        }
      }
    }
    if (axialDispersionModel.isEnabled() && cellLengthM == null) {
      return "Physical axial dispersion requires one positive length for each physical cell.";
    }
    if (cellLengthM != null) {
      for (int cell = 0; cell < oldCellMassKg.length; cell++) {
        double referenceCellMassKg = Math.min(oldCellMassKg[cell], newCellMassKg[cell]);
        double referenceMassFlowKgPerSecond = Math.max(faceMassFlowKgPerSecond[cell],
            faceMassFlowKgPerSecond[cell + 1]);
        double coefficient = axialDispersionModel.getCoefficientM2PerSecond(cell, cellLengthM[cell],
            referenceCellMassKg, referenceMassFlowKgPerSecond);
        if (!Double.isFinite(coefficient) || coefficient < 0.0) {
          return "Physical axial-dispersion coefficients must be finite and non-negative; cell=" + cell
              + ", coefficient=" + coefficient + " m2/s.";
        }
      }
    }
    return null;
  }

  private static SpeciesTransportDiagnostics createDiagnostics(SpeciesAdvectionScheme scheme, double[] oldCellMassKg,
      double[] newCellMassKg, double[] faceMassFlowKgPerSecond, double timeStepSeconds, double[] cellLengthM,
      AxialDispersionModel axialDispersionModel) {
    int cells = oldCellMassKg.length;
    double[] courantNumber = new double[cells];
    double[] numericalDispersion = new double[cells];
    double[] cellPecletNumber = new double[cells];
    double[] physicalDispersion = new double[cells];
    double[] physicalDispersionNumber = new double[cells];
    Arrays.fill(numericalDispersion, Double.NaN);
    Arrays.fill(cellPecletNumber, Double.NaN);
    Arrays.fill(physicalDispersionNumber, Double.NaN);
    double courantSum = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      double referenceCellMassKg = Math.min(oldCellMassKg[cell], newCellMassKg[cell]);
      double referenceMassFlowKgPerSecond = Math.max(faceMassFlowKgPerSecond[cell], faceMassFlowKgPerSecond[cell + 1]);
      courantNumber[cell] = timeStepSeconds * referenceMassFlowKgPerSecond / referenceCellMassKg;
      courantSum += courantNumber[cell];
      if (cellLengthM != null) {
        double velocityMPerSecond = referenceMassFlowKgPerSecond / referenceCellMassKg * cellLengthM[cell];
        numericalDispersion[cell] = 0.5 * velocityMPerSecond * cellLengthM[cell] * (1.0 + courantNumber[cell]);
        physicalDispersion[cell] = axialDispersionModel.getCoefficientM2PerSecond(cell, cellLengthM[cell],
            referenceCellMassKg, referenceMassFlowKgPerSecond);
        if (physicalDispersion[cell] > 0.0) {
          cellPecletNumber[cell] = velocityMPerSecond * cellLengthM[cell] / physicalDispersion[cell];
        }
      }
    }
    if (cellLengthM != null) {
      double[] referenceCellMassKg = new double[cells];
      for (int cell = 0; cell < cells; cell++) {
        referenceCellMassKg[cell] = Math.min(oldCellMassKg[cell], newCellMassKg[cell]);
      }
      double[] conductance = dispersionConductance(referenceCellMassKg, cellLengthM, physicalDispersion);
      for (int cell = 0; cell < cells; cell++) {
        physicalDispersionNumber[cell] = timeStepSeconds * (conductance[cell] + conductance[cell + 1])
            / referenceCellMassKg[cell];
      }
    }
    double maximumCombinedExplicitNumber = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      double combined = courantNumber[cell]
          + (Double.isFinite(physicalDispersionNumber[cell]) ? physicalDispersionNumber[cell] : 0.0);
      maximumCombinedExplicitNumber = Math.max(maximumCombinedExplicitNumber, combined);
    }
    int substeps = scheme.isHighResolution()
        ? Math.max(1, (int) Math.ceil(maximumCombinedExplicitNumber / scheme.getMaximumCourantNumber()))
        : 1;
    boolean physicalDispersionIncluded = false;
    for (double coefficient : physicalDispersion) {
      physicalDispersionIncluded |= coefficient > 0.0;
    }
    String message = "Full-step mass CFL is reported before " + substeps + " transport substep(s). "
        + "The numerical-dispersion array is the modified-equation reference for first-order implicit upwind. "
        + "Physical axial dispersion model='" + axialDispersionModel.getName() + "' is "
        + (physicalDispersionIncluded ? "enabled" : "disabled")
        + " with DIRICHLET_INLET and ZERO_GRADIENT_OUTLET boundary conditions; physical and numerical coefficients "
        + "are reported separately.";
    return new SpeciesTransportDiagnostics(scheme, courantNumber, courantSum / cells, substeps, numericalDispersion,
        cellPecletNumber, axialDispersionModel.getName(), physicalDispersion, physicalDispersionNumber,
        physicalDispersionIncluded, message);
  }

  private static double[] interpolate(double[] oldValues, double[] newValues, double fraction) {
    double[] result = new double[oldValues.length];
    for (int index = 0; index < result.length; index++) {
      result[index] = oldValues[index] + fraction * (newValues[index] - oldValues[index]);
    }
    return result;
  }

  private static double[] divide(double[] numerator, double[] denominator) {
    double[] result = new double[numerator.length];
    for (int index = 0; index < result.length; index++) {
      result[index] = numerator[index] / denominator[index];
    }
    return result;
  }

  private static double[] dispersionConductance(double[] cellMassKg, double[] cellLengthM,
      double[] dispersionM2PerSecond) {
    int cells = cellMassKg.length;
    double[] conductanceKgPerSecond = new double[cells + 1];
    if (cellLengthM == null || dispersionM2PerSecond.length == 0) {
      return conductanceKgPerSecond;
    }
    double inletConductivityKgMPerSecond = cellMassKg[0] / cellLengthM[0] * dispersionM2PerSecond[0];
    conductanceKgPerSecond[0] = inletConductivityKgMPerSecond / (0.5 * cellLengthM[0]);
    for (int face = 1; face < cells; face++) {
      double leftConductivity = cellMassKg[face - 1] / cellLengthM[face - 1] * dispersionM2PerSecond[face - 1];
      double rightConductivity = cellMassKg[face] / cellLengthM[face] * dispersionM2PerSecond[face];
      if (leftConductivity > 0.0 && rightConductivity > 0.0) {
        double leftHalfCellResistance = 0.5 * cellLengthM[face - 1] / leftConductivity;
        double rightHalfCellResistance = 0.5 * cellLengthM[face] / rightConductivity;
        conductanceKgPerSecond[face] = 1.0 / (leftHalfCellResistance + rightHalfCellResistance);
      }
    }
    conductanceKgPerSecond[cells] = 0.0;
    return conductanceKgPerSecond;
  }

  private static double[] physicalDispersiveFlux(double[] massFraction, double inletMassFraction,
      double[] conductanceKgPerSecond) {
    int cells = massFraction.length;
    double[] fluxKgPerSecond = new double[cells + 1];
    fluxKgPerSecond[0] = conductanceKgPerSecond[0] * (inletMassFraction - massFraction[0]);
    for (int face = 1; face < cells; face++) {
      fluxKgPerSecond[face] = conductanceKgPerSecond[face] * (massFraction[face - 1] - massFraction[face]);
    }
    fluxKgPerSecond[cells] = 0.0;
    return fluxKgPerSecond;
  }

  private static double[] conservativeEulerStep(double[] componentMassKg, double[] faceMassFraction,
      double[] faceMassFlowKgPerSecond, double[] dispersiveFluxKgPerSecond, double timeStepSeconds) {
    double[] result = new double[componentMassKg.length];
    for (int cell = 0; cell < result.length; cell++) {
      result[cell] = componentMassKg[cell]
          + timeStepSeconds * (faceMassFlowKgPerSecond[cell] * faceMassFraction[cell] + dispersiveFluxKgPerSecond[cell]
              - faceMassFlowKgPerSecond[cell + 1] * faceMassFraction[cell + 1] - dispersiveFluxKgPerSecond[cell + 1]);
    }
    return result;
  }

  private static double[] solveTridiagonal(double[] lower, double[] diagonal, double[] upper, double[] rightHandSide) {
    int size = diagonal.length;
    double[] upperPrime = new double[size];
    double[] rightHandSidePrime = new double[size];
    double denominator = diagonal[0];
    if (!Double.isFinite(denominator) || denominator <= 0.0) {
      return null;
    }
    upperPrime[0] = upper[0] / denominator;
    rightHandSidePrime[0] = rightHandSide[0] / denominator;
    for (int row = 1; row < size; row++) {
      denominator = diagonal[row] - lower[row] * upperPrime[row - 1];
      if (!Double.isFinite(denominator) || denominator <= 0.0) {
        return null;
      }
      upperPrime[row] = row == size - 1 ? 0.0 : upper[row] / denominator;
      rightHandSidePrime[row] = (rightHandSide[row] - lower[row] * rightHandSidePrime[row - 1]) / denominator;
    }
    double[] result = new double[size];
    result[size - 1] = rightHandSidePrime[size - 1];
    for (int row = size - 2; row >= 0; row--) {
      result[row] = rightHandSidePrime[row] - upperPrime[row] * result[row + 1];
    }
    for (double value : result) {
      if (!Double.isFinite(value)) {
        return null;
      }
    }
    return result;
  }

  private static double[] reconstructPositiveFlowFaces(double[] cellMassFraction, double inletMassFraction) {
    int cells = cellMassFraction.length;
    double[] faceMassFraction = new double[cells + 1];
    faceMassFraction[0] = inletMassFraction;
    for (int face = 1; face <= cells; face++) {
      int upstreamCell = face - 1;
      double slope = 0.0;
      if (upstreamCell < cells - 1) {
        double westValue = upstreamCell == 0 ? inletMassFraction : cellMassFraction[upstreamCell - 1];
        double westDifference = cellMassFraction[upstreamCell] - westValue;
        double eastDifference = cellMassFraction[upstreamCell + 1] - cellMassFraction[upstreamCell];
        slope = vanLeerLimitedSlope(westDifference, eastDifference);
      }
      faceMassFraction[face] = cellMassFraction[upstreamCell] + 0.5 * slope;
    }
    return faceMassFraction;
  }

  private static double vanLeerLimitedSlope(double westDifference, double eastDifference) {
    if (westDifference * eastDifference <= 0.0) {
      return 0.0;
    }
    return 2.0 * westDifference * eastDifference / (westDifference + eastDifference);
  }

  private static boolean isBounded(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
  }

  private static OnePhaseSpeciesConservationReport failed(ConservationReason reason, String[] componentNames,
      String message) {
    return new OnePhaseSpeciesConservationReport(reason, componentNames, new double[0][0], new double[0], new double[0],
        new double[0], new double[0], new double[0], new double[0], new double[0], Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, message);
  }
}

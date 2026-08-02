package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport.ConservationReason;

/** Positive-flow, first-order implicit finite-volume transport for n-1 species. */
final class ConservativeSpeciesTransport {
  static final double INVENTORY_RELATIVE_TOLERANCE = 1.0e-8;
  static final double MASS_FRACTION_TOLERANCE = 1.0e-12;

  private ConservativeSpeciesTransport() {
  }

  static OnePhaseSpeciesConservationReport solve(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds) {
    String validation = validate(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds);
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

    int components = componentNames.length;
    int cells = oldCellMassKg.length;
    double[][] massFraction = new double[components][cells];

    for (int component = 0; component < components - 1; component++) {
      for (int cell = 0; cell < cells; cell++) {
        double westMassFraction = cell == 0 ? inletMassFraction[component] : massFraction[component][cell - 1];
        double numerator = oldCellMassKg[cell] * oldMassFraction[component][cell]
            + timeStepSeconds * faceMassFlowKgPerSecond[cell] * westMassFraction;
        double denominator = newCellMassKg[cell] + timeStepSeconds * faceMassFlowKgPerSecond[cell + 1];
        if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= 0.0) {
          return failed(ConservationReason.INVALID_STATE, componentNames,
              "Component transport produced a non-finite or non-positive implicit coefficient " + "at component "
                  + component + ", cell " + cell + ".");
        }
        massFraction[component][cell] = numerator / denominator;
      }
    }

    for (int cell = 0; cell < cells; cell++) {
      double independentSum = 0.0;
      for (int component = 0; component < components - 1; component++) {
        independentSum += massFraction[component][cell];
      }
      massFraction[components - 1][cell] = 1.0 - independentSum;
    }

    return createReport(componentNames, oldMassFraction, inletMassFraction, oldCellMassKg, newCellMassKg,
        faceMassFlowKgPerSecond, timeStepSeconds, massFraction);
  }

  private static OnePhaseSpeciesConservationReport createReport(String[] componentNames, double[][] oldMassFraction,
      double[] inletMassFraction, double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond,
      double timeStepSeconds, double[][] massFraction) {
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
      inletMass[component] = timeStepSeconds * faceMassFlowKgPerSecond[0] * inletMassFraction[component];
      outletMass[component] = timeStepSeconds * faceMassFlowKgPerSecond[cells] * massFraction[component][cells - 1];
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
    return new OnePhaseSpeciesConservationReport(reason, componentNames, massFraction, initialInventory, finalInventory,
        inletMass, outletMass, residual, relativeResidual, maximumRelativeResidual, minimumMassFraction,
        maximumMassFraction, maximumSumError, Double.NaN, message);
  }

  private static String validate(String[] componentNames, double[][] oldMassFraction, double[] inletMassFraction,
      double[] oldCellMassKg, double[] newCellMassKg, double[] faceMassFlowKgPerSecond, double timeStepSeconds) {
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
    return null;
  }

  private static boolean isBounded(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
  }

  private static OnePhaseSpeciesConservationReport failed(ConservationReason reason, String[] componentNames,
      String message) {
    return new OnePhaseSpeciesConservationReport(reason, componentNames, new double[0][0], new double[0], new double[0],
        new double[0], new double[0], new double[0], new double[0], Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, message);
  }
}

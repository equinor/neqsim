package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Compressed signed-face complementarity block for conservative phase transport.
 *
 * <p>
 * Every internal gas, oil and water face owns three auxiliary variables: nonnegative signed-direction mass-flow
 * magnitude, advective momentum flux and a nonnegative donor-presence multiplier. The direction and the active donor
 * row are frozen while a Jacobian is assembled. A present donor uses the normalized conservative relation between mass
 * and momentum transport; an absent donor pins advective momentum to zero exactly. Replacing the free internal flux in
 * both adjacent cells preserves total mass and momentum without clipping either cell inventory.
 * </p>
 *
 * <p>
 * This class owns algebra and layout only. {@link UnsplitTransientSolver} decides when an active set may be refreshed
 * and whether a trial step is admissible.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class SignedFaceComplementarity implements Serializable {
  private static final long serialVersionUID = 1L;
  static final int PHASE_COUNT = 3;
  static final int VARIABLES_PER_PHASE_FACE = 3;
  static final int VARIABLES_PER_FACE = PHASE_COUNT * VARIABLES_PER_PHASE_FACE;
  static final int MASS_FLOW = 0;
  static final int ADVECTIVE_MOMENTUM = 1;
  static final int MULTIPLIER = 2;
  static final int CELL_COLOR_COUNT = 35;
  static final int TOTAL_COLOR_COUNT = 53;

  /** Frozen directions, active rows, unknowns and physical scales for one nonlinear iterate. */
  static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int cellCount;
    private final int[] direction;
    private final boolean[] constrained;
    private final double[] values;
    private final double[] scales;
    private final double[] donorMassScales;

    /** Build state from a validated free-flux evaluation. */
    private State(int cellCount, DirectionalPhaseFaceFlux[][] faces, double[][] endpointState) {
      this.cellCount = cellCount;
      int phaseFaceCount = Math.max(0, cellCount - 1) * PHASE_COUNT;
      direction = new int[phaseFaceCount];
      constrained = new boolean[phaseFaceCount];
      values = new double[phaseFaceCount * VARIABLES_PER_PHASE_FACE];
      scales = new double[values.length];
      donorMassScales = new double[phaseFaceCount];
      for (int face = 0; face < faces.length; face++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          int phaseFace = phaseFace(face, phase);
          int offset = variableOffset(face, phase);
          DirectionalPhaseFaceFlux free = faces[face][phase];
          double signed = free.getSignedMassFlowKgPerSecond();
          direction[phaseFace] = signed < 0.0 ? -1 : 1;
          values[offset + MASS_FLOW] = Math.abs(signed);
          values[offset + ADVECTIVE_MOMENTUM] = free.getAdvectiveMomentumN();
          values[offset + MULTIPLIER] = 0.0;
          scales[offset + MASS_FLOW] = Math.max(1.0, Math.abs(signed));
          scales[offset + ADVECTIVE_MOMENTUM] = Math.max(1.0, Math.abs(free.getAdvectiveMomentumN()));
          scales[offset + MULTIPLIER] = scales[offset + MASS_FLOW];
          int donorCell = direction[phaseFace] > 0 ? face : face + 1;
          donorMassScales[phaseFace] = Math.max(1.0, Math.abs(endpointState[donorCell][phase]));
          constrained[phaseFace] = endpointState[donorCell][phase] == 0.0
              || donorMass(free, direction[phaseFace]) == 0.0;
        }
      }
    }

    /** @return defensive auxiliary values ordered by face, phase and variable */
    double[] getValues() {
      return values.clone();
    }

    /** @return defensive physical variable scales */
    double[] getScales() {
      return scales.clone();
    }

    /** @return number of finite-volume cells */
    int getCellCount() {
      return cellCount;
    }

    /** Install an accepted auxiliary iterate. */
    void setValues(double[] candidate) {
      if (candidate == null || candidate.length != values.length) {
        throw new IllegalArgumentException("Signed-face auxiliary dimensions must remain fixed");
      }
      System.arraycopy(candidate, 0, values, 0, values.length);
    }

    /** @return frozen signed direction for one phase face */
    int getDirection(int face, int phase) {
      return direction[phaseFace(face, phase)];
    }

    /** @return whether the donor-presence constraint row is active */
    boolean isConstrained(int face, int phase) {
      return constrained[phaseFace(face, phase)];
    }

    /** Activate an exact donor-presence row. */
    void constrain(int face, int phase) {
      constrained[phaseFace(face, phase)] = true;
    }

    /** Release a donor-presence row. */
    void release(int face, int phase) {
      constrained[phaseFace(face, phase)] = false;
    }

    /** @return endpoint donor-mass scale for one phase face */
    double getDonorMassScale(int face, int phase) {
      return donorMassScales[phaseFace(face, phase)];
    }
  }

  /** Corrected conservative rates, face residuals and solved directional ledgers. */
  static final class Evaluation {
    private final double[][] conservativeRates;
    private final double[] residual;
    private final DirectionalPhaseFaceFlux[][] solvedFaces;

    /** Store defensive results from one auxiliary evaluation. */
    private Evaluation(double[][] conservativeRates, double[] residual, DirectionalPhaseFaceFlux[][] solvedFaces) {
      this.conservativeRates = copy(conservativeRates);
      this.residual = residual.clone();
      this.solvedFaces = copyFaces(solvedFaces);
    }

    /** @return defensive corrected conservative rates */
    double[][] getConservativeRates() {
      return copy(conservativeRates);
    }

    /** @return defensive scaled auxiliary residual */
    double[] getResidual() {
      return residual.clone();
    }

    /** @return defensive solved face ledgers */
    DirectionalPhaseFaceFlux[][] getSolvedFaces() {
      return copyFaces(solvedFaces);
    }
  }

  /** No instances. */
  private SignedFaceComplementarity() {
  }

  /**
   * Initialize a compressed signed-face state.
   *
   * @param evaluation validated model evaluation with directional faces and cell lengths
   * @param endpointState candidate endpoint conservative state
   * @return initialized auxiliary state
   */
  static State initialize(UnsplitTransientSolver.Evaluation evaluation, double[][] endpointState) {
    DirectionalPhaseFaceFlux[][] faces = requireFaces(evaluation, endpointState.length);
    requireLengths(evaluation, endpointState.length);
    return new State(endpointState.length, faces, endpointState);
  }

  /**
   * Replace free internal transport by the signed unknowns and evaluate the frozen KKT rows.
   *
   * @param evaluation validated free model evaluation
   * @param endpointState candidate endpoint conservative state
   * @param state frozen auxiliary state and current unknown values
   * @return corrected rates, auxiliary residual and solved ledgers
   */
  static Evaluation evaluate(UnsplitTransientSolver.Evaluation evaluation, double[][] endpointState, State state) {
    if (state == null || endpointState == null || endpointState.length != state.getCellCount()) {
      throw new IllegalArgumentException("Signed-face state must match the endpoint cells");
    }
    DirectionalPhaseFaceFlux[][] freeFaces = requireFaces(evaluation, endpointState.length);
    double[] lengths = requireLengths(evaluation, endpointState.length);
    double[][] corrected = evaluation.copyConservativeRates();
    double[] values = state.values;
    double[] residual = new double[values.length];
    DirectionalPhaseFaceFlux[][] solved = new DirectionalPhaseFaceFlux[freeFaces.length][PHASE_COUNT];
    for (int face = 0; face < freeFaces.length; face++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        int offset = variableOffset(face, phase);
        int direction = state.getDirection(face, phase);
        double magnitude = values[offset + MASS_FLOW];
        double momentum = values[offset + ADVECTIVE_MOMENTUM];
        double multiplier = values[offset + MULTIPLIER];
        DirectionalPhaseFaceFlux free = freeFaces[face][phase];
        double signedMass = direction * magnitude;
        double massCorrection = signedMass - free.getSignedMassFlowKgPerSecond();
        double momentumCorrection = momentum - free.getAdvectiveMomentumN();
        corrected[face][phase] -= massCorrection / lengths[face];
        corrected[face + 1][phase] += massCorrection / lengths[face + 1];
        corrected[face][phase + PHASE_COUNT] -= momentumCorrection / lengths[face];
        corrected[face + 1][phase + PHASE_COUNT] += momentumCorrection / lengths[face + 1];

        double massScale = state.scales[offset + MASS_FLOW];
        double momentumScale = state.scales[offset + ADVECTIVE_MOMENTUM];
        residual[offset + MASS_FLOW] = (magnitude - Math.abs(free.getSignedMassFlowKgPerSecond()) + multiplier)
            / massScale;
        double donorMass = donorMass(free, direction);
        double donorMomentum = donorMomentum(free, direction);
        residual[offset + ADVECTIVE_MOMENTUM] = donorMass == 0.0 ? momentum / momentumScale
            : (momentum - signedMass * donorMomentum / donorMass) / momentumScale;
        int donorCell = direction > 0 ? face : face + 1;
        residual[offset + MULTIPLIER] = state.isConstrained(face, phase)
            ? endpointState[donorCell][phase] / state.getDonorMassScale(face, phase)
            : multiplier / massScale;
        solved[face][phase] = new DirectionalPhaseFaceFlux(signedMass, momentum, free.getPressureTractionN(),
            free.getLeftDonorMassKgPerM(), free.getRightDonorMassKgPerM(), free.getLeftDonorMomentumKgPerSecond(),
            free.getRightDonorMomentumKgPerSecond());
      }
    }
    return new Evaluation(corrected, residual, solved);
  }

  /**
   * Replace the free internal mass and advective-momentum fluxes in conservative rates by solved ledgers.
   *
   * @param rates free conservative rates
   * @param cellLengthsM physical cell lengths in m
   * @param freeFaces free directional ledgers
   * @param solvedFaces solved directional ledgers
   * @return defensively copied corrected rates
   */
  static double[][] replaceInternalFluxes(double[][] rates, double[] cellLengthsM,
      DirectionalPhaseFaceFlux[][] freeFaces, DirectionalPhaseFaceFlux[][] solvedFaces) {
    if (rates == null || cellLengthsM == null || freeFaces == null || solvedFaces == null
        || rates.length != cellLengthsM.length || freeFaces.length != Math.max(0, rates.length - 1)
        || solvedFaces.length != freeFaces.length) {
      throw new IllegalArgumentException("Solved internal transport dimensions must match the finite-volume grid");
    }
    double[][] corrected = copy(rates);
    for (int face = 0; face < freeFaces.length; face++) {
      if (freeFaces[face] == null || solvedFaces[face] == null || freeFaces[face].length != PHASE_COUNT
          || solvedFaces[face].length != PHASE_COUNT) {
        throw new IllegalArgumentException("Every solved face requires gas, oil and water ledgers");
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double massCorrection = solvedFaces[face][phase].getSignedMassFlowKgPerSecond()
            - freeFaces[face][phase].getSignedMassFlowKgPerSecond();
        double momentumCorrection = solvedFaces[face][phase].getAdvectiveMomentumN()
            - freeFaces[face][phase].getAdvectiveMomentumN();
        corrected[face][phase] -= massCorrection / cellLengthsM[face];
        corrected[face + 1][phase] += massCorrection / cellLengthsM[face + 1];
        corrected[face][phase + PHASE_COUNT] -= momentumCorrection / cellLengthsM[face];
        corrected[face + 1][phase + PHASE_COUNT] += momentumCorrection / cellLengthsM[face + 1];
      }
    }
    return corrected;
  }

  /**
   * Refresh exact donor-presence rows without using an inventory floor.
   *
   * @param evaluation current free model evaluation
   * @param endpointState current endpoint state
   * @param state auxiliary state to update
   * @return true when a frozen row changed
   */
  static boolean refreshActiveSet(UnsplitTransientSolver.Evaluation evaluation, double[][] endpointState, State state) {
    DirectionalPhaseFaceFlux[][] faces = requireFaces(evaluation, endpointState.length);
    boolean changed = false;
    for (int face = 0; face < faces.length; face++) {
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        int direction = state.getDirection(face, phase);
        int donorCell = direction > 0 ? face : face + 1;
        boolean absent = endpointState[donorCell][phase] == 0.0 || donorMass(faces[face][phase], direction) == 0.0;
        if (absent && !state.isConstrained(face, phase)) {
          state.constrain(face, phase);
          changed = true;
        }
      }
    }
    return changed;
  }

  /** @return augmented nonlinear dimension for a cell count */
  static int dimension(int cellCount) {
    return cellCount * UnsplitTransientSolver.BLOCK_SIZE + Math.max(0, cellCount - 1) * VARIABLES_PER_FACE;
  }

  /** @return first global auxiliary column/row */
  static int auxiliaryOffset(int cellCount) {
    return cellCount * UnsplitTransientSolver.BLOCK_SIZE;
  }

  /** @return global auxiliary column for a face, phase and local variable */
  static int column(int cellCount, int face, int phase, int variable) {
    return auxiliaryOffset(cellCount) + variableOffset(face, phase) + variable;
  }

  /**
   * Return the exact declared color for one augmented column.
   *
   * @param cellCount cell count
   * @param column global nonlinear column
   * @return color in the minimum 53-color structural schedule
   */
  static int color(int cellCount, int column) {
    int cellDimension = auxiliaryOffset(cellCount);
    if (column < 0 || column >= dimension(cellCount)) {
      throw new IllegalArgumentException("Column is outside the augmented nonlinear layout");
    }
    if (column < cellDimension) {
      int cell = column / UnsplitTransientSolver.BLOCK_SIZE;
      int variable = column % UnsplitTransientSolver.BLOCK_SIZE;
      return variable * 5 + cell % 5;
    }
    int local = column - cellDimension;
    int face = local / VARIABLES_PER_FACE;
    int variable = local % VARIABLES_PER_FACE;
    return CELL_COLOR_COUNT + variable * 2 + face % 2;
  }

  /** @return local phase-face index */
  private static int phaseFace(int face, int phase) {
    return face * PHASE_COUNT + phase;
  }

  /** @return local variable offset */
  private static int variableOffset(int face, int phase) {
    return phaseFace(face, phase) * VARIABLES_PER_PHASE_FACE;
  }

  /** @return donor mass per length selected by a frozen direction */
  private static double donorMass(DirectionalPhaseFaceFlux face, int direction) {
    return direction > 0 ? face.getLeftDonorMassKgPerM() : face.getRightDonorMassKgPerM();
  }

  /** @return donor momentum per length selected by a frozen direction */
  private static double donorMomentum(DirectionalPhaseFaceFlux face, int direction) {
    return direction > 0 ? face.getLeftDonorMomentumKgPerSecond() : face.getRightDonorMomentumKgPerSecond();
  }

  /** Require the complete face ledger. */
  private static DirectionalPhaseFaceFlux[][] requireFaces(UnsplitTransientSolver.Evaluation evaluation,
      int cellCount) {
    DirectionalPhaseFaceFlux[][] faces = evaluation.getDirectionalPhaseFaceFluxes();
    if (faces == null || faces.length != Math.max(0, cellCount - 1)) {
      throw new IllegalArgumentException("Signed-face complementarity requires every internal phase face");
    }
    return faces;
  }

  /** Require physical cell lengths. */
  private static double[] requireLengths(UnsplitTransientSolver.Evaluation evaluation, int cellCount) {
    double[] lengths = evaluation.getCellLengthsM();
    if (lengths == null || lengths.length != cellCount) {
      throw new IllegalArgumentException("Signed-face complementarity requires every physical cell length");
    }
    return lengths;
  }

  /** Copy a rectangular primitive array. */
  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int row = 0; row < values.length; row++) {
      result[row] = values[row].clone();
    }
    return result;
  }

  /** Copy an immutable face array structure. */
  private static DirectionalPhaseFaceFlux[][] copyFaces(DirectionalPhaseFaceFlux[][] values) {
    DirectionalPhaseFaceFlux[][] result = new DirectionalPhaseFaceFlux[values.length][];
    for (int face = 0; face < values.length; face++) {
      result[face] = values[face].clone();
    }
    return result;
  }
}

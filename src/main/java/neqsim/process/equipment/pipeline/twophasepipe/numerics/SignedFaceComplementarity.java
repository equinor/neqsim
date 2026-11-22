package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static neqsim.util.util.ArrayUtils.copyMatrixArray;

import java.io.Serializable;

/**
 * Compressed signed-face complementarity block for conservative phase transport.
 *
 * <p>
 * Every internal gas, oil and water face owns two one-sided donor streams. Each stream has a nonnegative mass-flow
 * magnitude, advective momentum flux and a nonnegative donor-presence multiplier. The active donor rows are frozen
 * while a Jacobian is assembled, then refreshed from the unmodified endpoint state and free directional flux between
 * Newton linearizations. A present donor uses the normalized conservative relation between mass and momentum transport;
 * an absent donor pins advective momentum to zero exactly. Replacing the free internal flux in both adjacent cells
 * preserves total mass and momentum without clipping either cell inventory. Retaining the two streams independently
 * also preserves counter-transport created by pressure interpolation instead of switching the complete face flux to
 * whichever donor owns the net signed flow.
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
  static final int FORWARD = 0;
  static final int REVERSE = 1;
  static final int VARIABLES_PER_DIRECTION = 3;
  static final int VARIABLES_PER_PHASE_FACE = 2 * VARIABLES_PER_DIRECTION;
  static final int VARIABLES_PER_FACE = PHASE_COUNT * VARIABLES_PER_PHASE_FACE;
  static final int MASS_FLOW = 0;
  static final int ADVECTIVE_MOMENTUM = 1;
  static final int MULTIPLIER = 2;
  static final int CELL_COLOR_COUNT = 35;
  static final int TOTAL_COLOR_COUNT = CELL_COLOR_COUNT + 2 * VARIABLES_PER_FACE;

  /** Frozen active rows, unknowns and physical scales for one nonlinear iterate. */
  static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int cellCount;
    private final boolean[] constrained;
    private final boolean[] anchor;
    private final double[] values;
    private final double[] scales;
    private final double[] donorMassScales;

    /** Build state from a validated free-flux evaluation. */
    private State(int cellCount, DirectionalPhaseFaceFlux[][] faces, double[][] endpointState) {
      this.cellCount = cellCount;
      int phaseFaceCount = Math.max(0, cellCount - 1) * PHASE_COUNT;
      constrained = new boolean[phaseFaceCount * 2];
      anchor = new boolean[constrained.length];
      values = new double[phaseFaceCount * VARIABLES_PER_PHASE_FACE];
      scales = new double[values.length];
      donorMassScales = new double[constrained.length];
      for (int face = 0; face < faces.length; face++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          DirectionalPhaseFaceFlux free = faces[face][phase];
          initializeDirection(face, phase, FORWARD, free.getForwardMassFlowKgPerSecond(),
              free.getForwardAdvectiveMomentumN(), free.getLeftDonorMassKgPerM(), endpointState[face][phase]);
          initializeDirection(face, phase, REVERSE, free.getReverseMassFlowKgPerSecond(),
              free.getReverseAdvectiveMomentumN(), free.getRightDonorMassKgPerM(), endpointState[face + 1][phase]);
        }
      }
      assignAnchors(endpointState);
    }

    /** Initialize one directional stream and its donor-presence row. */
    private void initializeDirection(int face, int phase, int direction, double massFlow, double momentum,
        double freeDonorMass, double endpointDonorMass) {
      int offset = directionalOffset(face, phase, direction);
      int stream = stream(face, phase, direction);
      values[offset + MASS_FLOW] = massFlow;
      values[offset + ADVECTIVE_MOMENTUM] = momentum;
      values[offset + MULTIPLIER] = 0.0;
      scales[offset + MASS_FLOW] = Math.max(1.0, Math.abs(massFlow));
      scales[offset + ADVECTIVE_MOMENTUM] = Math.max(1.0, Math.abs(momentum));
      scales[offset + MULTIPLIER] = scales[offset + MASS_FLOW];
      donorMassScales[stream] = Math.max(1.0, Math.abs(endpointDonorMass));
      constrained[stream] = endpointDonorMass == 0.0 || freeDonorMass == 0.0;
    }

    /**
     * Copy a frozen choice set for an independently reversible line-search trial.
     *
     * @param source state to copy
     */
    private State(State source) {
      cellCount = source.cellCount;
      constrained = source.constrained.clone();
      anchor = source.anchor.clone();
      values = source.values.clone();
      scales = source.scales.clone();
      donorMassScales = source.donorMassScales.clone();
    }

    /** @return independent copy of values, scales and frozen choices */
    State copy() {
      return new State(this);
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

    /** @return whether the donor-presence constraint row is active */
    boolean isConstrained(int face, int phase, int direction) {
      return constrained[stream(face, phase, direction)];
    }

    /** Activate an exact donor-presence row. */
    void constrain(int face, int phase, int direction) {
      constrained[stream(face, phase, direction)] = true;
    }

    /** Release a donor-presence row. */
    void release(int face, int phase, int direction) {
      constrained[stream(face, phase, direction)] = false;
    }

    /** @return whether this row is the unique exact-presence anchor for its donor cell and phase */
    boolean isAnchor(int face, int phase, int direction) {
      return anchor[stream(face, phase, direction)];
    }

    /** @return fixed donor-mass scale for an exact-presence anchor */
    double getDonorMassScale(int face, int phase, int direction) {
      return donorMassScales[stream(face, phase, direction)];
    }

    /**
     * Assign at most one endpoint-mass row to each absent cell phase. Other constrained outgoing streams use exact
     * zero-flow rows, avoiding duplicate constraints across a phase-free region.
     *
     * @return true when an anchor choice changed
     */
    boolean assignAnchors(double[][] endpointState) {
      boolean[] selected = new boolean[anchor.length];
      boolean[][] assigned = new boolean[cellCount][PHASE_COUNT];
      boolean[][] faceAssigned = new boolean[Math.max(0, cellCount - 1)][PHASE_COUNT];
      for (int face = 0; face < cellCount - 1; face++) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          for (int direction = FORWARD; direction <= REVERSE; direction++) {
            int donorCell = direction == FORWARD ? face : face + 1;
            int stream = stream(face, phase, direction);
            if (constrained[stream] && endpointState[donorCell][phase] == 0.0 && !assigned[donorCell][phase]
                && !faceAssigned[face][phase]) {
              selected[stream] = true;
              assigned[donorCell][phase] = true;
              faceAssigned[face][phase] = true;
              donorMassScales[stream] = Math.max(1.0, Math.abs(endpointState[donorCell][phase]));
            }
          }
        }
      }
      boolean changed = false;
      for (int stream = 0; stream < anchor.length; stream++) {
        changed |= anchor[stream] != selected[stream];
        anchor[stream] = selected[stream];
      }
      return changed;
    }

  }

  /** Corrected conservative rates, face residuals and solved directional ledgers. */
  static final class Evaluation {
    private final double[][] conservativeRates;
    private final double[] residual;
    private final DirectionalPhaseFaceFlux[][] solvedFaces;

    /** Store defensive results from one auxiliary evaluation. */
    private Evaluation(double[][] conservativeRates, double[] residual, DirectionalPhaseFaceFlux[][] solvedFaces) {
      this.conservativeRates = copyMatrixArray(conservativeRates);
      this.residual = residual.clone();
      this.solvedFaces = copyMatrixArray(solvedFaces);
    }

    /** @return defensive corrected conservative rates */
    double[][] getConservativeRates() {
      return copyMatrixArray(conservativeRates);
    }

    /** @return defensive scaled auxiliary residual */
    double[] getResidual() {
      return residual.clone();
    }

    /** @return defensive solved face ledgers */
    DirectionalPhaseFaceFlux[][] getSolvedFaces() {
      return copyMatrixArray(solvedFaces);
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
   * Replace free internal transport by the two directional unknown streams and evaluate the frozen KKT rows.
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
        DirectionalPhaseFaceFlux free = freeFaces[face][phase];
        int forwardOffset = directionalOffset(face, phase, FORWARD);
        int reverseOffset = directionalOffset(face, phase, REVERSE);
        double forwardMass = values[forwardOffset + MASS_FLOW];
        double reverseMass = values[reverseOffset + MASS_FLOW];
        double forwardMomentum = values[forwardOffset + ADVECTIVE_MOMENTUM];
        double reverseMomentum = values[reverseOffset + ADVECTIVE_MOMENTUM];
        double signedMass = forwardMass - reverseMass;
        double momentum = forwardMomentum + reverseMomentum;
        double massCorrection = signedMass - free.getSignedMassFlowKgPerSecond();
        double momentumCorrection = momentum - free.getAdvectiveMomentumN();
        corrected[face][phase] -= massCorrection / lengths[face];
        corrected[face + 1][phase] += massCorrection / lengths[face + 1];
        corrected[face][phase + PHASE_COUNT] -= momentumCorrection / lengths[face];
        corrected[face + 1][phase + PHASE_COUNT] += momentumCorrection / lengths[face + 1];

        evaluateDirection(residual, values, state, endpointState, free, face, phase, FORWARD);
        evaluateDirection(residual, values, state, endpointState, free, face, phase, REVERSE);
        solved[face][phase] = new DirectionalPhaseFaceFlux(forwardMass, reverseMass, forwardMomentum, reverseMomentum,
            free.getPressureTractionN(), free.getLeftDonorMassKgPerM(), free.getRightDonorMassKgPerM(),
            free.getLeftDonorMomentumKgPerSecond(), free.getRightDonorMomentumKgPerSecond());
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
    double[][] corrected = copyMatrixArray(rates);
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
   * Refresh both exact donor-presence rows without using an inventory floor.
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
        changed |= refreshDirection(faces[face][phase], endpointState, state, face, phase, FORWARD);
        changed |= refreshDirection(faces[face][phase], endpointState, state, face, phase, REVERSE);
      }
    }
    changed |= state.assignAnchors(endpointState);
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
   * @return color in the minimum 71-color structural schedule
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

  /** @return local variable offset for one donor direction */
  private static int directionalOffset(int face, int phase, int direction) {
    return variableOffset(face, phase) + direction * VARIABLES_PER_DIRECTION;
  }

  /** @return local stream index for one donor direction */
  private static int stream(int face, int phase, int direction) {
    return phaseFace(face, phase) * 2 + direction;
  }

  /** Evaluate one directional mass, momentum and donor-presence row. */
  private static void evaluateDirection(double[] residual, double[] values, State state, double[][] endpointState,
      DirectionalPhaseFaceFlux free, int face, int phase, int direction) {
    int offset = directionalOffset(face, phase, direction);
    double massFlow = values[offset + MASS_FLOW];
    double momentum = values[offset + ADVECTIVE_MOMENTUM];
    double multiplier = values[offset + MULTIPLIER];
    double freeMassFlow = direction == FORWARD ? free.getForwardMassFlowKgPerSecond()
        : free.getReverseMassFlowKgPerSecond();
    double donorMass = direction == FORWARD ? free.getLeftDonorMassKgPerM() : free.getRightDonorMassKgPerM();
    double donorMomentum = direction == FORWARD ? free.getLeftDonorMomentumKgPerSecond()
        : free.getRightDonorMomentumKgPerSecond();
    double massScale = state.scales[offset + MASS_FLOW];
    double momentumScale = state.scales[offset + ADVECTIVE_MOMENTUM];
    residual[offset + MASS_FLOW] = (massFlow - freeMassFlow + multiplier) / massScale;
    if (donorMass == 0.0) {
      residual[offset + ADVECTIVE_MOMENTUM] = momentum / momentumScale;
    } else {
      double donorRelation = direction == FORWARD ? momentum - massFlow * donorMomentum / donorMass
          : momentum + massFlow * donorMomentum / donorMass;
      residual[offset + ADVECTIVE_MOMENTUM] = donorRelation / momentumScale;
    }
    if (state.isAnchor(face, phase, direction)) {
      int donorCell = direction == FORWARD ? face : face + 1;
      residual[offset + MULTIPLIER] = endpointState[donorCell][phase] / state.getDonorMassScale(face, phase, direction);
    } else {
      residual[offset + MULTIPLIER] = state.isConstrained(face, phase, direction) ? massFlow / massScale
          : multiplier / massScale;
    }
  }

  /** Refresh one directional donor-presence row. */
  private static boolean refreshDirection(DirectionalPhaseFaceFlux free, double[][] endpointState, State state,
      int face, int phase, int direction) {
    int donorCell = direction == FORWARD ? face : face + 1;
    double donorMass = direction == FORWARD ? free.getLeftDonorMassKgPerM() : free.getRightDonorMassKgPerM();
    boolean absent = endpointState[donorCell][phase] == 0.0 || donorMass == 0.0;
    if (absent && !state.isConstrained(face, phase, direction)) {
      state.constrain(face, phase, direction);
      return true;
    }
    if (!absent && state.isConstrained(face, phase, direction)) {
      state.release(face, phase, direction);
      return true;
    }
    return false;
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

}

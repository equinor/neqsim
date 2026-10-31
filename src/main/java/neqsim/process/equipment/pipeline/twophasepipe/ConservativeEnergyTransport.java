package neqsim.process.equipment.pipeline.twophasepipe;

/**
 * Conservative total-enthalpy transport associated with accepted phase mass-face corrections.
 *
 * <p>
 * A pressure correction that transports phase mass must also transport its total enthalpy. This operator applies only
 * that missing transport: pressure work is included in enthalpy, and phase kinetic energy belongs in the supplied
 * specific total enthalpy. It does not add a separate pressure-work, Joule-Thomson or latent-heat source. The caller
 * owns phase mass and momentum updates and must use the same accepted face transfers for this operator.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ConservativeEnergyTransport {
  /** Number of independently transported fluid phases. */
  private static final int PHASE_COUNT = 3;

  /** Conservative total-energy column. */
  private static final int ENERGY = 6;

  /** Utility class. */
  private ConservativeEnergyTransport() {
  }

  /**
   * Add energy associated with signed, time-integrated gas/oil/water mass transfers.
   *
   * <p>
   * Positive transfer points from inlet to outlet. Internal faces use the frozen upwind cell state and exchange exactly
   * one energy amount between adjacent cells, including nonuniform cells. The inlet uses the supplied feed state for
   * inflow and the first cell for outflow. Outlet inflow is rejected because this interface has no external outlet
   * composition or enthalpy. Zero transfers never inspect unused phase properties. All validation and candidate energy
   * calculations complete before any conservative state entry is changed.
   * </p>
   *
   * @param state conservative state with seven entries per cell, modified only in the total-energy column, J/m
   * @param phaseMassTransfersKg signed integrated phase masses at each face, with shape {@code [cells + 1][3]}, kg
   * @param cellPhaseTotalEnthalpyJPerKg immutable donor specific enthalpy plus phase kinetic energy,
   * {@code h + velocity * velocity / 2}, with shape {@code [cells][3]}, J/kg
   * @param inletPhaseTotalEnthalpyJPerKg feed specific total enthalpy for the three phases, J/kg
   * @param cellLengths finite positive physical cell lengths, m
   * @return signed inlet and outlet energy transfers in J, in that order
   * @throws IllegalArgumentException if the shapes, lengths or used transfer properties are invalid, an outlet transfer
   * is inward, or the candidate energy is nonfinite
   */
  public static double[] applyFaceTransfers(double[][] state, double[][] phaseMassTransfersKg,
      double[][] cellPhaseTotalEnthalpyJPerKg, double[] inletPhaseTotalEnthalpyJPerKg, double[] cellLengths) {
    double[][] phaseBoundaryEnergyJ = applyPhaseResolvedFaceTransfers(state, phaseMassTransfersKg,
        cellPhaseTotalEnthalpyJPerKg, inletPhaseTotalEnthalpyJPerKg, cellLengths);
    return new double[] {sum(phaseBoundaryEnergyJ[0]), sum(phaseBoundaryEnergyJ[1])};
  }

  /**
   * Add energy associated with signed phase mass transfers and retain each boundary phase contribution.
   *
   * <p>
   * This is the phase-resolved form of
   * {@link #applyFaceTransfers(double[][], double[][], double[][], double[], double[])}. It applies the same
   * transactional cell-energy update and returns inlet/outlet rows with gas, oil and water columns. A row sum is
   * therefore exactly the legacy boundary routing term apart from floating-point round-off.
   * </p>
   *
   * @param state conservative state with seven entries per cell, modified only in the total-energy column, J/m
   * @param phaseMassTransfersKg signed integrated phase masses at each face, shape {@code [cells + 1][3]}, kg
   * @param cellPhaseTotalEnthalpyJPerKg donor specific total enthalpies, shape {@code [cells][3]}, J/kg
   * @param inletPhaseTotalEnthalpyJPerKg inlet gas/oil/water specific total enthalpies, J/kg
   * @param cellLengths finite positive physical cell lengths, m
   * @return inlet/outlet phase energy transfers in J with shape {@code [2][3]}
   * @throws IllegalArgumentException if shapes, lengths or used donor properties are invalid
   */
  public static double[][] applyPhaseResolvedFaceTransfers(double[][] state, double[][] phaseMassTransfersKg,
      double[][] cellPhaseTotalEnthalpyJPerKg, double[] inletPhaseTotalEnthalpyJPerKg, double[] cellLengths) {
    int cells = state == null ? 0 : state.length;
    if (cells == 0 || phaseMassTransfersKg == null || phaseMassTransfersKg.length != cells + 1
        || cellPhaseTotalEnthalpyJPerKg == null || cellPhaseTotalEnthalpyJPerKg.length != cells
        || inletPhaseTotalEnthalpyJPerKg == null || inletPhaseTotalEnthalpyJPerKg.length != PHASE_COUNT
        || cellLengths == null || cellLengths.length != cells) {
      throw new IllegalArgumentException("Energy transport requires compatible cell, phase and face arrays");
    }
    double[] energyChangesJ = new double[cells];
    for (int cell = 0; cell < cells; cell++) {
      if (state[cell] == null || state[cell].length != ENERGY + 1 || cellPhaseTotalEnthalpyJPerKg[cell] == null
          || cellPhaseTotalEnthalpyJPerKg[cell].length != PHASE_COUNT || !(cellLengths[cell] > 0.0)
          || !Double.isFinite(cellLengths[cell]) || !Double.isFinite(state[cell][ENERGY])) {
        throw new IllegalArgumentException(
            "Energy transport requires seven finite-energy variables and positive lengths");
      }
    }
    double[][] boundaryEnergyJ = new double[2][PHASE_COUNT];
    for (int face = 0; face <= cells; face++) {
      if (phaseMassTransfersKg[face] == null || phaseMassTransfersKg[face].length != PHASE_COUNT) {
        throw new IllegalArgumentException("Each energy-transport face requires three phase transfers");
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        double massKg = phaseMassTransfersKg[face][phase];
        if (!Double.isFinite(massKg)) {
          throw new IllegalArgumentException("Energy-transport phase mass transfer must be finite");
        }
        if (massKg == 0.0) {
          continue;
        }
        if (face == cells && massKg < 0.0) {
          throw new IllegalArgumentException(
              "Conservative energy transport requires an external state for outlet inflow");
        }
        int donor = massKg > 0.0 ? face - 1 : face;
        double specificEnergy = donor < 0 ? inletPhaseTotalEnthalpyJPerKg[phase]
            : cellPhaseTotalEnthalpyJPerKg[donor][phase];
        double transferJ = massKg * specificEnergy;
        if (!Double.isFinite(specificEnergy) || !Double.isFinite(transferJ)) {
          throw new IllegalArgumentException("Energy-transport donor total enthalpy must be finite");
        }
        if (face > 0) {
          energyChangesJ[face - 1] -= transferJ;
        } else {
          boundaryEnergyJ[0][phase] += transferJ;
        }
        if (face < cells) {
          energyChangesJ[face] += transferJ;
        } else {
          boundaryEnergyJ[1][phase] += transferJ;
        }
      }
    }
    double[] candidateEnergyPerLength = new double[cells];
    for (int cell = 0; cell < cells; cell++) {
      candidateEnergyPerLength[cell] = state[cell][ENERGY] + energyChangesJ[cell] / cellLengths[cell];
      if (!Double.isFinite(candidateEnergyPerLength[cell])) {
        throw new IllegalArgumentException("Conservative transported energy must remain finite");
      }
    }
    for (int cell = 0; cell < cells; cell++) {
      state[cell][ENERGY] = candidateEnergyPerLength[cell];
    }
    return boundaryEnergyJ;
  }

  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }
}

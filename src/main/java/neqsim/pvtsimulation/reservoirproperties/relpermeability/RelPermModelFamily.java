package neqsim.pvtsimulation.reservoirproperties.relpermeability;

/**
 * Enumeration of relative permeability model families.
 *
 * <ul>
 * <li>{@link #COREY} - Corey power-law model (1954). Uses exponents no, nw, ng.</li>
 * <li>{@link #LET} - LET three-parameter model (Lomeland-Ebeltoft-Thomas, 2005). Uses L, E, T shape
 * parameters for each phase.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public enum RelPermModelFamily {
  /**
   * Corey power-law relative permeability model.
   *
   * <p>
   * For oil-water:
   *
   * $$ K_{rw} = K_{rw,max} \cdot S_{wn}^{n_w} $$
   *
   * $$ K_{row} = K_{ro,max} \cdot (1 - S_{wn})^{n_o} $$
   *
   * where $S_{wn}$ is the normalized water saturation.
   */
  COREY,

  /**
   * LET three-parameter relative permeability model.
   *
   * <p>
   * Provides greater flexibility than Corey for matching laboratory core-flood data. Each phase
   * curve uses three parameters (L, E, T) controlling the shape of the relative permeability curve:
   *
   * $$ K_r = K_{r,max} \cdot \frac{S_n^L}{S_n^L + E \cdot (1 - S_n)^T} $$
   *
   * where $S_n$ is the normalized saturation for the relevant phase.
   *
   * @see <a href="https://doi.org/10.2118/108264-MS">Lomeland, Ebeltoft, Thomas - SPE 108264</a>
   */
  LET
}

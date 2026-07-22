package neqsim.pvtsimulation.reservoirproperties.relpermeability;

/**
 * Enumeration of relative permeability table types for reservoir simulation.
 *
 * <p>
 * These correspond to standard Eclipse simulator keywords:
 * <ul>
 * <li>{@link #SWOF} - Water-Oil Function (Sw, Krw, Krow, Pcow)</li>
 * <li>{@link #SGOF} - Gas-Oil Function (Sg, Krg, Krog, Pcog)</li>
 * <li>{@link #SOF3} - Oil Function of Oil Saturation (So, Krow, Krog)</li>
 * <li>{@link #SLGOF} - Liquid-Gas Function (Sl, Krg, Krog, Pcog)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public enum RelPermTableType {
  /**
   * Water-Oil Function: columns are Sw, Krw, Krow, Pcow.
   */
  SWOF,

  /**
   * Gas-Oil Function: columns are Sg, Krg, Krog, Pcog.
   */
  SGOF,

  /**
   * Oil Function (three-phase): columns are So, Krow, Krog.
   */
  SOF3,

  /**
   * Liquid-Gas Function: columns are Sl, Krg, Krog, Pcog.
   */
  SLGOF
}

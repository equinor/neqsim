package neqsim.fluidmechanics.flownode;

/**
 * Enumeration of wall heat transfer models for pipe flow.
 *
 * <p>
 * Different thermal boundary conditions for heat transfer calculations at the pipe wall.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public enum WallHeatTransferModel {
  /**
   * Adiabatic wall - no heat transfer to/from surroundings. q'' = 0
   */
  ADIABATIC,

  /**
   * Constant wall temperature boundary condition. T_wall = T_specified (Dirichlet condition)
   */
  CONSTANT_WALL_TEMPERATURE,

  /**
   * Constant heat flux at the wall. q'' = q''_specified (Neumann condition)
   */
  CONSTANT_HEAT_FLUX,

  /**
   * Convective heat transfer to/from ambient. q'' = U_overall * (T_ambient - T_fluid) where
   * U_overall includes pipe wall, insulation, and external convection resistances.
   */
  CONVECTIVE_BOUNDARY,

  /**
   * Time-varying wall temperature. T_wall = f(t, position)
   */
  TRANSIENT_WALL_TEMPERATURE
}

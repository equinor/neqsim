package neqsim.process.equipment.distillation;

/**
 * Equation groups used in the distillation column MESH residual vector.
 *
 * @author esol
 * @version 1.0
 */
enum ColumnMeshEquationType {
  /** Component material balance residual. */
  MATERIAL,
  /** Phase equilibrium residual. */
  EQUILIBRIUM,
  /** Phase mole-fraction summation residual. */
  SUMMATION,
  /** Tray heat balance residual. */
  ENERGY,
  /** Active column specification residual. */
  SPECIFICATION
}

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
  /** Product draw consistency residual between terminal tray traffic and public products. */
  PRODUCT_DRAW,
  /** Active column specification residual. */
  SPECIFICATION
}

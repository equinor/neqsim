package neqsim.fluidmechanics.flowsolver;

/** Boundary conditions used by physical axial dispersion in one-phase component transport. */
public enum AxialDispersionBoundaryCondition {
  /** Inlet mass fraction is prescribed by the transient inlet thermodynamic system. */
  DIRICHLET_INLET,
  /** Outlet physical diffusive flux is zero; component departure remains convective. */
  ZERO_GRADIENT_OUTLET
}

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;

/**
 * Reserved numerical reactive enhancement model. A flux-ratio calculation is not implemented; construction and
 * calculation fail explicitly rather than returning the zero-initialized enhancement vector.
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnhancementFactorNumeric extends EnhancementFactor {
  /**
   * Constructor for EnhancementFactorNumeric.
   *
   * @param fluidBoundary a
   * {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface} object
   * @throws UnsupportedOperationException always, because numerical enhancement is not implemented
   */
  public EnhancementFactorNumeric(FluidBoundaryInterface fluidBoundary) {
    super(fluidBoundary);
    throw new UnsupportedOperationException("Numerical reactive enhancement is not implemented.");
  }

  /** {@inheritDoc} */
  @Override
  public void calcEnhancementVec(int phaseNum) {
    throw new UnsupportedOperationException("Numerical reactive enhancement is not implemented.");
  }

  /**
   * calcEnhancementMatrix.
   *
   * @param phaseNum a int
   * @throws UnsupportedOperationException always, because numerical enhancement is not implemented
   */
  public void calcEnhancementMatrix(int phaseNum) {
    throw new UnsupportedOperationException("Numerical reactive enhancement is not implemented.");
  }
}

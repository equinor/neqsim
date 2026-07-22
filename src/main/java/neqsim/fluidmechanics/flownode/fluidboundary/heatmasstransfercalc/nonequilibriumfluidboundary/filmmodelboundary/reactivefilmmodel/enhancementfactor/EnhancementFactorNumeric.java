package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.fluidboundarynonreactive.FluidBoundarySystemNonReactive;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.finitevolumeboundary.fluidboundarysystem.fluidboundarysystemreactive.FluidBoundarySystemReactive;

/**
 * EnhancementFactorNumeric class.
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
   */
  public EnhancementFactorNumeric(FluidBoundaryInterface fluidBoundary) {
    super(fluidBoundary);
    // fluidBoundary.setNumericSolve(true);
    reactiveInterface = new FluidBoundarySystemReactive(fluidBoundary);
    nonReactiveInterface = new FluidBoundarySystemNonReactive(fluidBoundary);
    reactiveInterface.createSystem();
    nonReactiveInterface.createSystem();
    // numericInterface.createSystem();
  }

  /**
   * calcEnhancementMatrix.
   *
   * @param phaseNum a int
   */
  public void calcEnhancementMatrix(int phaseNum) {
    reactiveInterface.createSystem();
    nonReactiveInterface.createSystem();
    nonReactiveInterface.solve();
    reactiveInterface.solve();
    for (int i = 0; i < fluidBoundary.getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); i++) {
      for (int j = 0; j < fluidBoundary.getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        // enhancementFactor[1].set(i,j,0);
        // System.out.println("num enhancement " + enhancementFactor[1].get(i,j));
      }
    }
  }
}

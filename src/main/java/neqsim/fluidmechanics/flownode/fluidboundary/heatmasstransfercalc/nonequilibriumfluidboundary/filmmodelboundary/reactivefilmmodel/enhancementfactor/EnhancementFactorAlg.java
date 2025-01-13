package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor;

import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;

/**
 * <p>
 * EnhancementFactorAlg class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnhancementFactorAlg extends EnhancementFactor {
  /**
   * <p>
   * Constructor for EnhancementFactorAlg.
   * </p>
   *
   * @param fluidBoundary a
   *        {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface}
   *        object
   */
  public EnhancementFactorAlg(FluidBoundaryInterface fluidBoundary) {
    super(fluidBoundary);
  }

  /** {@inheritDoc} */
  @Override
  public void calcEnhancementVec(int phaseNum) {
    double hatta = 0.0;
    for (int j = 0; j < fluidBoundary.getBulkSystem().getPhase(phaseNum)
        .getNumberOfComponents(); j++) {
      if (fluidBoundary.getBulkSystem().getPhase(phaseNum).getComponentName(j).equals("CO2")
          && phaseNum == 1) {
        enhancementVec[j] = fluidBoundary.getBulkSystem().getChemicalReactionOperations()
            .solveKinetics(phaseNum, fluidBoundary.getInterphaseSystem().getPhase(phaseNum), j);
        // System.out.println("enh " + enhancementVec[j]);
        hatta = Math
            .sqrt(enhancementVec[j] * fluidBoundary.getBulkSystem().getPhase(phaseNum)
                .getPhysicalProperties().getEffectiveDiffusionCoefficient(j))
            / fluidBoundary.getEffectiveMassTransferCoefficient(phaseNum, j);
        hattaNumber[j] = hatta;
        // System.out.println("hatta " + hatta);
        double phi = fluidBoundary.getBulkSystem().getChemicalReactionOperations().getKinetics()
            .getPhiInfinite();
        // System.out.println("phi " + phi);
        if (hatta > 2.0) {
          enhancementVec[j] = 1.0 + (phi - 1.0) * (1.0 - Math.exp(-(hatta - 1.0) / (phi - 1.0)));
        } else {
          enhancementVec[j] = 1.0
              + (phi - 1.0) * (1.0 - Math.exp(-1.0 / (phi - 1.0))) * Math.exp(1.0 - 2.0 / hatta);
        }
        // System.out.println("enh " +enhancementVec[j] );
      } else {
        enhancementVec[j] = 1.0;
      }
    }
  }

  // public void calcEnhancementMatrix(int phaseNum){
  // double z=1;
  // //fluidBoundary.getBulkSystem().getChemicalReactionOperations().solveKinetics(1);
  // double[][] reacMatrix =
  // fluidBoundary.getBulkSystem().getChemicalReactionOperations().getReactionList().getReacMatrix();
  // double[][] stocMatrix =
  // fluidBoundary.getBulkSystem().getChemicalReactionOperations().getReactionList().getStocMatrix();
  // fluidBoundary.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().calcEffectiveDiffusionCoefficients();

  // for(int
  // i=0;i<fluidBoundary.getBulkSystem().getPhase(phaseNum).getNumberOfComponents();i++){
  // for(int
  // j=0;j<fluidBoundary.getBulkSystem().getPhase(phaseNum).getNumberOfComponents();j++){
  // if(i==j || (stocMatrix[i][i]*stocMatrix[i][j])<=0)
  // enhancementFactor[phase].set(i,j, 1.0);
  // else{
  // double pseudoReacRate =
  // reacMatrix[i][j]*fluidBoundary.getBulkSystem().getPhase(phaseNum).getComponent(j).getx()*fluidBoundary.getBulkSystem().getPhase(phaseNum).getDensity()/fluidBoundary.getBulkSystem().getPhase(phaseNum).getMolarMass()*Math.abs(stocMatrix[i][i]);
  // hattaNumber[phase].set(i,j,Math.sqrt(pseudoReacRate*fluidBoundary.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getEffectiveDiffusionCoefficient(i))/fluidBoundary.getBinaryMassTransferCoefficient(phase,i,j));
  // double phiVal = 1.0 +
  // (fluidBoundary.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getEffectiveDiffusionCoefficient(j)/fluidBoundary.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getEffectiveDiffusionCoefficient(i))*
  // (stocMatrix[i][i]/stocMatrix[i][j]) *
  // (fluidBoundary.getBulkSystem().getPhase(phaseNum).getComponent(j).getx()*fluidBoundary.getBulkSystem().getPhase(phaseNum).getDensity()/fluidBoundary.getBulkSystem().getPhase(phaseNum).getMolarMass())/
  // (fluidBoundary.getInterphaseSystem().getPhase(phaseNum).getComponent(i).getx()*fluidBoundary.getInterphaseSystem().getPhase(phaseNum).getDensity()/fluidBoundary.getInterphaseSystem().getPhase(phaseNum).getMolarMass());

  // System.out.println("components " +
  // fluidBoundary.getBulkSystem().getPhase(phaseNum).getComponent(i).getComponentName()
  // + " " +
  // fluidBoundary.getBulkSystem().getPhase(phaseNum).getComponent(j).getComponentName());
  // System.out.println("hatta : " + hattaNumber[phase].get(i,j));
  // System.out.println("phi : " + phiVal);

  // if(hattaNumber[phase].get(i,j)>=2.0){
  // // Functions F = Functions.functions;
  // cern.colt.function.DoubleFunction fun = cern.jet.math.Functions.tanh;
  // double val =
  // hattaNumber[phase].get(i,j)/fun.apply(hattaNumber[phase].get(i,j));
  // enhancementFactor[1].set(i,j, (1.0 +
  // (phiVal-1.0)*(1.0-Math.exp(-(hattaNumber[phase].get(i,j)-1.0)/(phiVal-1.0)))));
  // }
  // else{
  // enhancementFactor[1].set(i,j, (1.0 +
  // (phiVal-1.0)*(1.0-Math.exp(-1.0/(phiVal-1.0)))*Math.exp(1.0-2.0/hattaNumber[phase].get(i,j))));
  // }
  // if(enhancementFactor[1].get(i,j)>1000) enhancementFactor[1].set(i,j,1000.0);
  // System.out.println("enhance : " + enhancementFactor[1].get(i,j) + " phase " +
  // phase);
  // }
  // }
  // }
  // }
}

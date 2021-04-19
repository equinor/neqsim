/*
 * EnhancementFactorAlgebraic.java
 *
 * Created on 3. august 2001, 13:46
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;

/**
 *
 * @author esol
 * @version
 */
public class EnhancementFactorAlg extends EnhancementFactor {

    private static final long serialVersionUID = 1000;

    public EnhancementFactorAlg() {
        super();
    }

    public EnhancementFactorAlg(FluidBoundaryInterface fluidBoundary) {
        super(fluidBoundary);
    }

    @Override
	public void calcEnhancementVec(int phase) {
        double hatta = 0.0;
        for (int j = 0; j < fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents(); j++) {
            if (fluidBoundary.getBulkSystem().getPhases()[phase].getComponent(j).getName().equals("CO2")
                    && phase == 1) {
                enhancementVec[j] = fluidBoundary.getBulkSystem().getChemicalReactionOperations().solveKinetics(phase,
                        fluidBoundary.getInterphaseSystem().getPhase(phase), j);
                // System.out.println("enh " + enhancementVec[j]);
                hatta = Math
                        .sqrt(enhancementVec[j] * fluidBoundary.getBulkSystem().getPhases()[phase]
                                .getPhysicalProperties().getEffectiveDiffusionCoefficient(j))
                        / fluidBoundary.getEffectiveMassTransferCoefficient(phase, j);
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

    // public void calcEnhancementMatrix(int phase){
    // double z=1;
    // //fluidBoundary.getBulkSystem().getChemicalReactionOperations().solveKinetics(1);
    // double[][] reacMatrix =
    // fluidBoundary.getBulkSystem().getChemicalReactionOperations().getReactionList().getReacMatrix();
    // double[][] stocMatrix =
    // fluidBoundary.getBulkSystem().getChemicalReactionOperations().getReactionList().getStocMatrix();
    // fluidBoundary.getBulkSystem().getPhases()[phase].getPhysicalProperties().calcEffectiveDiffusionCoefficients();
    //
    // for(int
    // i=0;i<fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents();i++){
    // for(int
    // j=0;j<fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents();j++){
    // if(i==j || (stocMatrix[i][i]*stocMatrix[i][j])<=0)
    // enhancementFactor[phase].set(i,j, 1.0);
    // else{
    // double pseudoReacRate =
    // reacMatrix[i][j]*fluidBoundary.getBulkSystem().getPhases()[phase].getComponents()[j].getx()*fluidBoundary.getBulkSystem().getPhases()[phase].getDensity()/fluidBoundary.getBulkSystem().getPhases()[phase].getMolarMass()*Math.abs(stocMatrix[i][i]);
    // hattaNumber[phase].set(i,j,Math.sqrt(pseudoReacRate*fluidBoundary.getBulkSystem().getPhases()[phase].getPhysicalProperties().getEffectiveDiffusionCoefficient(i))/fluidBoundary.getBinaryMassTransferCoefficient(phase,i,j));
    // double phiVal = 1.0 +
    // (fluidBoundary.getBulkSystem().getPhases()[phase].getPhysicalProperties().getEffectiveDiffusionCoefficient(j)/fluidBoundary.getBulkSystem().getPhases()[phase].getPhysicalProperties().getEffectiveDiffusionCoefficient(i))*
    // (stocMatrix[i][i]/stocMatrix[i][j]) *
    // (fluidBoundary.getBulkSystem().getPhases()[phase].getComponents()[j].getx()*fluidBoundary.getBulkSystem().getPhases()[phase].getDensity()/fluidBoundary.getBulkSystem().getPhases()[phase].getMolarMass())/
    // (fluidBoundary.getInterphaseSystem().getPhases()[phase].getComponents()[i].getx()*fluidBoundary.getInterphaseSystem().getPhases()[phase].getDensity()/fluidBoundary.getInterphaseSystem().getPhases()[phase].getMolarMass());
    //
    // System.out.println("components " +
    // fluidBoundary.getBulkSystem().getPhases()[phase].getComponents()[i].getComponentName()
    // + " " +
    // fluidBoundary.getBulkSystem().getPhases()[phase].getComponents()[j].getComponentName());
    // System.out.println("hatta : " + hattaNumber[phase].get(i,j));
    // System.out.println("phi : " + phiVal);
    //
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

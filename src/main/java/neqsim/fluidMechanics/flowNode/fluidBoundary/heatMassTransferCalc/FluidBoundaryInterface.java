/*
 * FluidBoundaryInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc;

import Jama.Matrix;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author Even Solbraa
 * @version
 */
public interface FluidBoundaryInterface extends Cloneable {
    public SystemInterface getInterphaseSystem();

    public void setInterphaseSystem(SystemInterface interphaseSystem);

    public void solve();

    public void massTransSolve();

    public void write(String name, String filename, boolean newfile);

    public void display(String name);

    public void heatTransSolve();

    public void setEnhancementType(int type);

    public EnhancementFactor getEnhancementFactor();

    public double getInterphaseHeatFlux(int phase);

    public ThermodynamicOperations getBulkSystemOpertions();

    public double[] calcFluxes();

    public Matrix[] getMassTransferCoefficientMatrix();

    public double getBinaryMassTransferCoefficient(int phase, int i, int j);

    public SystemInterface getBulkSystem();

    public boolean isHeatTransferCalc();

    public void setHeatTransferCalc(boolean heatTransferCalc);

    public void setMassTransferCalc(boolean heatTransferCalc);

    public double getInterphaseMolarFlux(int component);

    public double getEffectiveMassTransferCoefficient(int phase, int i);

    public FluidBoundaryInterface clone();

    public boolean useThermodynamicCorrections(int phase);

    public void useThermodynamicCorrections(boolean thermodynamicCorrections);

    public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase);

    public boolean useFiniteFluxCorrection(int phase);

    public void useFiniteFluxCorrection(boolean finiteFluxCorrection);

    public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase);

}

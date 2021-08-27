/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * @author  Even Solbraa
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

    public Object clone();

    public boolean useThermodynamicCorrections(int phase);

    public void useThermodynamicCorrections(boolean thermodynamicCorrections);

    public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase);

    public boolean useFiniteFluxCorrection(int phase);

    public void useFiniteFluxCorrection(boolean finiteFluxCorrection);

    public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase);

}

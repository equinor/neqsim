/*
 * FluidBoundaryInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */

package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

import Jama.Matrix;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * FluidBoundaryInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FluidBoundaryInterface extends Cloneable {
  /**
   * getInterphaseSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getInterphaseSystem();

  /**
   * setInterphaseSystem.
   *
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInterphaseSystem(SystemInterface interphaseSystem);

  /**
   * solve.
   */
  public void solve();

  /**
   * massTransSolve.
   */
  public void massTransSolve();

  /**
   * write.
   *
   * @param name a {@link java.lang.String} object
   * @param filename a {@link java.lang.String} object
   * @param newfile a boolean
   */
  public void write(String name, String filename, boolean newfile);

  /**
   * display.
   *
   * @param name a {@link java.lang.String} object
   */
  public void display(String name);

  /**
   * heatTransSolve.
   */
  public void heatTransSolve();

  /**
   * setEnhancementType.
   *
   * @param type a int
   */
  public void setEnhancementType(int type);

  /**
   * getEnhancementFactor.
   *
   * @return a
   * {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactor}
   * object
   */
  public EnhancementFactor getEnhancementFactor();

  /**
   * getInterphaseHeatFlux.
   *
   * @param phase a int
   * @return a double
   */
  public double getInterphaseHeatFlux(int phase);

  /**
   * getBulkSystemOpertions.
   *
   * @return a {@link neqsim.thermodynamicoperations.ThermodynamicOperations} object
   */
  public ThermodynamicOperations getBulkSystemOpertions();

  /**
   * calcFluxes.
   *
   * @return an array of type double
   */
  public double[] calcFluxes();

  /**
   * getMassTransferCoefficientMatrix.
   *
   * @return an array of {@link Jama.Matrix} objects
   */
  public Matrix[] getMassTransferCoefficientMatrix();

  /**
   * getBinaryMassTransferCoefficient.
   *
   * @param phase a int
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryMassTransferCoefficient(int phase, int i, int j);

  /**
   * getBulkSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBulkSystem();

  /**
   * isHeatTransferCalc.
   *
   * @return a boolean
   */
  public boolean isHeatTransferCalc();

  /**
   * setHeatTransferCalc.
   *
   * @param heatTransferCalc a boolean
   */
  public void setHeatTransferCalc(boolean heatTransferCalc);

  /**
   * setMassTransferCalc.
   *
   * @param heatTransferCalc a boolean
   */
  public void setMassTransferCalc(boolean heatTransferCalc);

  /**
   * getInterphaseMolarFlux.
   *
   * @param component a int
   * @return a double
   */
  public double getInterphaseMolarFlux(int component);

  /**
   * getEffectiveMassTransferCoefficient.
   *
   * @param phase a int
   * @param i a int
   * @return a double
   */
  public double getEffectiveMassTransferCoefficient(int phase, int i);

  /**
   * clone.
   *
   * @return a {@link neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface} object
   */
  public FluidBoundaryInterface clone();

  /**
   * useThermodynamicCorrections.
   *
   * @param phase a int
   * @return a boolean
   */
  public boolean useThermodynamicCorrections(int phase);

  /**
   * useThermodynamicCorrections.
   *
   * @param thermodynamicCorrections a boolean
   */
  public void useThermodynamicCorrections(boolean thermodynamicCorrections);

  /**
   * useThermodynamicCorrections.
   *
   * @param thermodynamicCorrections a boolean
   * @param phase a int
   */
  public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase);

  /**
   * useFiniteFluxCorrection.
   *
   * @param phase a int
   * @return a boolean
   */
  public boolean useFiniteFluxCorrection(int phase);

  /**
   * useFiniteFluxCorrection.
   *
   * @param finiteFluxCorrection a boolean
   */
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection);

  /**
   * useFiniteFluxCorrection.
   *
   * @param finiteFluxCorrection a boolean
   * @param phase a int
   */
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase);
}

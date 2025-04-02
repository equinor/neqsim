package neqsim.thermo.phase;

import org.netlib.util.doubleW;

public interface PhaseFundamentalEOSInterface {

  
  /**
   * Compute molar density [mol/m3] at specified temperature and pressure.
   *
   * @param temperature Temperature in Kelvin.
   * @param pressure Pressure (in the appropriate unit for your system).
   * @return Molar density in [mol/m3].
   */
  double solveDensity(double temperature, double pressure);

  /**
   * Get the reduced residual Helmholtz free energy and its derivatives.
   * The returned 2D array has the following structure:
   * <ul>
   *   <li>ar(0,0) - Residual Helmholtz energy (dimensionless, =a/RT)</li>
   *   <li>ar(0,1) - delta*∂(ar)/∂(delta)</li>
   *   <li>ar(0,2) - delta²*∂²(ar)/∂(delta)²</li>
   *   <li>ar(0,3) - delta³*∂³(ar)/∂(delta)³</li>
   *   <li>ar(1,0) - tau*∂(ar)/∂(tau)</li>
   *   <li>ar(1,1) - tau*delta*∂²(ar)/(∂(tau)∂(delta))</li>
   *   <li>ar(2,0) - tau²*∂²(ar)/∂(tau)²</li>
   * </ul>
   *
   * @return A 2D array of {@code doubleW} objects containing the Helmholtz energy derivatives.
   */
  doubleW[][] getAlpharesMatrix();

  /**
   * Get the matrix for the ideal-gas part of the Helmholtz free energy.
   *
   * @return An array of {@code doubleW} objects representing the ideal-gas contribution.
   */
  doubleW[] getAlpha0Matrix();

  /**
   * Initialize the phase with the specified parameters.
   *
   * @param totalNumberOfMoles Total number of moles.
   * @param nComponents Number of components.
   * @param initType Initialization type flag.
   * @param pt Phase type.
   * @param beta Additional parameter (context-dependent).
   */
  void init(double totalNumberOfMoles, int nComponents, int initType, PhaseType pt, double beta);

  /**
   * Get the compressibility factor.
   *
   * @return Compressibility factor Z.
   */
  double getZ();

  /**
   * Get the molar density [mol/m3].
   *
   * @return Density in mol/m3.
   */
  double getDensity();

  /**
   * Get the pressure calculated from the Helmholtz EOS.
   *
   * @return Pressure.
   */
  double getPressure();

  /**
   * Get the derivative of pressure with respect to density.
   *
   * @return dP/dD.
   */
  double getDPdD();

  /**
   * Get the Gibbs energy of the phase.
   *
   * @return Gibbs energy.
   */
  double getGibbsEnergy();

  /**
   * Get the Joule-Thomson coefficient.
   *
   * @return Joule-Thomson coefficient.
   */
  double getJouleThomsonCoefficient();

  /**
   * Get the enthalpy of the phase.
   *
   * @return Enthalpy.
   */
  double getEnthalpy();

  /**
   * Get the entropy of the phase.
   *
   * @return Entropy.
   */
  double getEntropy();

  /**
   * Get the internal energy of the phase.
   *
   * @return Internal energy.
   */
  double getInternalEnergy();

  /**
   * Get the isobaric heat capacity.
   *
   * @return Cp.
   */
  double getCp();

  /**
   * Get the isochoric heat capacity.
   *
   * @return Cv.
   */
  double getCv();

  /**
   * Get the speed of sound in the phase.
   *
   * @return Speed of sound.
   */
  double getSoundSpeed();

  /**
   * Get the isentropic exponent (kappa).
   *
   * @return kappa.
   */
  double getKappa();

  /**
   * Compute the molar volume.
   *
   * @param pressure Pressure.
   * @param temperature Temperature.
   * @param A Parameter A.
   * @param B Parameter B.
   * @param pt Phase type.
   * @return Molar volume.
   */
  double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt);

  /**
   * Get the temperature derivative of pressure times the number of moles.
   *
   * @return dP/dT * n.
   */
  double getdPdTVn();

  /**
   * Get the derivative of pressure with respect to volume times the number of moles.
   *
   * @return dP/dV * n.
   */
  double getdPdVTn();

  /**
   * Get the derivative of pressure with respect to density.
   *
   * @return dP/drho.
   */
  double getdPdrho();

  /**
   * Get the derivative of density with respect to pressure.
   *
   * @return drho/dP.
   */
  double getdrhodP();

  /**
   * Get the derivative of density with respect to temperature.
   *
   * @return drho/dT.
   */
  double getdrhodT();

  /**
   * Get the contribution to the Helmholtz energy from the residual part.
   *
   * @return Residual Helmholtz energy contribution.
   */
  double getF();


  /**
   * Get the derivative of the Helmholtz energy with respect to density.
   *
   * @param i Index for the derivative.
   * @return Derivative of Helmholtz energy with respect to density.
   */
  double dFdN(int i);

  /**
   * Get the derivative of the Helmholtz energy with respect to temperature.
   *
   * @return Derivative of Helmholtz energy with respect to temperature.
   */
  double dFdNdT(int i);

  /**
   * Get the derivative of the Helmholtz energy with respect to volume.
   *
   * @return Derivative of Helmholtz energy with respect to volume.
   */
  double dFdNdV(int i);

  /**
   * Get the derivative of the Helmholtz energy with respect to moles.
   *
   * @param j Index for the derivative.
   * @return Derivative of Helmholtz energy with respect to moles.
   */
  double dFdNdN(int i, int j);

  /**
   * Get parameter A. This method is not supported for fundamental EOS.
   *
   * @return Parameter A.
   * @throws UnsupportedOperationException if not supported.
   */
  double getA();

  /**
   * Get parameter B. This method is not supported for fundamental EOS.
   *
   * @return Parameter B.
   * @throws UnsupportedOperationException if not supported.
   */
  double getB();

}

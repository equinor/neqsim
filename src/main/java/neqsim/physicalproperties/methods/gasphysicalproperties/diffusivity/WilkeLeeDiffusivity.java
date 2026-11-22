package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * WilkeLeeDiffusivity class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class WilkeLeeDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for WilkeLeeDiffusivity.
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public WilkeLeeDiffusivity(PhysicalProperties gasPhase) {
    super(gasPhase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // Wilke-Lee (1955) modification of Chapman-Enskog theory
    // D_AB = [3.03 - (0.98 / sqrt(M_AB))] * 1e-3 * T^1.5 / (P * sqrt(M_AB) * sigma_AB^2 * Omega_D)
    // where D_AB in cm²/s, T in K, P in atm, M_AB in g/mol, sigma_AB in Angstrom
    double A2 = 1.06036;
    double B2 = 0.15610;
    double C2 = 0.19300;
    double D2 = 0.47635;
    double E2 = 1.03587;
    double F2 = 1.52996;
    double G2 = 1.76474;
    double H2 = 3.89411;
    double T = gasPhase.getPhase().getTemperature();
    double tempVar2 = T / binaryEnergyParameter[i][j];
    double collisionIntegral = A2 / Math.pow(tempVar2, B2) + C2 / Math.exp(D2 * tempVar2) + E2 / Math.exp(F2 * tempVar2)
        + G2 / Math.exp(H2 * tempVar2);
    // Note: the (3.03 - 0.98/sqrt(M_AB)) * 1e-3 is the Wilke-Lee prefactor replacing
    // the Chapman-Enskog 0.00266 constant
    double coefficient = (3.03 - 0.98 / Math.sqrt(binaryMolecularMass[i][j])) * 1.0e-3 * Math.pow(T, 1.5)
        / (gasPhase.getPhase().getPressure() * Math.sqrt(binaryMolecularMass[i][j])
            * Math.pow(binaryMolecularDiameter[i][j], 2.0) * collisionIntegral);
    // Convert from cm²/s to m²/s
    return coefficient * 1e-4;
  }
}

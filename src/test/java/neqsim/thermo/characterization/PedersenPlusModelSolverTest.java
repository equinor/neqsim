package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link PedersenPlusModelSolver}.
 *
 * <p>
 * The expected coefficients were captured from the implementation that used {@code Jama.Matrix} directly, before it was
 * migrated to {@code LinearAlgebraOperations}. Both paths run the same JAMA LU solve, so the values must match exactly.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class PedersenPlusModelSolverTest {
  /**
   * Characterises a C10+ fraction with the Pedersen model and returns the fitted coefficients.
   *
   * @param plusMoles number of moles in the plus fraction
   * @param plusMolarMass plus-fraction molar mass in kg/mol
   * @param plusDensity plus-fraction density in g/cm3
   * @return the four Pedersen coefficients
   */
  private static double[] characterise(double plusMoles, double plusMolarMass, double plusDensity) {
    SystemInterface system = new SystemSrkEos(298.0, 10.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("methane", 51.0);
    system.addComponent("ethane", 1.0);
    system.addComponent("propane", 1.0);
    system.getCharacterization().setTBPModel("PedersenSRK");
    system.addTBPfraction("C6", 1.0, 0.090, 0.70);
    system.addTBPfraction("C7", 1.0, 0.110, 0.73);
    system.addTBPfraction("C8", 1.0, 0.120, 0.76);
    system.addTBPfraction("C9", 1.0, 0.140, 0.79);
    system.addPlusFraction("C10", plusMoles, plusMolarMass, plusDensity);
    system.getCharacterization().setPlusFractionModel("Pedersen");
    system.getCharacterization().setLumpingModel("PVTlumpingModel");
    system.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(9);
    system.getCharacterization().characterisePlusFraction();
    return system.getCharacterization().getPlusFractionModel().getCoefs();
  }

  /**
   * Verifies the coefficients for a typical C10+ fraction.
   */
  @Test
  void typicalPlusFractionCoefficientsAreUnchanged() {
    assertArrayEquals(new double[] {-3.479373356777761, -0.0855476997870545, 0.7282361992342693, 0.029020154394731944},
        characterise(11.0, 0.290, 0.82), 0.0);
  }

  /**
   * Verifies the coefficients for a heavy, dense C10+ fraction.
   */
  @Test
  void heavyPlusFractionCoefficientsAreUnchanged() {
    assertArrayEquals(
        new double[] {-4.3450040345066885, -0.024758815553877978, 0.6014349180683386, 0.08672990444665372},
        characterise(30.0, 0.500, 0.92), 0.0);
  }
}

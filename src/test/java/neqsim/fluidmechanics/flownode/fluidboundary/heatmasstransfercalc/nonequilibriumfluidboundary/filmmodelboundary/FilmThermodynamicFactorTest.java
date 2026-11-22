package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Constant-pressure fugacity differences provide an independent oracle for binary diffusion and film driving forces.
 *
 * @author NeqSim
 * @version 1.0
 */
class FilmThermodynamicFactorTest {
  /** Components of the synthetic hydrocarbon liquid. */
  private static final String[] COMPONENTS = {"n-pentane", "n-decane", "CO2"};

  /**
   * Initialize fixed homogeneous liquid compositions without shifting the phase split during the derivative.
   *
   * @param amounts component amounts in mol
   * @return initialized SRK system
   */
  private static SystemInterface fluid(double... amounts) {
    SystemInterface system = new SystemSrkEos(298.15, 50.0);
    for (int i = 0; i < amounts.length; i++) {
      system.addComponent(COMPONENTS[i], amounts[i]);
    }
    system.setMixingRule("classic");
    system.init(0);
    system.init(3);
    return system;
  }

  /**
   * Differentiate ln(x_i phi_i P) on the composition simplex at fixed pressure and temperature.
   *
   * @param amounts baseline mole fractions summing to one
   * @param row species whose chemical potential is differentiated
   * @param column independent mole fraction
   * @return dimensionless reduced factor
   */
  private static double finiteDifference(double[] amounts, int row, int column) {
    double delta = 1.0e-5;
    double[] upper = amounts.clone();
    double[] lower = amounts.clone();
    int dependent = amounts.length - 1;
    upper[column] += delta;
    lower[column] -= delta;
    upper[dependent] -= delta;
    lower[dependent] += delta;
    double upperFugacity = fluid(upper).getPhase(1).getFugacity(row);
    double lowerFugacity = fluid(lower).getPhase(1).getFugacity(row);
    return amounts[row] * Math.log(upperFugacity / lowerFugacity) / (2.0 * delta);
  }

  /** Independent EOS perturbation and the ideal-derivative limit both retain positive mutual diffusion. */
  @Test
  void binaryLiquidFickCoefficientUsesConstrainedCompositionDerivative() {
    double[] amounts = {0.35, 0.65};
    SystemInterface system = fluid(amounts);
    PhaseInterface phase = system.getPhase(1);
    phase.initPhysicalProperties();
    PhysicalProperties properties = phase.getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Siddiqi Lucas");
    phase.initPhysicalProperties();
    double kinetic = properties.getDiffusionCoefficient(0, 1);
    double gamma = finiteDifference(amounts, 0, 0);
    assertTrue(kinetic > 0.0);
    assertTrue(Math.abs(gamma - 1.0) > 1e-4);
    assertEquals(gamma, properties.getFickDiffusionCoefficient(0, 1) / kinetic, 2e-6);
    assertEquals(gamma, properties.getFickDiffusionCoefficient(1, 0) / kinetic, 2e-6);

    // A composition-independent fugacity coefficient gives the ideal thermodynamic factor exactly.
    phase.getComponent(0).setdfugdn(0, 0.0);
    phase.getComponent(0).setdfugdn(1, 0.0);
    assertEquals(kinetic, properties.getFickDiffusionCoefficient(0, 1), 0.0);
  }

  /** Verify the noncommuting product K Gamma directly against fugacity perturbations. */
  @Test
  void ternaryFilmAppliesEachReducedDerivativeOnce() {
    double[] amounts = {0.35, 0.55, 0.10};
    double[][] kinetic = {{0.001, -0.0001}, {0.0002, 0.0008}};
    Film film = new Film(fluid(amounts));
    double[][] corrected = film.corrected(kinetic);
    for (int j = 0; j < 2; j++) {
      double first = finiteDifference(amounts, 0, j);
      double second = finiteDifference(amounts, 1, j);
      for (int i = 0; i < 2; i++) {
        assertEquals(kinetic[i][0] * first + kinetic[i][1] * second, corrected[i][j], 2e-9);
      }
    }
  }

  /** Expose the production film calculation at a prescribed kinetic matrix. */
  private static final class Film extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;

    /**
     * Construct the film at the test composition.
     *
     * @param system bulk system
     */
    Film(SystemInterface system) {
      super(system);
    }

    /**
     * Apply only the thermodynamic correction to the supplied kinetic matrix.
     *
     * @param kinetic inverse resistance in m/s
     * @return corrected coefficient matrix in m/s
     */
    double[][] corrected(double[][] kinetic) {
      setMassTransferCoefficientArray(1, kinetic);
      useThermodynamicCorrections(true, 1);
      calcNonIdealCorrections(1);
      calcTotalMassTransferCoefficientArray(1);
      return totalMassTransferCoefficientMatrix[1];
    }
  }
}

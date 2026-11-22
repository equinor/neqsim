package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the seven argument addTBPfraction overload honours the critical properties and acentric factor it is
 * given.
 *
 * <p>
 * The four argument overload derives every property from correlations, which is its purpose. The seven argument
 * overload exists so a caller can supply measured or externally regressed values, so silently replacing them by a
 * correlation defeats the point of the overload.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class AddTBPfractionAcentricFactorTest {
  /** Molar mass of the test fraction in kg/mol. */
  private static final double MOLAR_MASS = 0.1;

  /** Relative liquid density of the test fraction, dimensionless. */
  private static final double DENSITY = 0.75;

  /**
   * The supplied acentric factor must survive, not be replaced by the calcm correlation.
   */
  @Test
  public void suppliedAcentricFactorIsRetained() {
    double suppliedAcentricFactor = 0.49;
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addTBPfraction("testfraction", 1.0, MOLAR_MASS, DENSITY, 650.0, 20.0, suppliedAcentricFactor);
    fluid.setMixingRule("classic");

    assertEquals(suppliedAcentricFactor, fluid.getPhase(0).getComponent("testfraction_PC").getAcentricFactor(), 1.0e-9,
        "addTBPfraction discarded the acentric factor it was given");
  }

  /**
   * The supplied critical temperature and pressure must survive as well.
   */
  @Test
  public void suppliedCriticalPropertiesAreRetained() {
    double criticalTemperature = 650.0;
    double criticalPressure = 20.0;
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addTBPfraction("testfraction", 1.0, MOLAR_MASS, DENSITY, criticalTemperature, criticalPressure, 0.49);
    fluid.setMixingRule("classic");

    assertEquals(criticalTemperature, fluid.getPhase(0).getComponent("testfraction_PC").getTC(), 1.0e-9,
        "addTBPfraction discarded the critical temperature it was given");
    assertEquals(criticalPressure, fluid.getPhase(0).getComponent("testfraction_PC").getPC(), 1.0e-9,
        "addTBPfraction discarded the critical pressure it was given");
  }

  /**
   * A second, different acentric factor must give a different stored value, which rules out the assertion passing by
   * coincidence.
   */
  @Test
  public void differentAcentricFactorsGiveDifferentResults() {
    SystemInterface low = new SystemSrkEos(298.15, 10.0);
    low.addTBPfraction("testfraction", 1.0, MOLAR_MASS, DENSITY, 650.0, 20.0, 0.35);
    low.setMixingRule("classic");

    SystemInterface high = new SystemSrkEos(298.15, 10.0);
    high.addTBPfraction("testfraction", 1.0, MOLAR_MASS, DENSITY, 650.0, 20.0, 0.65);
    high.setMixingRule("classic");

    assertEquals(0.35, low.getPhase(0).getComponent("testfraction_PC").getAcentricFactor(), 1.0e-9);
    assertEquals(0.65, high.getPhase(0).getComponent("testfraction_PC").getAcentricFactor(), 1.0e-9);
  }
}

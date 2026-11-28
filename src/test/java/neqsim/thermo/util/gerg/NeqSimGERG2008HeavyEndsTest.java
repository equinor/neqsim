package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies the heavy-end handling described in docs/thermo/gerg2008_eoscg.md: components outside the GERG-2008 list are
 * mapped by molar mass, and a molar-mass-preserving lump into n-nonane and n-decane removes the molar-mass error of the
 * default mapping.
 *
 * @author ESOL
 * @version 1.0
 */
public class NeqSimGERG2008HeavyEndsTest {
  private static final double NONANE_M = 0.12825;
  private static final double DECANE_M = 0.14228;

  /**
   * Builds a lean gas with a heavy fraction and returns the flashed single gas phase wrapper.
   *
   * @param lumped true to replace the plus fraction by n-nonane and n-decane
   * @param plusFractionMolarMass molar mass of the plus fraction in kg/mol
   * @return the fluid after a TP flash
   */
  private static SystemInterface buildGas(boolean lumped, double plusFractionMolarMass) {
    double heavy = 0.002;
    SystemInterface fluid = new SystemSrkEos(288.15, 5.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nitrogen", 0.008);
    if (lumped) {
      double xDecane = (plusFractionMolarMass - NONANE_M) / (DECANE_M - NONANE_M);
      fluid.addComponent("n-nonane", heavy * (1.0 - xDecane));
      fluid.addComponent("nC10", heavy * xDecane);
    } else {
      fluid.addTBPfraction("C9p", heavy, plusFractionMolarMass, 0.77);
    }
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    return fluid;
  }

  /** The default mapping puts a 130 g/mol fraction in the n-decane slot and misses molar mass. */
  @Test
  void rawPlusFractionIsMappedToDecane() {
    SystemInterface raw = buildGas(false, 0.130);
    NeqSimGERG2008 gerg = new NeqSimGERG2008(raw.getPhase(0));
    double error = Math.abs(gerg.getMolarMass() - raw.getPhase(0).getMolarMass()) / raw.getPhase(0).getMolarMass();
    assertTrue(error > 1.0e-4, "mapped molar mass should deviate, error=" + error);
  }

  /** Lumping with a molar-mass balance restores the GERG molar mass. */
  @Test
  void molarMassPreservingLumpMatchesPhaseMolarMass() {
    SystemInterface lumped = buildGas(true, 0.130);
    NeqSimGERG2008 gerg = new NeqSimGERG2008(lumped.getPhase(0));
    assertEquals(lumped.getPhase(0).getMolarMass(), gerg.getMolarMass(), 1.0e-4 * lumped.getPhase(0).getMolarMass());
    assertTrue(lumped.getPhase(0).getDensity_GERG2008() > 0.0);
  }
}

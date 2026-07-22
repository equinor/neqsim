package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link SystemThermo#getMolarMass()} — the molar mass is an intensive property and must be
 * invariant to any total-moles rescaling such as {@link SystemThermo#setTotalNumberOfMoles(double)} (NIP-01).
 *
 * @author NeqSim
 * @version 1.0
 */
public class SystemThermoMolarMassTest {

  private static SystemInterface buildGas() {
    SystemInterface gas = new SystemSrkEos(303.15, 110.0);
    gas.addComponent("nitrogen", 0.8);
    gas.addComponent("CO2", 2.0);
    gas.addComponent("methane", 82.0);
    gas.addComponent("ethane", 8.0);
    gas.addComponent("propane", 4.0);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();
    return gas;
  }

  /**
   * Molar mass of a representative reinjection gas should be around 19-20 g/mol.
   */
  @Test
  public void testMolarMassPhysicalRange() {
    SystemInterface gas = buildGas();
    double mwKgMol = gas.getMolarMass();
    assertTrue(mwKgMol > 0.017 && mwKgMol < 0.023, "molar mass should be ~0.019 kg/mol, was " + mwKgMol);
    assertEquals(mwKgMol * 1000.0, gas.getMolarMass("gr/mol"), 1e-9, "gr/mol overload must equal kg/mol * 1000");
  }

  /**
   * The molar mass must not change after {@link SystemThermo#setTotalNumberOfMoles(double)} (NIP-01 regression —
   * previously returned ~100x too high).
   */
  @Test
  public void testMolarMassInvariantToSetTotalNumberOfMoles() {
    SystemInterface gas = buildGas();
    double before = gas.getMolarMass();
    gas.setTotalNumberOfMoles(1.0);
    double after = gas.getMolarMass();
    assertEquals(before, after, 1e-9, "getMolarMass must be invariant to setTotalNumberOfMoles");
    assertTrue(after > 0.017 && after < 0.023,
        "molar mass should stay ~0.019 kg/mol after setTotalNumberOfMoles, was " + after);
  }
}

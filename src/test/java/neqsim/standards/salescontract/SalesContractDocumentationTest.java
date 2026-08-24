package neqsim.standards.salescontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import org.junit.jupiter.api.Test;

/** Executes the database-backed example in the gas sales-contract guide. */
class SalesContractDocumentationTest extends NeqSimTest {
  @Test
  void testBrazilContractExampleAndResultTableContract() {
    SystemInterface gas = new SystemGERGwaterEos(268.15, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.02);
    gas.addComponent("n-heptane", 0.00012);
    gas.addComponent("H2S", 0.000012);
    gas.addComponent("water", 0.000071);
    gas.addComponent("oxygen", 0.0012);
    gas.addComponent("CO2", 0.022);
    gas.addComponent("nitrogen", 0.022);
    gas.setMixingRule(8);
    gas.init(0);

    BaseContract contract = new BaseContract(gas, "central", "Brazil");
    contract.runCheck();

    String[][] rows = contract.getResultTable();
    int specificationCount = contract.getSpecificationsNumber();
    int rowsWithinLimits = 0;
    for (int rowIndex = 0; rowIndex < specificationCount; rowIndex++) {
      double value = Double.parseDouble(rows[rowIndex][1]);
      double minimum = Double.parseDouble(rows[rowIndex][4]);
      double maximum = Double.parseDouble(rows[rowIndex][5]);
      if (Double.isFinite(value) && value >= minimum && value <= maximum) {
        rowsWithinLimits++;
      }
    }

    assertEquals(8, specificationCount);
    assertEquals(8, rows.length);
    assertEquals(12, rows[0].length);
    assertEquals("CO2", rows[1][0]);
    assertEquals(2.18817727816606, Double.parseDouble(rows[1][1]), 1.0e-6);
    assertEquals("Brazil", rows[1][2]);
    assertEquals("central", rows[1][3]);
    assertEquals(0.0, Double.parseDouble(rows[1][4]), 0.0);
    assertEquals(3.0, Double.parseDouble(rows[1][5]), 0.0);
    assertEquals("mol%", rows[1][6]);
    assertEquals(1.0, Double.parseDouble(rows[1][10]), 0.0);
    assertEquals("", rows[1][11]);
    assertTrue(rowsWithinLimits > 0);
  }
}

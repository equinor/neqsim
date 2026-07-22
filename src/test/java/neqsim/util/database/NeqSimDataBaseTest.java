package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class NeqSimDataBaseTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(NeqSimDataBaseTest.class);

  @Test
  void testHasComponent() {
    assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("methane"), "Could not load component methane");
  }

  /**
   * Verifies that the extended component database (COMP_EXT.csv) supplies a component that is not present in the
   * default component database (COMP.csv), and that a Peng-Robinson flash on that component produces physically
   * sensible results. The component 1-decene (C10H20) is a clean alpha-olefin that only exists in the extended
   * database.
   */
  @Test
  void testComponentOnlyInExtendedDatabase() {
    try {
      // 1-decene is not part of the default component database.
      assertFalse(neqsim.util.database.NeqSimDataBase.hasComponent("1-decene"),
          "1-decene should NOT be present in the default component database (COMP.csv)");

      // Switch to the extended component database.
      neqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(true);
      assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("1-decene"),
          "1-decene should be present in the extended component database (COMP_EXT.csv)");

      // Build a pure 1-decene fluid with the standard Peng-Robinson EOS and flash it at 25 C, 1
      // atm.
      SystemInterface fluid = new SystemPrEos(298.15, 1.01325);
      fluid.addComponent("1-decene", 1.0);
      fluid.setMixingRule("classic");

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      // Molar mass is read straight from the database row -> proves the component loaded correctly.
      Assertions.assertEquals(140.27, fluid.getMolarMass("g/mol"), 0.5,
          "Molar mass of 1-decene from COMP_EXT should be ~140.27 g/mol");

      // At 25 C and 1 atm (well below the 171 C normal boiling point) 1-decene is a single liquid.
      Assertions.assertEquals(1, fluid.getNumberOfPhases(),
          "Pure 1-decene at 25 C / 1 atm should be a single liquid phase");

      // Liquid density should be in a realistic range for a C10 olefin (PR EOS, no volume shift).
      double density = fluid.getDensity("kg/m3");
      assertTrue(density > 500.0 && density < 900.0,
          "Liquid density of 1-decene should be 500-900 kg/m3 but was " + density);
    } finally {
      // Always restore the default database so other tests are unaffected.
      neqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  /**
   * Verifies that a vapour-liquid equilibrium flash works for a mixture containing a component that only exists in the
   * extended database. A methane / 1-decene mixture is flashed with the Peng-Robinson EOS and the resulting phase split
   * is checked for correct partitioning.
   */
  @Test
  void testFlashWithExtendedDatabaseComponent() {
    try {
      neqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(true);
      assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("1-decene"),
          "1-decene should be present in the extended component database (COMP_EXT.csv)");

      // Light + heavy mixture: methane (in COMP) and 1-decene (only in COMP_EXT).
      SystemInterface fluid = new SystemPrEos(298.15, 60.0);
      fluid.addComponent("methane", 1.0);
      fluid.addComponent("1-decene", 1.0);
      fluid.setMixingRule("classic");

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      // Expect a two-phase split: a methane-rich gas and a 1-decene-rich liquid.
      Assertions.assertEquals(2, fluid.getNumberOfPhases(),
          "methane / 1-decene at 25 C / 60 bara should split into gas and liquid");

      // Phase 0 is the lightest (gas), the last phase is the heaviest (liquid).
      int heavyIdx = fluid.getNumberOfPhases() - 1;
      double xMethaneGas = fluid.getPhase(0).getComponent("methane").getx();
      double xMethaneLiquid = fluid.getPhase(heavyIdx).getComponent("methane").getx();
      double xDeceneGas = fluid.getPhase(0).getComponent("1-decene").getx();
      double xDeceneLiquid = fluid.getPhase(heavyIdx).getComponent("1-decene").getx();

      assertTrue(xMethaneGas > xMethaneLiquid, "Gas phase should be richer in methane than the liquid phase");
      assertTrue(xDeceneLiquid > xDeceneGas, "Liquid phase should be richer in 1-decene than the gas phase");
    } finally {
      neqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  @Test
  void testReplaceTable() {
    neqsim.util.database.NeqSimDataBase.replaceTable("COMP", "src/main/resources/data/COMP.csv");
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
        () -> neqsim.util.database.NeqSimDataBase.replaceTable("COMP", "file_does_not_exist.csv"));
    assertTrue(
        thrown.getMessage()
            .startsWith("neqsim.util.exception.InvalidInputException: NeqSimDataBase:replaceTable "
                + "- Input path - failed to load table COMP from file_does_not_exist.csv"),
        "Unexpected exception message: " + thrown.getMessage());
    Assertions.assertNotNull(thrown.getCause().getCause(),
        "The original database/IO exception should be preserved as the cause");
    // The COMP table must still be usable after a failed replaceTable call (falls back to the
    // bundled default), not left missing.
    assertTrue(neqsim.util.database.NeqSimDataBase.hasComponent("methane"),
        "COMP table should have been restored to a usable state after the failed replaceTable call");
  }

  @Test
  void testUpdateTable() {
    neqsim.util.database.NeqSimDataBase.updateTable("COMP");
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
        () -> neqsim.util.database.NeqSimDataBase.updateTable("COMP", "file_does_not_exist.csv"));
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: NeqSimDataBase:updateTable - Input path - Resource file_does_not_exist.csv not found",
        thrown.getMessage());
  }

  @Test
  void testMain() {
    boolean failed = true;
    // NeqSimDataBase.initH2DatabaseFromCSVfiles();
    double molmass = 0.0;
    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM comp WHERE NAME='methane'")) {
      dataSet.next();
      molmass = Double.valueOf(dataSet.getString("molarmass"));
      dataSet.close();
      failed = false;
    } catch (Exception ex) {
      logger.info(ex.getMessage());
    }
    // Assertions.assertTrue(testHasMethane, "Methane component found in database");
    Assertions.assertEquals(16.04, molmass, 0.1);
    Assertions.assertFalse(failed, "Failed getting data from NeqsimDataBase");
  }

  @Test
  void testGetResultSetAfterClose() throws Exception {
    NeqSimDataBase database = new NeqSimDataBase();
    database.close();
    try (ResultSet rs = database.getResultSet("SELECT 1")) {
      assertTrue(rs.next(), "Expected result from reopened connection");
    }
  }
}

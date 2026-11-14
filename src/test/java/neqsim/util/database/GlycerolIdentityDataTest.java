package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Glycerol must carry its own identity and pure-component data, not a copy of the TEG row (issue #4069).
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GlycerolIdentityDataTest {
  /**
   * Both database modes must supply the sourced glycerol identity and consistent mass/element bookkeeping.
   *
   * @throws Exception if the bundled database cannot be queried
   */
  @Test
  void identityAndMassConversionAreConsistent() throws Exception {
    try {
      for (boolean extended : new boolean[] {false, true}) {
        NeqSimDataBase.useExtendedComponentDatabase(extended);
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet row = database.getResultSet("SELECT * FROM comp WHERE name='glycerol'")) {
          assertTrue(row.next());
          assertEquals("56-81-5", row.getString("CASnumber"));
          assertEquals("C3H8O3", row.getString("FORMULA"));
          assertEquals("PEDCQBHIVMGVHV-UHFFFAOYSA-N", row.getString("InChIKey"));
          assertEquals(92.0938, row.getDouble("MOLARMASS"), 1.0e-8);
          assertEquals(92.0938, row.getDouble("MWAVG"), 1.0e-8);
          assertFalse(row.next(), "glycerol must resolve to exactly one row");
        }
        try (NeqSimDataBase database = new NeqSimDataBase();
            ResultSet row = database.getResultSet("SELECT CASnumber FROM comp WHERE name='TEG'")) {
          assertTrue(row.next());
          assertNotEquals("56-81-5", row.getString("CASnumber"));
        }
        SystemSrkEos fluid = new SystemSrkEos(298.15, 1.01325);
        fluid.addComponent("glycerol", 2.0);
        fluid.init(0);
        assertEquals(0.0920938, fluid.getComponent("glycerol").getMolarMass(), 1.0e-10);
        assertEquals(0.1841876, fluid.getMass("kg"), 1.0e-10);
        assertEquals(850.0, fluid.getComponent("glycerol").getTC(), 1.0e-9);
        assertEquals(75.0, fluid.getComponent("glycerol").getPC(), 1.0e-9);
        // C3H8O3 element balance against the stored molar mass (IUPAC conventional atomic weights).
        double formulaMass = 3 * 12.011 + 8 * 1.008 + 3 * 15.999;
        assertEquals(formulaMass, 1000.0 * fluid.getComponent("glycerol").getMolarMass(), 0.01);
      }
    } finally {
      NeqSimDataBase.useExtendedComponentDatabase(false);
    }
  }

  /**
   * The re-expressed CPA parameters reproduce measured glycerol liquid density and vapour pressure.
   *
   * <p>
   * Density reference: 1261.3 kg/m3 at 20 C. Vapour pressure reference: Stull (1947), 10 mmHg at 167.2 C and 760 mmHg
   * at 290.0 C.
   * </p>
   *
   * @throws Exception if a flash fails
   */
  @Test
  void cpaReproducesPureGlycerolDensityAndVapourPressure() throws Exception {
    SystemInterface liquid = new SystemSrkCPAstatoil(293.15, 1.01325);
    liquid.addComponent("glycerol", 1.0);
    liquid.setMixingRule(10);
    new ThermodynamicOperations(liquid).TPflash();
    liquid.initProperties();
    assertEquals(1261.3, liquid.getDensity("kg/m3"), 0.005 * 1261.3);

    double[] temperaturesC = {167.2, 290.0};
    double[] pressuresKPa = {10.0 * 0.133322368, 101.325};
    for (int i = 0; i < temperaturesC.length; i++) {
      SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + temperaturesC[i], 0.01);
      fluid.addComponent("glycerol", 1.0);
      fluid.setMixingRule(10);
      new ThermodynamicOperations(fluid).bubblePointPressureFlash(false);
      assertEquals(pressuresKPa[i], 100.0 * fluid.getPressure(), 0.05 * pressuresKPa[i]);
    }
  }
}

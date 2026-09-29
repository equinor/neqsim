package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;
import org.junit.jupiter.api.Test;

/**
 * Guards repaired ideal-gas heat capacities in both component database modes.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class WaterCpPlaceholderRegressionTest extends neqsim.NeqSimTest {
  /**
   * Verify independent gas Cp values for the direct NIST fit and representative Joback groups.
   */
  @Test
  public void repairedHydrocarbonCpIsNotWaterCp() {
    synchronized (NeqSimDataBase.class) {
      try {
        NeqSimDataBase.useExtendedComponentDatabase(false);
        double water298 = cp0("water", 298.15);
        assertEquals(33.519817, water298, 1e-5);
        checkCase("1,2,4-trimethylbenzene", 154.35131, 237.67382, water298);
        checkCase("(e)-2-methyl-3-hexene", 151.5, 231.9, water298);
        checkCase("1,1,4-trimethylcyclohexane", 175.6, 299.1, water298);
        checkCase("2,2,4-trimethylheptane", 234.26537, 359.043, water298);
        checkCase("biphenyl", 161.52454, 267.145, water298);
      } finally {
        NeqSimDataBase.useExtendedComponentDatabase(false);
      }
    }
  }

  /**
   * Verify that the extended table and the imported standard-only component agree.
   *
   * @throws Exception if database access fails
   */
  @Test
  public void extendedModeUsesTheSameRepairedCp() throws Exception {
    synchronized (NeqSimDataBase.class) {
      try {
        String[] names = {"1,2,4-trimethylbenzene", "1,1,4-trimethylcyclohexane", "1-butyl-2-methylbenzene"};
        double[] standard = new double[names.length];
        NeqSimDataBase.useExtendedComponentDatabase(false);
        for (int i = 0; i < names.length; i++) {
          standard[i] = cp0(names[i], 298.15);
        }
        String[] parameterNames = {"ethylene", "propylbenzene", "methane", "methanol", "hydrogen", "MDEA"};
        String[][] standardParameters = new String[parameterNames.length][];
        for (int i = 0; i < parameterNames.length; i++) {
          standardParameters[i] = pureComponentParameters(parameterNames[i]);
        }
        NeqSimDataBase.useExtendedComponentDatabase(true);
        for (int i = 0; i < names.length; i++) {
          assertEquals(standard[i], cp0(names[i], 298.15), 1e-8, names[i]);
        }
        for (int i = 0; i < parameterNames.length; i++) {
          String[] actual = pureComponentParameters(parameterNames[i]);
          for (int j = 0; j < actual.length; j++) {
            assertEquals(standardParameters[i][j], actual[j], parameterNames[i] + " field " + j);
          }
        }
      } finally {
        NeqSimDataBase.useExtendedComponentDatabase(false);
      }
    }
  }

  /**
   * Assert both temperature points and their distance from the water sentinel.
   *
   * @param name database component name
   * @param expected298 expected ideal-gas Cp in J/(mol K) at 298.15 K
   * @param expected500 expected ideal-gas Cp in J/(mol K) at 500 K
   * @param water298 water ideal-gas Cp at 298.15 K
   */
  private static void checkCase(String name, double expected298, double expected500, double water298) {
    double actual298 = cp0(name, 298.15);
    assertEquals(expected298, actual298, 0.8, name);
    assertEquals(expected500, cp0(name, 500.0), 1.0, name);
    assertNotEquals(water298, actual298, 1.0, name);
  }

  /**
   * Read the ideal-gas heat capacity through the table-driven component path.
   *
   * @param name database component name
   * @param temperature temperature in K
   * @return ideal-gas Cp in J/(mol K)
   */
  private static double cp0(String name, double temperature) {
    SystemSrkEos system = new SystemSrkEos(temperature, 1.0);
    system.addComponent(name, 1.0);
    system.createDatabase(true);
    return system.getPhase(0).getComponent(0).getCp0(temperature);
  }

  /**
   * Read shared pure-component fields spanning identity, EOS, caloric, and property models.
   *
   * @param name database component name
   * @return ordered parameter strings from the active COMP table
   * @throws Exception if database access fails
   */
  private static String[] pureComponentParameters(String name) throws Exception {
    String sql = "SELECT COMPTYPE,PC,ACSFACT,MOLARMASS,HenryCoef1,mSAFTVRMie,"
        + "DIELECTRICPARAMETER1,TRIPLEPOINTTEMPERATURE,FORMULA,CASnumber FROM COMP WHERE NAME=?";
    try (NeqSimDataBase database = new NeqSimDataBase();
        PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
      statement.setString(1, name);
      try (ResultSet row = statement.executeQuery()) {
        org.junit.jupiter.api.Assertions.assertTrue(row.next(), name);
        String[] values = new String[10];
        for (int i = 0; i < values.length; i++) {
          values[i] = row.getString(i + 1);
        }
        return values;
      }
    }
  }
}

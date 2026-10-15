package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Ensures that extended database loading keeps canonical neutral dielectric inputs and derivatives.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
@Tag("slow")
class CanonicalDielectricDatabaseTest extends neqsim.NeqSimTest {
  /**
   * Checks the actual extended loader against independent dielectric reference points and standard mode.
   *
   * @throws Exception if the component database cannot be queried
   */
  @Test
  void extendedLoaderPreservesCanonicalDielectricInputsAndDerivatives() throws Exception {
    synchronized (NeqSimDataBase.class) {
      boolean temporaryTables = NeqSimDataBase.createTemporaryTables();
      try {
        NeqSimDataBase.setCreateTemporaryTables(false);
        NeqSimDataBase.useExtendedComponentDatabase(false);
        assertFalse(NeqSimDataBase.hasComponent("1-decene"));
        String[][] coefficients = dielectricParameters();
        double[][] standard = dielectricReferenceValues();
        NeqSimDataBase.useExtendedComponentDatabase(true);
        assertTrue(NeqSimDataBase.hasComponent("1-decene"), "The extended table must actually be loaded");
        String[][] extendedCoefficients = dielectricParameters();
        for (int component = 0; component < coefficients.length; component++) {
          for (int parameter = 0; parameter < coefficients[component].length; parameter++) {
            assertEquals(coefficients[component][parameter], extendedCoefficients[component][parameter],
                "Canonical neutral dielectric coefficient " + component + ":" + parameter);
          }
        }
        double[][] extended = dielectricReferenceValues();
        for (int point = 0; point < standard.length; point++) {
          for (int derivative = 0; derivative < standard[point].length; derivative++) {
            assertEquals(standard[point][derivative], extended[point][derivative], 0.0,
                "Database mode changed dielectric reference point " + point + ", derivative " + derivative);
          }
        }
      } finally {
        try {
          NeqSimDataBase.useExtendedComponentDatabase(false);
        } finally {
          NeqSimDataBase.setCreateTemporaryTables(temporaryTables);
        }
      }
    }
  }

  /**
   * Read all five canonical dielectric coefficients from the active database.
   *
   * @return coefficient strings for nitrogen, n-butane and n-hexane
   * @throws Exception if the component database cannot be queried
   */
  private static String[][] dielectricParameters() throws Exception {
    String[] names = {"nitrogen", "n-butane", "n-hexane"};
    String[][] parameters = new String[names.length][5];
    try (NeqSimDataBase database = new NeqSimDataBase();
        PreparedStatement statement = database.getConnection().prepareStatement("SELECT * FROM COMP WHERE NAME=?")) {
      for (int component = 0; component < names.length; component++) {
        statement.setString(1, names[component]);
        try (ResultSet result = statement.executeQuery()) {
          assertTrue(result.next(), names[component]);
          for (int parameter = 0; parameter < 5; parameter++) {
            parameters[component][parameter] = result.getString("DIELECTRICPARAMETER" + (parameter + 1));
          }
        }
      }
    }
    return parameters;
  }

  /**
   * Check Solbraa nitrogen, Haynes butane holdouts and independent Mopsik hexane reference values.
   *
   * @return values and first/second temperature derivatives at each reference point
   */
  private static double[][] dielectricReferenceValues() {
    String[] names = {"nitrogen", "nitrogen", "nitrogen", "n-butane", "n-butane", "n-butane", "n-butane", "n-butane",
        "n-butane", "n-butane", "n-hexane", "n-hexane"};
    double[] temperatures = {273.15, 298.15, 373.15, 140.0, 170.0, 200.0, 235.0, 255.0, 285.0, 300.0, 273.15, 298.15};
    double[] expected = {2.0, 2.0, 2.0, 2.03139, 1.98187, 1.93297, 1.87511, 1.84117, 1.78823, 1.76031, 1.9178, 1.8799};
    double[][] values = new double[names.length][3];
    for (int point = 0; point < names.length; point++) {
      double temperature = temperatures[point];
      SystemSrkEos fluid = new SystemSrkEos(temperature, 1.0);
      fluid.addComponent(names[point], 1.0);
      ComponentInterface component = fluid.getPhase(0).getComponent(0);
      values[point][0] = component.getDielectricConstant(temperature);
      values[point][1] = component.getDielectricConstantdT(temperature);
      values[point][2] = component.getDielectricConstantdTdT(temperature);
      double tolerance = point < 3 ? 0.0 : point < 10 ? 0.0002 : 0.005;
      assertEquals(expected[point], values[point][0], tolerance, names[point] + " at " + temperature + " K");
      assertEquals(values[point][1],
          (component.getDielectricConstant(temperature + 0.01) - component.getDielectricConstant(temperature - 0.01))
              / 0.02,
          1.0e-10);
      assertEquals(values[point][2], (component.getDielectricConstantdT(temperature + 0.01)
          - component.getDielectricConstantdT(temperature - 0.01)) / 0.02, 1.0e-12);
    }
    return values;
  }
}

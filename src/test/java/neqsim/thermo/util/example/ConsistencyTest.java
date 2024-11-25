package neqsim.thermo.util.example;

import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ConsistencyTest class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class ConsistencyTest {
  /**
   * A easy implementation to test a thermodynamic model
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(310.0, 10);
    testSystem.addComponent("methane", 10.0);
    testSystem.addComponent("CO2", 10.0);

    testSystem.setMixingRule(4);
    testSystem.init(0);
    testSystem.init(1);
    ThermodynamicModelTest testModel = new ThermodynamicModelTest(testSystem);
    testModel.runTest();
  }
}

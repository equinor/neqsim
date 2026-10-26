package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks the gradient solver's logarithmic Jacobian independently of its Newton updates.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPgradientFlashJacobianTest {
  /**
   * Compares pressure and sum-preserving composition directions with centered differences.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @throws ReflectiveOperationException if the solver's diagnostic arrays cannot be read
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void logarithmicJacobianMatchesResidualDifferences(boolean pr) throws ReflectiveOperationException {
    SystemInterface inlet = reservoirGas(pr);
    TPgradientFlash operation = new TPgradientFlash(inlet, 3000.0, 390.0);
    operation.run();
    SystemInterface result = operation.getThermoSystem();
    result.init(3);
    operation.setJac();
    double[][] jacobian = (double[][]) field(operation, "Jac");
    double pressure = result.getPressure();
    double pressureStep = 1.0e-3;
    result.setPressure(pressure + pressureStep);
    double[] plus = residual(operation);
    result.setPressure(pressure - pressureStep);
    double[] minus = residual(operation);
    result.setPressure(pressure);
    int last = result.getNumberOfComponents() - 1;
    for (int i = 0; i <= last; i++) {
      assertEquals((plus[i] - minus[i]) / (2.0 * pressureStep), jacobian[i][last + 1], 1.0e-10,
          "pressure derivative for component " + i);
    }

    double compositionStep = 1.0e-6;
    double lastComposition = result.getPhase(0).getComponent(last).getx();
    for (int j = 0; j < last; j++) {
      double composition = result.getPhase(0).getComponent(j).getx();
      result.getPhase(0).getComponent(j).setx(composition + compositionStep);
      result.getPhase(0).getComponent(last).setx(lastComposition - compositionStep);
      plus = residual(operation);
      result.getPhase(0).getComponent(j).setx(composition - compositionStep);
      result.getPhase(0).getComponent(last).setx(lastComposition + compositionStep);
      minus = residual(operation);
      result.getPhase(0).getComponent(j).setx(composition);
      result.getPhase(0).getComponent(last).setx(lastComposition);
      for (int i = 0; i <= last; i++) {
        assertEquals((plus[i] - minus[i]) / (2.0 * compositionStep), jacobian[i][j] - jacobian[i][last], 3.0e-7,
            "composition direction " + j + " for component " + i);
      }
    }
  }

  /**
   * An exhausted Newton solve must fail explicitly without modifying the supplied fluid.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void exhaustedGradientRejectsUnconvergedState(boolean pr) {
    SystemInterface inlet = reservoirGas(pr);
    final int[] updates = {0};
    TPgradientFlash operation = new TPgradientFlash(inlet, 3000.0, 390.0) {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public void setNewX() {
        updates[0]++;
      }
    };
    IllegalStateException failure = assertThrows(IllegalStateException.class, operation::run);
    assertTrue(failure.getMessage().contains("did not converge"));
    assertEquals(50, updates[0]);
    assertEquals(360.0, inlet.getTemperature(), 0.0);
    assertEquals(250.0, inlet.getPressure(), 0.0);
    assertEquals(100.0, inlet.getTotalNumberOfMoles(), 0.0);
  }

  /**
   * Constructs the public five-component gradient reference mixture.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @return initialized reservoir gas
   */
  static SystemInterface reservoirGas(boolean pr) {
    SystemInterface inlet = pr ? new SystemPrEos(360.0, 250.0) : new SystemSrkEos(360.0, 250.0);
    inlet.addComponent("nitrogen", 1.0);
    inlet.addComponent("CO2", 2.0);
    inlet.addComponent("methane", 85.0);
    inlet.addComponent("ethane", 7.0);
    inlet.addComponent("propane", 5.0);
    inlet.createDatabase(true);
    inlet.setMixingRule(2);
    return inlet;
  }

  /**
   * Evaluates a fresh residual without applying a Newton update.
   *
   * @param operation operation whose current local state is evaluated
   * @return independent copy of the logarithmic residual
   * @throws ReflectiveOperationException if the residual array cannot be read
   */
  private double[] residual(TPgradientFlash operation) throws ReflectiveOperationException {
    operation.getThermoSystem().init(3);
    operation.setfvec();
    return ((double[]) field(operation, "fvec")).clone();
  }

  /**
   * Reads a private diagnostic without extending the public solver API.
   *
   * @param operation gradient solver
   * @param name diagnostic field name
   * @return diagnostic value
   * @throws ReflectiveOperationException if the diagnostic cannot be accessed
   */
  private Object field(TPgradientFlash operation, String name) throws ReflectiveOperationException {
    Field field = TPgradientFlash.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(operation);
  }
}

package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessDerivativeCalculator.
 */
public class ProcessDerivativeCalculatorTest {

  private ProcessSystem process;
  private Stream feed;
  private Separator separator;
  private ProcessDerivativeCalculator calculator;

  @BeforeEach
  public void setUp() {
    // Create a simple process
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    process = new ProcessSystem();

    feed = new Stream("Feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    separator = new Separator("Separator", feed);

    process.add(feed);
    process.add(separator);
    process.run();

    // Create calculator
    calculator = new ProcessDerivativeCalculator(process);
  }

  @Test
  public void testAddVariables() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addInputVariable("Feed.pressure", "bara");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    assertEquals(2, calculator.getInputVariableNames().size());
    assertEquals(1, calculator.getOutputVariableNames().size());
  }

  @Test
  public void testCalculateJacobian() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    double[][] jacobian = calculator.calculateJacobian();

    assertNotNull(jacobian);
    assertEquals(1, jacobian.length); // 1 output
    assertEquals(1, jacobian[0].length); // 1 input

    // Gas flow should increase with feed flow (positive derivative)
    assertTrue(jacobian[0][0] > 0, "Gas flow should increase with feed flow");
  }

  @Test
  public void testGetDerivative() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    double derivative =
        calculator.getDerivative("Separator.gasOutStream.flowRate", "Feed.flowRate");

    // Derivative should be positive (more feed = more gas)
    // and reasonably bounded (not infinite or extremely large)
    assertTrue(derivative > 0, "Derivative should be positive");
    assertTrue(derivative < 10.0, "Derivative should be reasonably bounded");
  }

  @Test
  public void testGetGradient() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addInputVariable("Feed.pressure", "bara");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    double[] gradient = calculator.getGradient("Separator.gasOutStream.flowRate");

    assertEquals(2, gradient.length);
    // Flow derivative should be positive
    assertTrue(gradient[0] > 0, "Derivative w.r.t. flow should be positive");
  }

  @Test
  public void testCentralDifferenceAccuracy() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    // Compare forward and central difference
    calculator.setMethod(ProcessDerivativeCalculator.DerivativeMethod.FORWARD_DIFFERENCE);
    double[][] forwardJacobian = calculator.calculateJacobian();

    calculator.setMethod(ProcessDerivativeCalculator.DerivativeMethod.CENTRAL_DIFFERENCE);
    double[][] centralJacobian = calculator.calculateJacobian();

    // Both should give similar results
    assertEquals(forwardJacobian[0][0], centralJacobian[0][0], 0.01);
  }

  @Test
  public void testMultipleInputsOutputs() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addInputVariable("Feed.pressure", "bara");
    calculator.addInputVariable("Feed.temperature", "C");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.pressure", "bara");

    double[][] jacobian = calculator.calculateJacobian();

    assertEquals(2, jacobian.length); // 2 outputs
    assertEquals(3, jacobian[0].length); // 3 inputs
    assertEquals(3, jacobian[1].length); // 3 inputs
  }

  @Test
  public void testBaseValueCaching() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    double[] baseInputs = calculator.getBaseInputValues();
    double[] baseOutputs = calculator.getBaseOutputValues();

    assertEquals(1, baseInputs.length);
    assertEquals(1, baseOutputs.length);
    assertEquals(100.0, baseInputs[0], 1.0); // Feed flow ~ 100 kg/hr
  }

  @Test
  public void testExportToJSON() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    String json = calculator.exportJacobianToJSON();

    assertNotNull(json);
    assertTrue(json.contains("\"inputs\""));
    assertTrue(json.contains("\"outputs\""));
    assertTrue(json.contains("\"jacobian\""));
    assertTrue(json.contains("Feed.flowRate"));
  }

  @Test
  public void testCustomStepSize() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr", 1.0); // 1 kg/hr step
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    double[][] jacobian = calculator.calculateJacobian();

    assertNotNull(jacobian);
    assertTrue(jacobian[0][0] > 0);
  }

  @Test
  public void testRelativeStepSize() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    // Test with different relative step sizes
    calculator.setRelativeStepSize(1e-3);
    double[][] jacobian1 = calculator.calculateJacobian();

    calculator.setRelativeStepSize(1e-5);
    double[][] jacobian2 = calculator.calculateJacobian();

    // Results should be similar
    assertEquals(jacobian1[0][0], jacobian2[0][0], 0.01);
  }

  @Test
  public void testClearVariables() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    calculator.clearInputVariables();
    calculator.clearOutputVariables();

    assertEquals(0, calculator.getInputVariableNames().size());
    assertEquals(0, calculator.getOutputVariableNames().size());
  }

  @Test
  public void testVariableNotFoundThrowsException() {
    calculator.addInputVariable("Feed.flowRate", "kg/hr");
    calculator.addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr");

    assertThrows(IllegalArgumentException.class, () -> {
      calculator.getDerivative("NonExistent.variable", "Feed.flowRate");
    });
  }

  @Test
  public void testFluidMethodChaining() {
    ProcessDerivativeCalculator result = calculator.addInputVariable("Feed.flowRate", "kg/hr")
        .addInputVariable("Feed.pressure", "bara")
        .addOutputVariable("Separator.gasOutStream.flowRate", "kg/hr")
        .setMethod(ProcessDerivativeCalculator.DerivativeMethod.CENTRAL_DIFFERENCE)
        .setRelativeStepSize(1e-4);

    assertSame(calculator, result);
  }
}

package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for FluidBuilder fluent API and static factory methods.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FluidBuilderTest extends neqsim.NeqSimTest {

  @Test
  public void testFluentBuilder() {
    SystemInterface fluid = FluidBuilder.create(273.15 + 25.0, 60.0).addComponent("methane", 0.85)
        .addComponent("ethane", 0.10).addComponent("propane", 0.05).withMixingRule("classic")
        .build();

    assertNotNull(fluid);
    assertEquals(3, fluid.getNumberOfComponents());

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getDensity("kg/m3") > 0, "Density should be positive");
    assertTrue(fluid.getZ() > 0, "Z-factor should be positive");
  }

  @Test
  public void testBuilderWithPR() {
    SystemInterface fluid = FluidBuilder.create(273.15 + 80.0, 200.0)
        .withEOS(FluidBuilder.EOSType.PR).addComponent("methane", 0.50)
        .addComponent("n-hexane", 0.50).withMixingRule("classic").withMultiPhaseCheck().build();

    assertNotNull(fluid);
    assertTrue(fluid instanceof SystemPrEos, "Should be PR EOS");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getDensity("kg/m3") > 0);
  }

  @Test
  public void testLeanNaturalGas() {
    SystemInterface gas = FluidBuilder.leanNaturalGas(273.15 + 25.0, 60.0);

    assertNotNull(gas);
    assertTrue(gas.getNumberOfComponents() >= 5, "Should have multiple components");

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();

    double density = gas.getDensity("kg/m3");
    assertTrue(density > 10 && density < 200, "Gas density at 60 bara should be 10-200 kg/m3");
  }

  @Test
  public void testRichNaturalGas() {
    SystemInterface gas = FluidBuilder.richNaturalGas(273.15 + 25.0, 60.0);

    assertNotNull(gas);
    assertTrue(gas.getNumberOfComponents() >= 8, "Rich gas should have many components");

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();

    assertTrue(gas.getDensity("kg/m3") > 0);
  }

  @Test
  public void testCO2Rich() {
    SystemInterface co2 = FluidBuilder.co2Rich(273.15 + 40.0, 100.0);

    assertNotNull(co2);
    assertTrue(co2.getNumberOfComponents() >= 4);

    ThermodynamicOperations ops = new ThermodynamicOperations(co2);
    ops.TPflash();
    co2.initProperties();

    assertTrue(co2.getDensity("kg/m3") > 0);
  }

  @Test
  public void testDryExportGas() {
    SystemInterface gas = FluidBuilder.dryExportGas(273.15 + 25.0, 120.0);

    assertNotNull(gas);

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();

    double density = gas.getDensity("kg/m3");
    assertTrue(density > 0, "Density should be positive");
  }

  @Test
  public void testGasCondensate() {
    SystemInterface fluid = FluidBuilder.gasCondensate(273.15 + 100.0, 300.0);

    assertNotNull(fluid);
    assertTrue(fluid.getNumberOfComponents() >= 8);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getDensity("kg/m3") > 0);
  }

  @Test
  public void testAcidGas() {
    SystemInterface fluid = FluidBuilder.acidGas(273.15 + 40.0, 50.0);

    assertNotNull(fluid);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getDensity("kg/m3") > 0);
  }

  @Test
  public void testBuilderWithCPAMixingRule() {
    SystemInterface fluid = FluidBuilder.create(273.15 + 25.0, 10.0)
        .withEOS(FluidBuilder.EOSType.SRK_CPA).addComponent("methane", 0.90)
        .addComponent("water", 0.10).withMixingRule(10).withMultiPhaseCheck().build();

    assertNotNull(fluid);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getNumberOfPhases() >= 1);
  }
}

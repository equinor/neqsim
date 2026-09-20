package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** A model conversion must preserve the fluid or fail explicitly. */
class ModelConversionFailureTest extends neqsim.NeqSimTest {
  @Test
  void unsupportedDuanSunConversionCannotReturnAnUnrelatedCo2Fluid() {
    SystemInterface source = brine();
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> source.setModel("Duan-Sun"));
    assertTrue(error.getMessage().contains("Duan-Sun"));
    assertNotNull(error.getCause());
    assertTrue(error.getCause().getMessage().contains("not supported"));
    assertEquals(3, source.getNumberOfComponents());
    assertEquals(55.508, source.getComponent("water").getNumberOfmoles(), 0.0);
  }

  @Test
  void invalidModelNamesFailExplicitly() {
    SystemInterface source = brine();
    assertThrows(IllegalArgumentException.class, () -> source.setModel("no-such-model"));
    assertThrows(IllegalArgumentException.class, () -> source.setModel(null));
  }

  @Test
  void componentCopyFailureDoesNotReturnAPartialFluid() {
    SystemInterface source = brine();
    source.renameComponent("Cl-", "unregistered-conversion-test-component");
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> source.setModel("PR-EOS"));
    assertNotNull(error.getCause());
    assertTrue(error.getMessage().contains("unregistered-conversion-test-component"));
    assertEquals(3, source.getNumberOfComponents());
  }

  @Test
  void supportedConversionPreservesComponentsAmountsAndState() {
    SystemInterface source = brine();
    SystemInterface converted = source.setModel("PR-EOS");
    assertTrue(converted instanceof SystemPrEos);
    assertEquals(source.getNumberOfComponents(), converted.getNumberOfComponents());
    assertEquals(source.getTemperature(), converted.getTemperature(), 0.0);
    assertEquals(source.getPressure(), converted.getPressure(), 0.0);
    for (int i = 0; i < source.getNumberOfComponents(); i++) {
      String name = source.getComponent(i).getComponentName();
      assertEquals(source.getComponent(name).getNumberOfmoles(), converted.getComponent(name).getNumberOfmoles(), 0.0);
    }
  }

  private static SystemInterface brine() {
    SystemInterface source = new SystemSrkEos(313.15, 5.0);
    source.addComponent("water", 55.508);
    source.addComponent("Na+", 1.0);
    source.addComponent("Cl-", 1.0);
    source.init(0);
    return source;
  }
}

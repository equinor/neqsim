package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FormationEnthalpyReferenceTest {
  @Test
  void referencesAreExplicitAndLegacyValuesArePreserved() {
    ComponentInterface methane = new ComponentSrk("methane", 1.0, 1.0, 0);
    assertEquals(0.0, methane.getHID(273.15), 1.0e-10);
    assertEquals(882.7864286135118, methane.getHID(298.15), 1.0e-8);
    assertEquals(-74873.10, methane.getHID(298.15, true), 1.0e-8);
    assertFalse(methane.isUsingIdealGasEnthalpyOfFormation());
    methane.setUseIdealGasEnthalpyOfFormation(true);
    assertEquals(-74873.10, methane.getHID(298.15), 1.0e-8);
    assertEquals(-74873.10 - 882.7864286135118, methane.getHID(273.15), 1.0e-8);
    for (double t : new double[] {273.15, 298.15, 600.0, 1000.0}) {
      double derivative = (methane.getHID(t + 0.001) - methane.getHID(t - 0.001)) / 0.002;
      assertEquals(methane.getCp0(t), derivative, 1.0e-6);
    }
    methane.setCpA(methane.getCpA() + 10.0);
    assertEquals(-74873.10, methane.getHID(298.15), 1.0e-8);
  }

  @Test
  void knownZeroIsDistinctFromMissingDataAndUserValuesAreSupported() {
    for (String name : new String[] {"hydrogen", "nitrogen", "oxygen", "helium", "argon"}) {
      ComponentInterface element = component(name);
      assertTrue(element.hasIdealGasEnthalpyOfFormation(), name);
      assertEquals(0.0, element.getHID(298.15, true), 1.0e-10, name);
    }
    ComponentInterface unknown = component("default");
    assertFalse(unknown.hasIdealGasEnthalpyOfFormation());
    assertThrows(IllegalStateException.class, () -> unknown.getHID(298.15, true));
    assertThrows(IllegalStateException.class, () -> unknown.setUseIdealGasEnthalpyOfFormation(true));
    assertFalse(unknown.isUsingIdealGasEnthalpyOfFormation());
    unknown.setIdealGasEnthalpyOfFormation(12000.0);
    unknown.setUseIdealGasEnthalpyOfFormation(true);
    assertEquals(12000.0, unknown.getHID(298.15), 1.0e-8);
    assertEquals("user-supplied", unknown.getFormationEnthalpySource());
    assertThrows(IllegalArgumentException.class, () -> unknown.setIdealGasEnthalpyOfFormation(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> unknown.setIdealGasEnthalpyOfFormation(Double.POSITIVE_INFINITY));
    ComponentInterface ion = component("Na+");
    ion.setIdealGasEnthalpyOfFormation(0.0);
    assertFalse(ion.hasIdealGasEnthalpyOfFormation());
    assertThrows(IllegalStateException.class, () -> ion.setUseIdealGasEnthalpyOfFormation(true));
  }

  @Test
  void reactionHeatsMatchNistGasPhaseBenchmarksAt298K() {
    // NIST-JANAF/Chase 1998; water is vapor, so methane combustion gives the LHV.
    assertEquals(-802302.1, h("CO2") + 2 * h("water") - h("methane") - 2 * h("oxygen"), 0.1);
    assertEquals(-41168.9, h("CO2") + h("hydrogen") - h("CO") - h("water"), 0.1);
    assertEquals(-91796.12, 2 * h("ammonia") - h("nitrogen") - 3 * h("hydrogen"), 0.1);
  }

  private double h(String name) {
    return component(name).getHID(298.15, true);
  }

  private ComponentInterface component(String name) {
    return new ComponentSrk(name, 1.0, 1.0, 0);
  }
}

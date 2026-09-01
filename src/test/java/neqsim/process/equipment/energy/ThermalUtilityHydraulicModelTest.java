package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ThermalUtilityHydraulicModelTest {
  @Test
  void calculatesHeaderHydraulicsAndCapacity() {
    ThermalUtilityHydraulicModel model = new ThermalUtilityHydraulicModel();
    model.setGeometry(1000.0, 0.30, 4.5e-5);
    model.setFluidProperties(998.0, 1.0e-3);
    model.setLocalLossCoefficient(8.0);
    model.setPumpEfficiency(0.80);
    model.setCapacityLimits(2.5, 3.0e5);

    assertTrue(model.getVelocity(50.0) > 0.0);
    assertTrue(model.getReynoldsNumber(50.0) > 2300.0);
    assertTrue(model.getPressureDrop(50.0) > 0.0);
    assertTrue(model.getPumpPower(50.0) > 0.0);
    assertTrue(model.getMaximumMassFlow() > 0.0);
  }

  @Test
  void usesLaminarFrictionFactor() {
    ThermalUtilityHydraulicModel model = new ThermalUtilityHydraulicModel();
    model.setGeometry(10.0, 0.1, 0.0);
    model.setFluidProperties(1000.0, 1.0);
    double massFlow = 0.01;
    assertEquals(64.0 / model.getReynoldsNumber(massFlow), model.getFrictionFactor(massFlow), 1.0e-12);
  }
}

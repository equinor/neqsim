package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphasePHflash;

/** Integration coverage for formation-referenced stream enthalpy. */
class SystemFormationEnthalpyTest {
  @Test
  void compositionChangingStreamDutyIncludesMethaneCombustionHeat() {
    SystemInterface feed = new SystemSrkEos(298.15, 0.001);
    feed.addComponent("methane", 1.0);
    feed.addComponent("oxygen", 2.0);
    feed.setMixingRule("classic");
    feed.setUseIdealGasEnthalpyOfFormation(true);
    SystemInterface products = new SystemSrkEos(298.15, 0.001);
    products.addComponent("CO2", 1.0);
    products.addComponent("water", 2.0);
    products.setMixingRule("classic");
    products.setUseIdealGasEnthalpyOfFormation(true);
    flash(feed);
    flash(products);
    assertEquals(1, products.getNumberOfPhases());
    assertEquals(-802302.1, products.getEnthalpy() - feed.getEnthalpy(), 1.0);
    assertEquals(feed.getMass("kg"), products.getMass("kg"), 1.0e-5);
  }

  @Test
  void commonReferenceMixerConservesEnthalpyAndPhaseExtractionKeepsReference() {
    SystemInterface first = new SystemSrkEos(300.0, 10.0);
    first.addComponent("methane", 1.0);
    first.setMixingRule("classic");
    first.setUseIdealGasEnthalpyOfFormation(true);
    SystemInterface second = new SystemSrkEos(350.0, 10.0);
    second.addComponent("CO2", 1.0);
    second.setMixingRule("classic");
    second.setUseIdealGasEnthalpyOfFormation(true);
    Stream inlet1 = new Stream("methane feed", first);
    Stream inlet2 = new Stream("CO2 feed", second);
    inlet1.run();
    inlet2.run();
    double inlet = inlet1.getFluid().getEnthalpy() + inlet2.getFluid().getEnthalpy();
    Mixer mixer = new Mixer("formation mixer");
    mixer.addStream(inlet1);
    mixer.addStream(inlet2);
    mixer.run();
    SystemInterface outlet = mixer.getOutletStream().getFluid();
    assertTrue(outlet.isUsingIdealGasEnthalpyOfFormation());
    assertEquals(inlet, outlet.getEnthalpy(), Math.abs(inlet) * 1.0e-7);
    SystemInterface extracted = outlet.phaseToSystem(0);
    assertTrue(extracted.isUsingIdealGasEnthalpyOfFormation());
    assertTrue(extracted.getComponent("CO2").isUsingIdealGasEnthalpyOfFormation());
    SystemInterface converted = first.setModel("PR-EOS");
    assertTrue(converted.isUsingIdealGasEnthalpyOfFormation());
    second.setUseIdealGasEnthalpyOfFormation(false);
    assertThrows(IllegalArgumentException.class, () -> first.addFluid(second));
  }

  @Test
  void shiftPreservesPhaseSplitHeatCapacityEntropyAndHeatingDuty() {
    for (SystemInterface legacy : new SystemInterface[] {new SystemSrkEos(250.0, 20.0), new SystemPrEos(250.0, 20.0),
        new SystemSrkCPAstatoil(250.0, 20.0)}) {
      legacy.addComponent("methane", 0.5);
      legacy.addComponent("propane", 0.5);
      legacy.setMixingRule("classic");
      flash(legacy);
      SystemInterface formation = legacy.clone();
      formation.setUseIdealGasEnthalpyOfFormation(true);
      flash(formation);
      assertEquals(legacy.getNumberOfPhases(), formation.getNumberOfPhases());
      assertEquals(legacy.getEntropy(), formation.getEntropy(), 1.0e-8);
      assertEquals(legacy.getCp(), formation.getCp(), 1.0e-8);
      double expectedShift = 0.0;
      for (int i = 0; i < legacy.getNumberOfComponents(); i++) {
        ComponentInterface c = legacy.getComponent(i);
        expectedShift += c.getNumberOfmoles() * (c.getHID(298.15, true) - c.getHID(298.15, false));
      }
      assertEquals(expectedShift, formation.getEnthalpy() - legacy.getEnthalpy(), 1.0e-6);
      for (int p = 0; p < legacy.getNumberOfPhases(); p++) {
        assertEquals(legacy.getBeta(p), formation.getBeta(p), 1.0e-10);
        for (int i = 0; i < legacy.getNumberOfComponents(); i++) {
          assertEquals(legacy.getPhase(p).getComponent(i).getx(), formation.getPhase(p).getComponent(i).getx(),
              1.0e-10);
        }
      }
      double legacyIn = legacy.getEnthalpy();
      double formationIn = formation.getEnthalpy();
      legacy.setTemperature(350.0);
      formation.setTemperature(350.0);
      flash(legacy);
      flash(formation);
      assertEquals(legacy.getEnthalpy() - legacyIn, formation.getEnthalpy() - formationIn, 1.0e-6);
      double specification = formation.getEnthalpy();
      formation.setTemperature(330.0);
      new ThermodynamicOperations(formation).PHflash(specification);
      assertEquals(350.0, formation.getTemperature(), 1.0e-5);
    }
  }

  @Test
  void cloneSerializationAndLaterComponentsPreserveSelection() throws Exception {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.setUseIdealGasEnthalpyOfFormation(true);
    fluid.addComponent("CO2", 0.2);
    fluid.addComponent("hydrogen", 0.1, 0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    flash(fluid);
    assertTrue(fluid.getComponent("CO2").isUsingIdealGasEnthalpyOfFormation());
    SystemInterface clone = fluid.clone();
    assertTrue(clone.isUsingIdealGasEnthalpyOfFormation());
    clone.setUseIdealGasEnthalpyOfFormation(false);
    assertTrue(fluid.isUsingIdealGasEnthalpyOfFormation());
    assertFalse(clone.getComponent(0).isUsingIdealGasEnthalpyOfFormation());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      SystemInterface restored = (SystemInterface) input.readObject();
      flash(restored);
      assertTrue(restored.isUsingIdealGasEnthalpyOfFormation());
      assertEquals(fluid.getEnthalpy(), restored.getEnthalpy(), 1.0e-6);
    }
  }

  @Test
  void missingDataAndIndependentCaloricReferencesFailBeforeSwitching() {
    SystemInterface fluid = new SystemSrkEos(300.0, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("default", 1.0);
    assertThrows(IllegalStateException.class, () -> fluid.setUseIdealGasEnthalpyOfFormation(true));
    assertFalse(fluid.isUsingIdealGasEnthalpyOfFormation());
    assertFalse(fluid.getComponent("methane").isUsingIdealGasEnthalpyOfFormation());
    SystemInterface supported = new SystemSrkEos(300.0, 1.0);
    supported.addComponent("methane", 1.0);
    supported.setUseIdealGasEnthalpyOfFormation(true);
    assertThrows(IllegalStateException.class, () -> supported.addComponent("default", 1.0));
    assertEquals(1, supported.getNumberOfComponents());
    assertEquals(1.0, supported.getTotalNumberOfMoles(), 1.0e-12);
    SystemInterface nativeModel = new SystemGERG2008Eos(300.0, 1.0);
    nativeModel.addComponent("methane", 1.0);
    assertThrows(IllegalStateException.class, () -> nativeModel.setUseIdealGasEnthalpyOfFormation(true));
    assertFalse(nativeModel.isUsingIdealGasEnthalpyOfFormation());
    SystemInterface characterized = new SystemSrkEos(300.0, 1.0);
    characterized.addTBPfraction("C10", 1.0, 0.140, 0.78);
    assertFalse(characterized.getComponent(0).hasIdealGasEnthalpyOfFormation());
    assertThrows(IllegalStateException.class, () -> characterized.setUseIdealGasEnthalpyOfFormation(true));
    for (int p = 0; p < characterized.getMaxNumberOfPhases(); p++) {
      characterized.getPhase(p).getComponent(0).setIdealGasEnthalpyOfFormation(-250000.0);
    }
    characterized.setUseIdealGasEnthalpyOfFormation(true);
    assertEquals(-250000.0, characterized.getComponent(0).getHID(298.15), 1.0e-8);
  }

  @Test
  void reactivePhClosesStreamEnthalpyWithoutDoubleCounting() {
    SystemInterface fluid = new SystemSrkEos(700.0, 10.0);
    fluid.addComponent("CO", 0.40);
    fluid.addComponent("water", 0.40);
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("hydrogen", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMaxNumberOfPhases(1);
    fluid.setNumberOfPhases(1);
    fluid.setUseIdealGasEnthalpyOfFormation(true);
    flash(fluid);
    double inlet = fluid.getEnthalpy();
    double initialCO2 = fluid.getComponent("CO2").getNumberOfMolesInPhase();
    ReactiveMultiphasePHflash operation = new ReactiveMultiphasePHflash(fluid, inlet);
    operation.run();
    fluid.init(2);
    assertTrue(operation.isConverged());
    assertTrue(fluid.getTemperature() > 700.0);
    assertTrue(fluid.getComponent("CO2").getNumberOfMolesInPhase() > initialCO2);
    assertEquals(inlet, fluid.getEnthalpy(), Math.abs(inlet) * 1.0e-7);
    // Independent integration of each species' polynomial from 298.15 K plus departure.
    double independent = fluid.getPhase(0).getHresTP();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      double t = fluid.getTemperature();
      double tr = 298.15;
      double sensible = c.getCpA() * (t - tr) + c.getCpB() / 2 * (t * t - tr * tr)
          + c.getCpC() / 3 * (Math.pow(t, 3) - Math.pow(tr, 3)) + c.getCpD() / 4 * (Math.pow(t, 4) - Math.pow(tr, 4))
          + c.getCpE() / 5 * (Math.pow(t, 5) - Math.pow(tr, 5));
      independent += c.getNumberOfMolesInPhase() * (c.getIdealGasEnthalpyOfFormation() + sensible);
    }
    assertEquals(inlet, independent, Math.abs(inlet) * 1.0e-7);
  }

  private void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
  }
}

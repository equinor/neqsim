package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for the pressure units and sizing state reported in issue #3446. */
class ThrottlingValveSizingStateRegressionTest extends neqsim.NeqSimTest {
  private Stream gasInlet() {
    SystemSrkEos fluid = new SystemSrkEos(296.25, 1.82925);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("ethane", 0.2);
    fluid.addComponent("propane", 0.4);
    fluid.addComponent("n-butane", 0.3);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(14942.5, "kg/hr");
    inlet.run();
    return inlet;
  }

  private void setDesignPressure(ThrottlingValve valve, String unit) {
    valve.getOutletStream().setPressure(1.41325, "bara");
    valve.setOutletPressure(valve.getOutletStream().getPressure(unit), unit);
  }

  @ParameterizedTest
  @CsvSource({ "bara, false", "barg, false", "kPa, false", "bara, true", "barg, true", "kPa, true" })
  void pressureInversionPreservesUnitsAndDesignPoint(String unit, boolean isothermal) {
    Stream inlet = gasInlet();
    ThrottlingValve valve = new ThrottlingValve("sized valve", inlet);
    setDesignPressure(valve, unit);
    valve.setIsoThermal(isothermal);
    valve.autoSize(1.0, 100.0);
    assertEquals(1.41325, valve.getOutletPressure(), 1.0e-6, "Sizing must preserve the specified absolute pressure");
    assertEquals(1.41325, valve.getOutletStream().getPressure("bara"), 1.0e-6);
    double cv = valve.getCv();
    assertEquals(inlet.getFlowRate("mole/sec"), valve.calculateMolarFlow(), 1.0e-6);

    valve.setIsCalcOutPressure(true);
    ProcessSystem process = new ProcessSystem("pressure inversion");
    process.add(inlet);
    process.add(valve);
    double previousDrop = 0.0;
    for (double factor : new double[] { 0.95, 1.0, 1.05 }) {
      inlet.setFlowRate(14942.5 * factor, "kg/hr");
      process.run();
      double outletPressure = valve.getOutletStream().getPressure("bara");
      assertEquals(outletPressure, valve.getOutletPressure(), 1.0e-12);
      double pressureDrop = inlet.getPressure("bara") - outletPressure;
      assertTrue(pressureDrop > previousDrop, "Pressure drop must increase continuously with flow");
      assertTrue(outletPressure > 0.1, "Nearby flows must not hit the pressure floor");
      assertEquals(inlet.getFlowRate("mole/sec"), valve.calculateMolarFlow(), 2.0e-4);
      assertEquals(inlet.getFlowRate("kg/hr"), valve.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
      assertEquals(cv, valve.getCv(), 0.0);
      if (factor == 1.0) {
        assertEquals(1.41325, outletPressure, 1.0e-5);
      }
      previousDrop = pressureDrop;
    }
  }

  @ParameterizedTest
  @ValueSource(strings = { "bara", "barg", "kPa" })
  void transientPressureInversionPreservesUnits(String unit) {
    Stream inlet = gasInlet();
    ThrottlingValve valve = new ThrottlingValve("transient valve", inlet);
    setDesignPressure(valve, unit);
    valve.autoSize(1.0, 100.0);
    valve.setIsCalcOutPressure(true);
    valve.setCalculateSteadyState(false);
    valve.runTransient(0.1);
    assertEquals(1.41325, valve.getOutletPressure(), 1.0e-5);
    assertEquals(14942.5, inlet.getFlowRate("kg/hr"), 0.1);
    assertEquals(14942.5, valve.getOutletStream().getFlowRate("kg/hr"), 0.1);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void copiedCvUsesTheSameEquationBeforeFirstRun(boolean richGas) {
    Stream inlet = gasInlet();
    if (!richGas) {
      SystemSrkEos fluid = new SystemSrkEos(296.25, 1.82925);
      fluid.addComponent("methane", 1.0);
      fluid.setMixingRule("classic");
      inlet = new Stream("methane inlet", fluid);
      inlet.setFlowRate(14942.5, "kg/hr");
      inlet.run();
    }
    // Keep an independent, initialized copy of the design inlet before autoSize.
    Stream copiedInlet = new Stream("copied inlet", inlet.getFluid().clone());
    ThrottlingValve sized = new ThrottlingValve("sized", inlet);
    sized.setOutletPressure(1.41325, "bara");
    sized.autoSize(1.0, 100.0);

    ThrottlingValve copied = new ThrottlingValve("copied", copiedInlet);
    copied.setOutletPressure(1.41325, "bara");
    copied.setCv(sized.getCv());
    copied.setPercentValveOpening(100.0);
    double designMolarFlow = inlet.getFlowRate("mole/sec");
    assertEquals(designMolarFlow, sized.calculateMolarFlow(), 1.0e-5);
    assertEquals(designMolarFlow, copied.calculateMolarFlow(), 1.0e-5,
        "An initialized inlet and the same Cv must give the same flow without running or sizing the copy");
    assertEquals(1.41325, copied.calculateOutletPressure(copied.getKv()), 1.0e-5);
    assertEquals(sized.isGasValve(), copied.isGasValve());
    assertEquals(copiedInlet.getFluid().getDensity("kg/m3"), inlet.getFluid().getDensity("kg/m3"), 1.0e-10,
        "Sizing must preserve the initialized inlet density");
    assertEquals(copiedInlet.getFluid().getGamma2(), inlet.getFluid().getGamma2(), 1.0e-10);
    assertEquals(copiedInlet.getFluid().getZ(), inlet.getFluid().getZ(), 1.0e-10);
  }
}

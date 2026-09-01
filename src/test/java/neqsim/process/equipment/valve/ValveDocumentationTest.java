package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Executable coverage for examples in docs/process/equipment/valves.md. */
class ValveDocumentationTest {
  /**
   * Creates the common gas feed used by the documented valve examples.
   *
   * @return initialized gas feed stream
   */
  private Stream createGasFeed() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("feed", fluid);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.setTemperature(30.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();
    return inlet;
  }

  /** Verifies the complete pressure-letdown example and its isenthalpic invariant. */
  @Test
  void pressureLetdownPreservesMolarEnthalpy() {
    Stream inlet = createGasFeed();
    double inletEnthalpy = inlet.getFluid().getEnthalpy("J/mol");
    double inletTemperature = inlet.getTemperature("C");

    ThrottlingValve valve = new ThrottlingValve("PV-100", inlet);
    valve.setOutletPressure(30.0, "bara");
    valve.run();

    double outletEnthalpy = valve.getOutletStream().getFluid().getEnthalpy("J/mol");
    double outletTemperature = valve.getOutletStream().getTemperature("C");
    double enthalpyTolerance = Math.max(1.0e-6, Math.abs(inletEnthalpy) * 1.0e-8);

    assertEquals(30.0, valve.getOutletStream().getPressure("bara"), 1.0e-8);
    assertEquals(inletEnthalpy, outletEnthalpy, enthalpyTolerance);
    assertTrue(Double.isFinite(outletTemperature));
    assertTrue(Math.abs(outletTemperature - inletTemperature) < 100.0);
  }

  /** Verifies Cv/Kv conversion, valve opening, and mechanical-design APIs. */
  @Test
  void cvOpeningAndMechanicalDesignApisAreExecutable() {
    Stream inlet = createGasFeed();
    ThrottlingValve valve = new ThrottlingValve("PCV-101", inlet);
    valve.setCv(150.0, "US");
    valve.setPercentValveOpening(50.0);

    assertEquals(150.0, valve.getCv("US"), 1.0e-10);
    assertEquals(150.0 / 1.156, valve.getCv("SI"), 1.0e-10);
    assertEquals(50.0, valve.getPercentValveOpening(), 1.0e-10);

    valve.setOutletPressure(60.0, "bara");
    valve.run();

    ValveMechanicalDesign design = valve.getMechanicalDesign();
    design.setValveCharacterization("equal percentage");
    design.setValveSizingStandard("IEC 60534");
    design.calcDesign();

    assertEquals("equal percentage", design.getValveCharacterization());
    assertTrue(design.getAnsiPressureClass() > 0);
    assertTrue(design.getNominalSizeInches() > 0.0);
    assertTrue(design.getRequiredActuatorThrust() >= 0.0);
    assertTrue(design.getWeightTotal() > 0.0);
  }

  /** Verifies production-choke configuration and the documented dynamic-travel APIs. */
  @Test
  void productionChokeAndDynamicTravelApisAreExecutable() {
    Stream inlet = createGasFeed();
    ThrottlingValve choke = new ThrottlingValve("Production choke", inlet);
    choke.setOutletPressure(30.0, "bara");

    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeDiameter(32.0, "64ths");
    design.setChokeDischargeCoefficient(0.84);

    assertEquals("Sachdeva", design.getValveSizingStandard());
    assertEquals(0.0127, design.getChokeDiameter(), 1.0e-12);

    choke.run();
    choke.setCalculateSteadyState(false);
    choke.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    choke.setTravelTime(10.0);
    choke.setPercentValveOpening(20.0);
    choke.setTargetPercentValveOpening(80.0);
    choke.runTransient(1.0, UUID.randomUUID());

    assertEquals(80.0, choke.getTargetPercentValveOpening(), 1.0e-10);
    assertTrue(choke.getPercentValveOpening() > 20.0);
    assertTrue(choke.getPercentValveOpening() < 80.0);
    assertNotNull(choke.getOutletStream());
  }
}

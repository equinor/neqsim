package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign.SafetyValveScenarioReport;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign.SafetyValveScenarioResult;
import neqsim.process.equipment.valve.SafetyValve.FluidService;
import neqsim.process.equipment.valve.SafetyValve.RelievingScenario;
import neqsim.process.equipment.valve.SafetyValve.SizingStandard;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for the safety valve mechanical design sizing strategies. */
public class SafetyValveMechanicalDesignTest {

  private SystemInterface createGasSystem(double temperature, double pressure, double flowRate) {
    SystemInterface gas = new SystemSrkEos(temperature, pressure);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    gas.setTotalFlowRate(flowRate, "kg/sec");
    return gas;
  }

  private SystemInterface createLiquidSystem(double temperature, double pressure, double flowRate) {
    SystemInterface liquid = new SystemSrkEos(temperature, pressure);
    liquid.addComponent("water", 1.0);
    liquid.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(liquid);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    liquid.setTotalFlowRate(flowRate, "kg/sec");
    return liquid;
  }

  private SystemInterface createMultiphaseSystem(double temperature, double pressure,
      double flowRate) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("water", 0.4);
    fluid.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    fluid.setTotalFlowRate(flowRate, "kg/sec");
    return fluid;
  }

  private SafetyValve createValve(StreamInterface stream, double setPressure) {
    SafetyValve valve = new SafetyValve("PSV", stream);
    valve.setPressureSpec(setPressure);
    valve.clearRelievingScenarios();
    return valve;
  }

  private SafetyValveMechanicalDesign designFor(SafetyValve valve) {
    return (SafetyValveMechanicalDesign) valve.getMechanicalDesign();
  }

  @Test
  void gasApiStrategyMatchesManualCalculation() {
    SystemInterface gas = createGasSystem(300.0, 50.0, 10.0);
    StreamInterface stream = new Stream("gas", gas);
    SafetyValve valve = createValve(stream, 50.0);

    RelievingScenario scenario = new RelievingScenario.Builder("gasAPI")
        .fluidService(FluidService.GAS)
        .relievingStream(stream)
        .setPressure(50.0)
        .overpressureFraction(0.0)
        .backPressure(0.0)
        .sizingStandard(SizingStandard.API_520)
        .build();

    valve.addScenario(scenario);

    SafetyValveMechanicalDesign design = designFor(valve);
    design.calcDesign();

    SafetyValveScenarioResult result = design.getScenarioResults().get("gasAPI");
    double relievingPressure = 50.0 * 1e5;
    double kd = 0.975;
    double kb = 1.0;
    double kw = 1.0;
    double expected = design.calcGasOrificeAreaAPI520(gas.getFlowRate("kg/sec"), relievingPressure,
        gas.getTemperature(), gas.getZ(), gas.getMolarMass(), gas.getGamma(), kd, kb, kw);

    assertEquals(expected, result.getRequiredOrificeArea(), 1e-8);
    assertEquals(expected, design.getOrificeArea(), 1e-8);
    assertEquals(expected, design.getControllingOrificeArea(), 1e-8);
  }

  @Test
  void liquidSizingMatchesEnergyBalanceEquation() {
    SystemInterface liquid = createLiquidSystem(298.15, 15.0, 5.0);
    StreamInterface stream = new Stream("liquid", liquid);
    SafetyValve valve = createValve(stream, 15.0);

    RelievingScenario scenario = new RelievingScenario.Builder("liquid")
        .fluidService(FluidService.LIQUID)
        .relievingStream(stream)
        .setPressure(15.0)
        .overpressureFraction(0.1)
        .backPressure(1.0)
        .build();

    valve.addScenario(scenario);

    SafetyValveMechanicalDesign design = designFor(valve);
    design.calcDesign();

    SafetyValveScenarioResult result = design.getScenarioResults().get("liquid");
    double relievingPressure = 15.0 * 1e5 * 1.1;
    double deltaP = relievingPressure - 1.0 * 1e5;
    double kd = 0.62;
    double kb = 1.0;
    double kw = 1.0;
    double expected = 5.0 / (kd * kb * kw * Math
        .sqrt(2.0 * liquid.getDensity("kg/m3") * deltaP));

    assertEquals(expected, result.getRequiredOrificeArea(), 1e-8);
    assertEquals(expected, design.getOrificeArea(), 1e-8);
    assertEquals(expected, design.getControllingOrificeArea(), 1e-8);
  }

  @Test
  void multiphaseSizingUsesHemApproximation() {
    SystemInterface fluid = createMultiphaseSystem(290.0, 30.0, 8.0);
    StreamInterface stream = new Stream("multiphase", fluid);
    SafetyValve valve = createValve(stream, 30.0);

    RelievingScenario scenario = new RelievingScenario.Builder("multiphase")
        .fluidService(FluidService.MULTIPHASE)
        .relievingStream(stream)
        .setPressure(30.0)
        .overpressureFraction(0.05)
        .backPressure(5.0)
        .build();

    valve.addScenario(scenario);

    SafetyValveMechanicalDesign design = designFor(valve);
    design.calcDesign();

    SafetyValveScenarioResult result = design.getScenarioResults().get("multiphase");
    double relievingPressure = 30.0 * 1e5 * 1.05;
    double deltaP = relievingPressure - 5.0 * 1e5;
    double kd = 0.85;
    double kb = 1.0;
    double kw = 1.0;
    double expected = 8.0 / (kd * kb * kw * Math
        .sqrt(fluid.getDensity("kg/m3") * deltaP));

    assertEquals(expected, result.getRequiredOrificeArea(), 1e-8);
    assertEquals(expected, design.getOrificeArea(), 1e-8);
    assertEquals(expected, design.getControllingOrificeArea(), 1e-8);
  }

  @Test
  void fireCaseAppliesMarginAndSupportsScenarioSwitching() {
    SystemInterface gas = createGasSystem(320.0, 60.0, 12.0);
    StreamInterface fireStream = new Stream("fire", gas);
    SafetyValve valve = createValve(fireStream, 60.0);

    RelievingScenario apiScenario = new RelievingScenario.Builder("apiGas")
        .fluidService(FluidService.GAS)
        .relievingStream(fireStream)
        .setPressure(60.0)
        .overpressureFraction(0.0)
        .backPressure(0.0)
        .sizingStandard(SizingStandard.API_520)
        .build();

    SystemInterface fireGas = createGasSystem(320.0, 60.0, 14.0);
    StreamInterface fireScenarioStream = new Stream("fire-case", fireGas);
    RelievingScenario fireScenario = new RelievingScenario.Builder("fire")
        .fluidService(FluidService.FIRE)
        .relievingStream(fireScenarioStream)
        .setPressure(60.0)
        .overpressureFraction(0.1)
        .backPressure(2.0)
        .sizingStandard(SizingStandard.API_520)
        .build();

    valve.addScenario(apiScenario);
    valve.addScenario(fireScenario);
    valve.setActiveScenario("fire");

    SafetyValveMechanicalDesign design = designFor(valve);
    design.calcDesign();

    SafetyValveScenarioResult fireResult = design.getScenarioResults().get("fire");
    double relievingPressure = 60.0 * 1e5 * 1.1;
    double kd = 0.975;
    double kb = 1.0;
    double kw = 1.0;
    double baseArea = design.calcGasOrificeAreaAPI520(fireGas.getFlowRate("kg/sec"),
        relievingPressure, fireGas.getTemperature(), fireGas.getZ(), fireGas.getMolarMass(),
        fireGas.getGamma(), kd, kb, kw);
    double expected = baseArea * 1.1;

    assertEquals(expected, fireResult.getRequiredOrificeArea(), 1e-8);
    assertEquals(expected, design.getOrificeArea(), 1e-8);
    assertEquals("fire", design.getControllingScenarioName());
    assertEquals(expected, design.getControllingOrificeArea(), 1e-8);

    Map<String, SafetyValveScenarioReport> report = design.getScenarioReports();
    SafetyValveScenarioReport fireReport = report.get("fire");
    assertTrue(fireReport.isControllingScenario());
    assertEquals(60.0, fireReport.getSetPressureBar(), 1e-8);
    assertEquals(6.0, fireReport.getOverpressureMarginBar(), 1e-8);
  }
}

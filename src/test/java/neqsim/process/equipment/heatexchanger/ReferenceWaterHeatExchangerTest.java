package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSeawater;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemWaterIF97;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * End-to-end reference cooling-water integration and balance checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReferenceWaterHeatExchangerTest {
  /**
   * Builds a public synthetic hot natural-gas stream.
   *
   * @return methane-rich gas at 120 C and 20 bara, 1 kg/s
   */
  private Stream hotGas() {
    SystemInterface fluid = new SystemSrkEos(393.15, 20.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1.0, "kg/sec");
    Stream stream = new Stream("hot gas", fluid);
    stream.run();
    return stream;
  }

  /**
   * Verifies mass and energy closure across both explicit streams.
   *
   * @param exchanger calculated two-stream exchanger
   */
  private void assertBalances(HeatExchanger exchanger) {
    double residual = 0.0;
    for (int side = 0; side < 2; side++) {
      SystemInterface inlet = exchanger.getInStream(side).getThermoSystem();
      SystemInterface outlet = exchanger.getOutStream(side).getThermoSystem();
      residual += outlet.getEnthalpy() - inlet.getEnthalpy();
      assertEquals(inlet.getFlowRate("kg/sec"), outlet.getFlowRate("kg/sec"), 1e-10);
    }
    assertEquals(0.0, residual, Math.max(0.01, Math.abs(exchanger.getDuty()) * 1e-7));
    assertTrue(exchanger.getOutStream(0).getTemperature() < exchanger.getInStream(0).getTemperature());
    assertTrue(exchanger.getOutStream(1).getTemperature() > exchanger.getInStream(1).getTemperature());
  }

  /** Checks cooling demand, volumetric units, and preservation of the process-side model. */
  @Test
  void utilitySizingDoesNotChangeProcessFluid() {
    Stream gas = hotGas();
    PhysicalPropertyModel originalModel = gas.getThermoSystem().getPhase(0).getPhysicalPropertyModel();
    WaterCooler cooler = new WaterCooler("water cooler", gas);
    cooler.setOutletTemperature(40.0, "C");
    cooler.setWaterInletTemperature(15.0, "C");
    cooler.setWaterOutletTemperature(25.0, "C");
    cooler.setWaterPressure(3.0, "bara");
    cooler.run();
    double freshMassFlow = cooler.getCoolingWaterFlowRate("kg/sec");
    double processDuty = cooler.getDuty();
    cooler.setCoolingWaterSalinity(35.0);
    assertTrue(cooler.needRecalculation());
    ProcessSystem process = new ProcessSystem();
    process.add(gas);
    process.add(cooler);
    process.run();
    assertEquals(processDuty, cooler.getDuty(), 1e-6);
    assertEquals(originalModel, gas.getThermoSystem().getPhase(0).getPhysicalPropertyModel());
    assertEquals(originalModel, cooler.getOutletStream().getThermoSystem().getPhase(0).getPhysicalPropertyModel());
    assertTrue(cooler.getCoolingWaterFlowRate("kg/sec") > freshMassFlow * 1.04);
    SystemSeawater inletWater = new SystemSeawater(288.15, 3.0, 35.0);
    new ThermodynamicOperations(inletWater).TPflash();
    assertEquals(cooler.getCoolingWaterFlowRate("kg/sec") / inletWater.getDensity("kg/m3") * 3600.0,
        cooler.getCoolingWaterFlowRate("m3/hr"), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> cooler.getCoolingWaterFlowRate("Sm3/hr"));
    cooler.setWaterOutletTemperature(15.0, "C");
    assertThrows(IllegalArgumentException.class, cooler::run);
    assertTrue(Double.isNaN(cooler.getCoolingWaterFlowRate("kg/sec")));
  }

  /** Checks fixed-UA operation with a hot inlet above the saline property limit, in either capacity ordering. */
  @Test
  void seawaterFixedUaBalanceAndRepeat() {
    for (double waterFlow : new double[] {0.3, 2.0}) {
      Stream gas = hotGas();
      SystemSeawater water = new SystemSeawater(288.15, 3.0, 35.0);
      water.setTotalFlowRate(waterFlow, "kg/sec");
      Stream cooling = new Stream("seawater supply", water);
      HeatExchanger exchanger = new HeatExchanger("E-100", gas, cooling);
      exchanger.setUAvalue(500.0);
      exchanger.setGuessOutTemperature(65.0, "C");
      ProcessSystem process = new ProcessSystem();
      process.add(gas);
      process.add(cooling);
      process.add(exchanger);
      process.run();
      assertBalances(exchanger);
      double outletTemperature = exchanger.getOutStream(1).getTemperature();
      exchanger.run();
      assertEquals(outletTemperature, exchanger.getOutStream(1).getTemperature(), 1e-7);
      ((SystemSeawater) cooling.getThermoSystem()).setSalinity(20.0);
      assertTrue(exchanger.needRecalculation());
      process.run();
      assertBalances(exchanger);
      assertEquals(20.0, ((SystemSeawater) exchanger.getOutStream(1).getThermoSystem()).getSalinity(), 0.0);
      HeatExchanger.FoulingScreeningResult screening = exchanger.foulingScreening(new double[] {1.0, 0.5});
      assertTrue(screening.getDuty()[0] > screening.getDuty()[1]);
      assertTrue(screening.getColdOutletTemperature()[0] > screening.getColdOutletTemperature()[1]);
    }
  }

  /** Checks process-temperature specification and pure-water reference utility selection. */
  @Test
  void specifiedOutletWithPureWaterAndSeawater() {
    for (SystemInterface utility : new SystemInterface[] {new SystemWaterIF97(288.15, 3.0),
        new SystemSeawater(288.15, 3.0, 35.0)}) {
      Stream gas = hotGas();
      utility.setTotalFlowRate(10.0, "kg/sec");
      Stream cooling = new Stream("cooling supply", utility);
      cooling.run();
      HeatExchanger exchanger = new HeatExchanger("E-101", gas, cooling);
      exchanger.setOutTemperature(40.0, "C");
      exchanger.run();
      assertEquals(40.0, exchanger.getOutStream(0).getTemperature("C"), 1e-7);
      assertBalances(exchanger);
    }
  }

  /** Checks that the existing geometry-based rating consumes saline reference properties. */
  @Test
  void geometryRatingWithSeawater() {
    SystemWaterIF97 hotFluid = new SystemWaterIF97(333.15, 8.0);
    hotFluid.setTotalFlowRate(8.0, "kg/sec");
    Stream hot = new Stream("hot water", hotFluid);
    SystemSeawater coldFluid = new SystemSeawater(288.15, 8.0, 35.0);
    coldFluid.setTotalFlowRate(10.0, "kg/sec");
    Stream cold = new Stream("sea water", coldFluid);
    HeatExchanger exchanger = new HeatExchanger("rated cooler", hot, cold);
    ThermalDesignCalculator calculator = new ThermalDesignCalculator();
    calculator.setTubeIDm(0.020);
    calculator.setTubeODm(0.025);
    calculator.setTubePitchm(0.032);
    calculator.setTubeCount(100);
    calculator.setTubePasses(2);
    calculator.setTubeLengthm(4.0);
    exchanger.setRatingCalculator(calculator);
    exchanger.setRatingArea(Math.PI * 0.025 * 4.0 * 100);
    exchanger.setUseRatingPressureDrop(true);
    exchanger.run();
    assertBalances(exchanger);
    assertTrue(exchanger.getUAvalue() > 0.0);
    assertTrue(exchanger.getOutStream(1).getPressure() < cold.getPressure());
  }
}

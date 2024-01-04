package neqsim.processSimulation.processEquipment.reservoir;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

public class SimpleReservoirTest {
  static Logger logger = LogManager.getLogger(SimpleReservoirTest.class);

  @Test
  void testRun2() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(373.15, 900.0);
    fluid1.addComponent("water", 3.599);
    fluid1.addComponent("nitrogen", 0.599);
    fluid1.addComponent("CO2", 0.51);
    fluid1.addComponent("methane", 62.8);
    fluid1.addComponent("ethane", 8.12);
    fluid1.addComponent("propane", 4.95);
    fluid1.addComponent("i-butane", 1.25);
    fluid1.addComponent("n-butane", 1.25);
    fluid1.addComponent("i-pentane", 0.25);
    fluid1.addComponent("n-pentane", 0.25);
    fluid1.addComponent("n-hexane", 0.81);
    fluid1.addPlusFraction("C7", 10.5, 180.0 / 1000.0, 840.0 / 1000.0);
    fluid1.getCharacterization().characterisePlusFraction();
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 0, 635949179.71, 10.0e7);

    StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
    producedOilStream.setFlowRate(6500.0 / 0.86 * 1000.0 * 4, "kg/day");

    StreamInterface producedWaterStream = reservoirOps.addWaterProducer("waterproducer_1");
    producedWaterStream.setFlowRate(10000, "kg/day");

    StreamInterface injectorGasStream = reservoirOps.addGasInjector("gasinjector_1");
    neqsim.thermo.system.SystemInterface fluidGas = fluid1.clone();
    fluidGas.setMolarComposition(new double[] {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0});
    injectorGasStream.setFluid(fluidGas);
    injectorGasStream.setFlowRate(5.0, "MSm3/day");

    StreamInterface injectorWaterStream = reservoirOps.addWaterInjector("waterinjector_1");
    neqsim.thermo.system.SystemInterface fluidWater = fluid1.clone();
    fluidWater.setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0});
    injectorWaterStream.setFluid(fluidWater);
    injectorWaterStream.setFlowRate(8000.0 * 1000 / 3.0 * 2, "kg/day");

    reservoirOps.run();

    double deltaTime = 24 * 60 * 60.0 * 365;
    for (int i = 0; i < 10; i++) {
      reservoirOps.runTransient(deltaTime);
    }
    Assertions.assertEquals(352.274030, reservoirOps.getReservoirFluid().getPressure("bara"), 0.1);
    Assertions.assertEquals(11.698, reservoirOps.getWaterProdution("Sm3/day"), 0.1);

    reservoirOps.setLowPressureLimit(52.0e5, "Pa");
    Assertions.assertEquals(52.0, reservoirOps.getLowPressureLimit("bara"), 0.1);
    Assertions.assertEquals(52.0e5, reservoirOps.getLowPressureLimit("Pa"), 0.1);
  }

  @Test
  void testPureGasReservoir() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemPrEos(273.15 + 13, 59.7);
    fluid1.addComponent("water", 1.1);
    fluid1.addComponent("methane", 99.5);
    fluid1.addComponent("nitrogen", 0.45);
    fluid1.setMixingRule("classic");
    fluid1.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(fluid1, 1 * 27 * 1e9 / 60.0, 0.0, 1.0e9);
    reservoirOps.setLowPressureLimit(5.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("gas producer 1");
    // StreamInterface producedWaterStream = reservoirOps.addWaterProducer("water producer 1");

    double[] productionprofile = new double[] {10.38, 9.9, 9.22, 8.18, 6.94, 5.83, 4.91, 4.09, 3.41,
        2.86, 2.35, 2.35, 2.35, 2.35, 2.35, 2.35, 2.35, 2.35};
    // double[] producedwaterprofile =
    // new double[] {1e-12, 1e-12, 1e-12, 50.0, 100.94, 5.83, 4.91, 4.09};
    reservoirOps.run();

    System.out.println("OGIP " + reservoirOps.getOGIP("GSm3") + " GMSm3");
    double deltaTime = 24 * 60 * 60.0 * 365;
    for (int i = 0; i < 13; i++) {

      producedGasStream.setFlowRate(productionprofile[i], "MSm3/day");
      // producedWaterStream.setFlowRate(producedwaterprofile[i] * 1000.0, "kg/day");
      System.out.println("pressure " + reservoirOps.getReservoirFluid().getPressure("bara")
          + " water in gas " + producedGasStream.getFluid().getComponent("water").getx()
          + " produced MSm3oe " + reservoirOps.getProductionTotal("MSm3 oe"));
      reservoirOps.runTransient(deltaTime);

      System.out
          .println("flow calc" + Math.sqrt((10.0) / reservoirOps.getFluid().getDensity("kg/m3")));
    }
    // valve1.setOutletPressure(51);
    // valve1.run();
    ThrottlingValve valve1 = new ThrottlingValve("valve1", producedGasStream);
    // valve1.setOutletPressure(51);
    valve1.setCv(25443.44371323);
    valve1.setIsCalcOutPressure(true);
    valve1.run();
    System.out.println("flow " + valve1.getOutletStream().getFlowRate("MSm3/day"));
    System.out.println("out pres " + valve1.getOutletPressure());
    System.out.println("Cv " + valve1.getCv());

    Compressor subseaCompressor = new Compressor("compressor 1", valve1.getOutletStream());
    subseaCompressor.setOutletPressure(40.0, "bara");
    subseaCompressor.setPolytropicEfficiency(0.8);
    subseaCompressor.setUsePolytropicCalc(true);
    subseaCompressor.run();

    System.out.println("compressor power " + subseaCompressor.getPower("MW") + " head "
        + subseaCompressor.getPolytropicFluidHead() + " kJ/kg" + " flow "
        + subseaCompressor.getInletStream().getFlowRate("m3/hr") + " m3/hr " + " pressure out "
        + subseaCompressor.getOutletPressure() + " pressure in "
        + subseaCompressor.getInletPressure() + " pressure ratio "
        + subseaCompressor.getOutletPressure() / subseaCompressor.getInletPressure());

    Cooler cooler1 = new Cooler("cooler1", subseaCompressor.getOutletStream());
    cooler1.setOutTemperature(30.0, "C");

    Compressor subseaCompressor2 = new Compressor("compressor 1", cooler1.getOutletStream());
    subseaCompressor2.setOutletPressure(170.0, "bara");
    subseaCompressor2.setPolytropicEfficiency(0.8);
    subseaCompressor2.setUsePolytropicCalc(true);
    subseaCompressor2.run();

    System.out.println("compressor power " + subseaCompressor2.getPower("MW") + " head "
        + subseaCompressor2.getPolytropicFluidHead() + " kJ/kg" + " flow "
        + subseaCompressor2.getInletStream().getFlowRate("m3/hr") + " m3/hr " + " pressure out "
        + subseaCompressor2.getOutletPressure() + " pressure in "
        + subseaCompressor2.getInletPressure() + " pressure ratio "
        + subseaCompressor2.getOutletPressure() / subseaCompressor2.getInletPressure());



  }
}

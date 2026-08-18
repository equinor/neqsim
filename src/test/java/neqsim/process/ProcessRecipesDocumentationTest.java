package neqsim.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executable coverage for the public APIs used by the process-recipes cookbook. */
class ProcessRecipesDocumentationTest {
  private Stream createFeed(String name) {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    return feed;
  }

  @Test
  void streamSeparatorCompressorAndCoolerRecipesExecute() {
    Stream feed = createFeed("Feed");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("K-100", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    Cooler cooler = new Cooler("E-100", compressor.getOutletStream());
    cooler.setOutletTemperature(40.0, "C");
    process.add(cooler);
    process.run();

    assertTrue(compressor.getPower("kW") > 0.0);
    assertEquals(100.0, compressor.getOutletStream().getPressure("bara"), 1.0e-6);
    assertEquals(40.0, cooler.getOutletStream().getTemperature("C"), 1.0e-6);
  }

  @Test
  void polytropicHeaterExpanderAndHeatExchangerRecipesExecute() {
    Stream feed = createFeed("Feed");

    Compressor compressor = new Compressor("Polytropic compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.80);
    compressor.run();
    assertTrue(compressor.getPower("kW") > 0.0);

    Heater heater = new Heater("Heater", feed);
    heater.setOutletTemperature(80.0, "C");
    heater.run();
    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1.0e-6);

    Expander expander = new Expander("Turbo-Expander", feed);
    expander.setOutletPressure(20.0, "bara");
    expander.setIsentropicEfficiency(0.85);
    expander.run();
    assertTrue(expander.getPower("kW") < 0.0);

    Stream hotStream = createFeed("Hot feed");
    hotStream.setTemperature(120.0, "C");
    Stream coldStream = createFeed("Cold feed");
    coldStream.setTemperature(20.0, "C");
    HeatExchanger heatExchanger = new HeatExchanger("E-102", hotStream, coldStream);
    heatExchanger.setUAvalue(5000.0);
    heatExchanger.run();

    assertTrue(Double.isFinite(heatExchanger.getDuty()));
    assertTrue(heatExchanger.getOutStream(0).getTemperature("C") < 120.0);
    assertTrue(heatExchanger.getOutStream(1).getTemperature("C") > 20.0);
  }

  @Test
  void valveRecycleAndAdjusterConfigurationUsesCurrentApis() {
    Stream feed = createFeed("Feed");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
    valve.setOutletPressure(25.0, "bara");
    valve.setPercentValveOpening(100.0);
    ValveMechanicalDesign design = (ValveMechanicalDesign) valve.getMechanicalDesign();
    design.setValveSizingStandard("IEC 60534");
    design.getValveSizingMethod().setxT(0.75);
    valve.run();
    valve.calcKv();
    assertTrue(valve.getKv() > 0.0);

    for (String standard : new String[] {"IEC 60534", "IEC 60534 full", "prod choke", "Sachdeva", "Gilbert",
        "Baxendell", "Ros", "Achong"}) {
      design.setValveSizingStandard(standard);
      assertEquals(standard, design.getValveSizingStandard());
    }

    Stream recycleSource = createFeed("Recycle source");
    Stream recycleTear = createFeed("Recycle tear");
    Recycle recycle = new Recycle("Recycle");
    recycle.addStream(recycleSource);
    recycle.setOutletStream(recycleTear);
    recycle.setTolerance(1.0e-6);
    assertNotNull(recycle.getOutletStream());

    Adjuster adjuster = new Adjuster("Adjust feed flow");
    adjuster.setAdjustedVariable(feed, "flow", "kg/hr");
    adjuster.setTargetVariable(feed, "gasVolumeFlow", feed.getFlowRate("Am3/hr"), "Am3/hr");
    adjuster.setMinAdjustedValue(1.0);
    adjuster.setMaxAdjustedValue(100000.0);
    adjuster.setTolerance(1.0e-4);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(adjuster);
    process.run();
    assertTrue(adjuster.solved());
  }
}

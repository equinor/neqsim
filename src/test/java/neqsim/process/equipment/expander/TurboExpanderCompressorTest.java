package neqsim.process.equipment.expander;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

public class TurboExpanderCompressorTest {
  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 0.006);
    feedGas.addComponent("CO2", 0.014);
    feedGas.addComponent("methane", 0.862);
    feedGas.addComponent("ethane", 0.08);
    feedGas.addComponent("propane", 0.03);
    feedGas.addComponent("i-butane", 0.0024);
    feedGas.addComponent("n-butane", 0.004);
    feedGas.addComponent("n-hexane", 0.0015);
    feedGas.setMixingRule(2);
    feedGas.init(0);

    Stream feedStream = new Stream("dry feed gas Smorbukk", feedGas);
    feedStream.setFlowRate(456000.0, "kg/hr");
    feedStream.setTemperature(-23.0, "C");
    feedStream.setPressure(60.95, "bara");
    feedStream.run();

    Stream feedStream2 = new Stream("dry feed gas Smorbukk2", feedGas.clone());
    feedStream2.setFlowRate(423448.0, "kg/hr");
    feedStream2.setTemperature(17.0, "C");
    feedStream2.setPressure(42.0, "bara");
    feedStream2.run();

    TurboExpanderCompressor turboExpander =
        new TurboExpanderCompressor("TurboExpander", feedStream);
    turboExpander.setCompressorFeedStream(feedStream2);
    /*
     * double designSpeed = 6850.0; // rpm double designIsentropicEfficiency = 0.88; double designUC
     * = 0.7; double bearingloww = 0.25; double impeller_diamater = 0.424; double
     * compressor_polytropicEfficiency = 0.85; double compressor_polytropichead = 20.47; // kJ/kg
     * double qn_design = 0.0; // kJ/kg turboExpander.setDesignParameters(designSpeed,
     * designIsentropicEfficiency, designUC, bearingloww, impeller_diamater,
     * compressor_polytropicEfficiency, compressor_polytropichead, qn_design);
     */

    turboExpander.setUCcurve(
        new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
            0.9552460269880922, 1.0},
        new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
            0.9943181818181818, 1.0});

    turboExpander.setQNEfficiencycurve(new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
        new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});

    turboExpander.setQNHeadcurve(new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
        new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});

    turboExpander.setImpellerDiameter(0.424); // m
    turboExpander.setDesignSpeed(6850.0); // rpm
    turboExpander.setExpanderDesignIsentropicEfficiency(0.88);
    turboExpander.setDesignUC(0.7); // m/s
    turboExpander.setDesignQn(0.03328);
    turboExpander.setExpanderOutPressure(42.0);
    turboExpander.setCompressorDesignPolytropicEfficiency(0.81);
    turboExpander.setCompressorDesignPolytropicHead(20.47);//// kJ/kg
    turboExpander.setMaximumIGVArea(1.637e4); // mm2
    turboExpander.setCompressorDesignPolytropicHead(20.47);

    turboExpander.run();
    turboExpander.toJson();

    Stream outStream = new Stream("outstream", turboExpander.getOutletStream());
    outStream.run();

    Assertions.assertEquals(49.7730176,
        turboExpander.getCompressorOutletStream().getPressure("bara"), 1e-2);

    Assertions.assertEquals(6614.447912, turboExpander.getSpeed(), 1e-2);
    Assertions.assertEquals(3001861.63337, turboExpander.getPowerExpander(), 1e-2);
    Assertions.assertEquals(3.00186163337, turboExpander.getPowerExpander("MW"), 1e-6);
    Assertions.assertEquals(3001852.309290, turboExpander.getPowerCompressor(), 1e-2);
    Assertions.assertEquals(3.00185230929, turboExpander.getPowerCompressor("MW"), 1e-6);
    Assertions.assertEquals(0.7748200353, turboExpander.getCompressorPolytropicEfficiency(), 1e-2);
    Assertions.assertEquals(0.83552030, turboExpander.getExpanderIsentropicEfficiency(), 1e-2);
    Assertions.assertEquals(19.774925264, turboExpander.getCompressorPolytropicHead(), 1e-2);
    Assertions.assertEquals(0.88071122465, turboExpander.getUCratioexpander(), 1e-2);
    Assertions.assertEquals(0.869368201, turboExpander.getQNratiocompressor(), 1e-2);
    Assertions.assertEquals(-40.8000256,
        turboExpander.getExpanderOutletStream().getTemperature("C"), 1e-2);
  }

  @Test
  void testRun2() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 0.006);
    feedGas.addComponent("CO2", 0.014);
    feedGas.addComponent("methane", 0.862);
    feedGas.addComponent("ethane", 0.08);
    feedGas.addComponent("propane", 0.03);
    feedGas.addComponent("i-butane", 0.0024);
    feedGas.addComponent("n-butane", 0.004);
    feedGas.addComponent("n-hexane", 0.0015);
    feedGas.setMixingRule(2);
    feedGas.init(0);

    Stream feedStream = new Stream("dry feed gas Smorbukk", feedGas);
    feedStream.setFlowRate(456000.0, "kg/hr");
    feedStream.setTemperature(-23.0, "C");
    feedStream.setPressure(60.95, "bara");
    feedStream.run();

    TurboExpanderCompressor turboExpander =
        new TurboExpanderCompressor("TurboExpander", feedStream);
    /*
     * double designSpeed = 6850.0; // rpm double designIsentropicEfficiency = 0.88; double designUC
     * = 0.7; double bearingloww = 0.25; double impeller_diamater = 0.424; double
     * compressor_polytropicEfficiency = 0.85; double compressor_polytropichead = 20.47; // kJ/kg
     * double qn_design = 0.0; // kJ/kg turboExpander.setDesignParameters(designSpeed,
     * designIsentropicEfficiency, designUC, bearingloww, impeller_diamater,
     * compressor_polytropicEfficiency, compressor_polytropichead, qn_design);
     */

    turboExpander.setUCcurve(
        new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
            0.9552460269880922, 1.0},
        new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
            0.9943181818181818, 1.0});

    turboExpander.setQNEfficiencycurve(new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
        new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});

    turboExpander.setQNHeadcurve(new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
        new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});

    turboExpander.setImpellerDiameter(0.424); // m
    turboExpander.setDesignSpeed(6850.0); // rpm
    turboExpander.setExpanderDesignIsentropicEfficiency(0.88);
    turboExpander.setDesignUC(0.7); // m/s
    turboExpander.setDesignQn(0.03328);
    turboExpander.setExpanderOutPressure(42.0);
    turboExpander.setCompressorDesignPolytropicEfficiency(0.81);
    turboExpander.setCompressorDesignPolytropicHead(20.47);//// kJ/kg
    turboExpander.setMaximumIGVArea(1.637e4); // mm2
    turboExpander.setCompressorDesignPolytropicHead(20.47);

    turboExpander.run(UUID.randomUUID());

    Separator separator = new Separator("separator", turboExpander.getExpanderOutletStream());
    separator.run();

    Heater heater = new Heater("heater", separator.getGasOutStream());
    heater.setOutTemperature(17.0, "C");
    heater.run();

    turboExpander.setCompressorFeedStream(heater.getOutStream());
    turboExpander.run();

    ProcessSystem mode1 = new ProcessSystem();
    mode1.add(feedStream);
    mode1.add(turboExpander);
    mode1.add(separator);
    mode1.add(heater);

    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();

    Stream outStream = new Stream("outstream", turboExpander.getOutletStream());
    outStream.run();

    Assertions.assertEquals(49.60010, turboExpander.getCompressorOutletStream().getPressure("bara"),
        1e-2);

    Assertions.assertEquals(6707.09019, turboExpander.getSpeed(), 1e-2);
    Assertions.assertEquals(3033276.91507, turboExpander.getPowerExpander(), 1);
    Assertions.assertEquals(3033267.3279, turboExpander.getPowerCompressor(), 1);
    Assertions.assertEquals(0.78230968663, turboExpander.getCompressorPolytropicEfficiency(), 1e-2);
    Assertions.assertEquals(0.84505046543, turboExpander.getExpanderIsentropicEfficiency(), 1e-2);
    Assertions.assertEquals(20.2290325, turboExpander.getCompressorPolytropicHead(), 1e-2);
    Assertions.assertEquals(0.893046, turboExpander.getUCratioexpander(), 1e-2);
    Assertions.assertEquals(0.8924806818, turboExpander.getQNratiocompressor(), 1e-2);
    Assertions.assertEquals(6707.09019, turboExpander.getSpeed(), 1e-2);
    Assertions.assertEquals(-40.858226753,
        turboExpander.getExpanderOutletStream().getTemperature("C"), 1e-2);
  }

  @Test
  void testRun3() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 0.006);
    feedGas.addComponent("CO2", 0.014);
    feedGas.addComponent("methane", 0.862);
    feedGas.addComponent("ethane", 0.08);
    feedGas.addComponent("propane", 0.03);
    feedGas.addComponent("i-butane", 0.0024);
    feedGas.addComponent("n-butane", 0.004);
    feedGas.addComponent("n-hexane", 0.0015);
    feedGas.setMixingRule(2);
    feedGas.init(0);

    Stream feedStream = new Stream("dry feed gas Smorbukk", feedGas);
    feedStream.setFlowRate(426000.0, "kg/hr");
    feedStream.setTemperature(-23.0, "C");
    feedStream.setPressure(55.95, "bara");
    feedStream.run();

    TurboExpanderCompressor turboExpander =
        new TurboExpanderCompressor("TurboExpander", feedStream);
    /*
     * double designSpeed = 6850.0; // rpm double designIsentropicEfficiency = 0.88; double designUC
     * = 0.7; double bearingloww = 0.25; double impeller_diamater = 0.424; double
     * compressor_polytropicEfficiency = 0.85; double compressor_polytropichead = 20.47; // kJ/kg
     * double qn_design = 0.0; // kJ/kg turboExpander.setDesignParameters(designSpeed,
     * designIsentropicEfficiency, designUC, bearingloww, impeller_diamater,
     * compressor_polytropicEfficiency, compressor_polytropichead, qn_design);
     */

    turboExpander.setUCcurve(
        new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
            0.9552460269880922, 1.0},
        new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
            0.9943181818181818, 1.0});

    turboExpander.setQNEfficiencycurve(new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
        new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});

    turboExpander.setQNHeadcurve(new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
        new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});

    turboExpander.setImpellerDiameter(0.424); // m
    turboExpander.setDesignSpeed(6850.0); // rpm
    turboExpander.setExpanderDesignIsentropicEfficiency(0.88);
    turboExpander.setDesignUC(0.7); // m/s
    turboExpander.setDesignQn(0.03328);
    turboExpander.setExpanderOutPressure(42.0);
    turboExpander.setCompressorDesignPolytropicEfficiency(0.81);
    turboExpander.setCompressorDesignPolytropicHead(20.47);//// kJ/kg
    turboExpander.setMaximumIGVArea(1.637e4); // mm2
    turboExpander.setCompressorDesignPolytropicHead(20.47);

    turboExpander.run(UUID.randomUUID());

    Separator separator = new Separator("separator", turboExpander.getExpanderOutletStream());
    separator.run();

    Heater heater = new Heater("heater", separator.getGasOutStream());
    heater.setOutTemperature(17.0, "C");
    heater.run();

    turboExpander.setCompressorFeedStream(heater.getOutStream());
    turboExpander.run();

    ProcessSystem mode1 = new ProcessSystem();
    mode1.add(feedStream);
    mode1.add(turboExpander);
    mode1.add(separator);
    mode1.add(heater);

    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();
    mode1.run();

    Stream outStream = new Stream("outstream", turboExpander.getOutletStream());
    outStream.run();
    Assertions.assertEquals(6043.804353, turboExpander.getSpeed(), 1e-2);
    Assertions.assertEquals(48.0958031,
        turboExpander.getCompressorOutletStream().getPressure("bara"), 1e-2);
    Assertions.assertEquals(2281735.34701, turboExpander.getPowerExpander(), 1);
    Assertions.assertEquals(2281727.56235, turboExpander.getPowerCompressor(), 1);
    Assertions.assertEquals(0.79709017011, turboExpander.getCompressorPolytropicEfficiency(), 1e-2);

    Assertions.assertEquals(-37.03361130,
        turboExpander.getExpanderOutletStream().getTemperature("C"), 1e-2);
  }

  @Test
  void testMassBalance() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 50.0);
    feedGas.addComponent("methane", 0.95);
    feedGas.addComponent("ethane", 0.05);
    feedGas.setMixingRule(2);
    feedGas.init(0);

    Stream expanderFeed = new Stream("expander feed", feedGas);
    expanderFeed.setFlowRate(10000.0, "kg/hr");
    expanderFeed.setTemperature(20.0, "C");
    expanderFeed.setPressure(50.0, "bara");
    expanderFeed.run();

    Stream compressorFeed = new Stream("compressor feed", feedGas.clone());
    compressorFeed.setFlowRate(7500.0, "kg/hr");
    compressorFeed.setTemperature(5.0, "C");
    compressorFeed.setPressure(30.0, "bara");
    compressorFeed.run();

    TurboExpanderCompressor turboExpander =
        new TurboExpanderCompressor("TurboExpander", expanderFeed);
    turboExpander.setCompressorFeedStream(compressorFeed);
    turboExpander.setExpanderOutPressure(25.0);
    turboExpander.setCompressorDesignPolytropicEfficiency(0.8);
    turboExpander.setCompressorDesignPolytropicHead(10.0);

    turboExpander.run();

    double expanderInletFlow = turboExpander.getInletStream().getFlowRate("kg/hr");
    double expanderOutletFlow = turboExpander.getExpanderOutletStream().getFlowRate("kg/hr");
    double compressorInletFlow = turboExpander.getCompressorFeedStream().getFlowRate("kg/hr");
    double compressorOutletFlow = turboExpander.getCompressorOutletStream().getFlowRate("kg/hr");

    Assertions.assertEquals(expanderInletFlow, expanderOutletFlow, 1e-6);
    Assertions.assertEquals(compressorInletFlow, compressorOutletFlow, 1e-6);
    Assertions.assertEquals(0.0, turboExpander.getMassBalance("kg/hr"), 1e-6);
  }
}

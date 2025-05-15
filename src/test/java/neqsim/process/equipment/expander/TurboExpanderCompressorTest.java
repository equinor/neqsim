package neqsim.process.equipment.expander;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TurboExpanderCompressorTest {
  @Test
  void testRun() {
    System.out.println("Turtall etter konvergens: ");
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

turboExpander.setUCcurve([0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821, 0.9552460269880922, 1.0],
 :[0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364, 0.9943181818181818, 1.0]);

 
 turboExpander.setQNEfficiencycurve([0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6],
[0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6] );

turboExpander.setQNHeadcurve([0.5, 0.8, 1.0, 1.2, 1.4, 1.6],
[1.1, 1.05, 1.0, 0.9, 0.7, 0.4]);


turboExpander.setImpellerDiameter(0.424); // m
    turboExpander.setDesignSpeed(6850.0); // rpm
    turboExpander.setDesignIsentropicEfficiency(0.88);
    turboExpander.setDesignUC(0.7); // m/s
    turboExpander.setDesignQn(0.03328); 
    turboExpander.setExpanderOutPressure(42.0, "bara");
    turboExpander.setComprosserPolytropicEfficieny(0.81); 
    turboExpander.setCompressorDesingPolytropicHead(20.47, "kJ/kg")// kJ/kg
    turboExpander.setMaximumIGVArea(1.637e4); //mm2
\
    //turboExpander.setIGVposition(0.8); // 80%
    turboExpander.setExpanderOutPressure(45.0); // bar
    turboExpander.run(UUID.randomUUID());

    System.out.println("Turtall etter konvergens: " + turboExpander.getTurboSpeed());
  }
}

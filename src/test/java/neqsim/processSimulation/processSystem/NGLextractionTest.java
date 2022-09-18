package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.expander.Expander;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

public class NGLextractionTest {

  @Test
  public void runNGLProcessTest() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 0.0, 110.0);
        feedGas.addComponent("nitrogen", 6.10936E-3);
        feedGas.addComponent("CO2", 1.04911E-2);
        feedGas.addComponent("methane", 8.62456E-1);
        feedGas.addComponent("ethane", 7.571E-2 );
        feedGas.addComponent("propane", 3.27257E-2);
        feedGas.addComponent("i-butane", 3.83674E-3);
        feedGas.addComponent("n-butane", 5.39893E-3);
        feedGas.addComponent("i-pentane", 1.02107E-3);
        feedGas.addComponent("n-pentane", 8.41404E-4);
        feedGas.addComponent("n-hexane", 8.89968E-4);
        feedGas.addComponent("n-heptane", 2.63703E-4);
        feedGas.addComponent("n-octane", 7.87692E-5 );
        feedGas.addComponent("n-nonane", 2.23709E-5);
        feedGas.addComponent("nC10", 7.76741E-5);
        feedGas.setMixingRule(2);

        Stream feedGasStream = new Stream(feedGas);
        ThrottlingValve valve = new ThrottlingValve(feedGasStream);

        HeatExchanger hx1 = new HeatExchanger(valve.getOutletStream());
        hx1.setGuessOutTemperature(273.15 - 30.0);
        hx1.setUAvalue(1000.0);

        Expander exp1 = new Expander(hx1.getOutStream(0));
        exp1.setOutletPressure(45.0, "bara");

        Separator sep1 = new Separator(exp1.getOutletStream());

        Stream resGasStream = new Stream(exp1.getOutletStream().clone());

        Recycle res1 = new Recycle();
        res1.addStream(sep1.getGasOutStream());
        res1.setOutletStream(resGasStream);

        hx1.setFeedStream(1, resGasStream);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(feedGasStream);
        operations.add(hx1);
        operations.add(exp1);
        operations.add(sep1);
        operations.add(res1);
        operations.run();
        double outPressure = hx1.getOutStream(1).getPressure("bara");
        assertEquals(45.0, outPressure);

  }
}

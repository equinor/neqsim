package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OilGasProcessTest extends neqsim.NeqSimTest {
  @Test
  public void runProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("water", 51.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setMolarComposition(new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154,
        0.050908, 0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355,
        0.007658, 0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});
    // thermoSystem.prettyPrint();

    Stream feedStream = new Stream("feed stream", thermoSystem);
    feedStream.setFlowRate(604094, "kg/hr");
    feedStream.setTemperature(25.5, "C");
    feedStream.setPressure(26.0, "bara");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator1stStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "1st stage separator", feedStream);

    ThrottlingValve valve1 = new ThrottlingValve("valve1", seprator1stStage.getLiquidOutStream());
    valve1.setOutletPressure(19.0);

    Heater oilHeater = new Heater("oil heater", valve1.getOutletStream());
    oilHeater.setOutTemperature(359.0);

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator2ndStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "2nd stage separator", oilHeater.getOutletStream());

    ThrottlingValve valve2 = new ThrottlingValve("valve2", seprator2ndStage.getLiquidOutStream());
    valve2.setOutletPressure(2.7);

    StreamInterface recircstream1 = valve2.getOutletStream().clone();
    recircstream1.setName("oilRecirc1");
    recircstream1.setFlowRate(1e-6, "kg/hr");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator seprator3rdStage =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "3rd stage separator");
    seprator3rdStage.addStream(valve2.getOutletStream());
    seprator3rdStage.addStream(recircstream1);

    ThrottlingValve pipeloss1st =
        new ThrottlingValve("pipeloss1st", seprator3rdStage.getGasOutStream());
    pipeloss1st.setOutletPressure(2.7 - 0.03);

    Heater coolerLP = new Heater("cooler LP", pipeloss1st.getOutletStream());
    coolerLP.setOutTemperature(273.15 + 25.0);

    Separator sepregenGas = new Separator("sepregenGas", coolerLP.getOutletStream());

    Pump oil1pump = new Pump("oil1pump", sepregenGas.getLiquidOutStream());
    oil1pump.setOutletPressure(19.);

    ThrottlingValve valveLP1 = new ThrottlingValve("valvseLP1", oil1pump.getOutletStream());
    valveLP1.setOutletPressure(2.7);

    Recycle recycle1 = new Recycle("oil recirc 1");
    recycle1.addStream(valveLP1.getOutletStream());
    recycle1.setOutletStream(recircstream1);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(feedStream);
    operations.add(seprator1stStage);
    operations.add(valve1);
    operations.add(oilHeater);
    operations.add(seprator2ndStage);
    operations.add(valve2);
    operations.add(recircstream1);
    operations.add(seprator3rdStage);
    operations.add(pipeloss1st);
    operations.add(coolerLP);
    operations.add(sepregenGas);
    operations.add(oil1pump);
    operations.add(valveLP1);
    operations.add(recycle1);

    operations.run();

    assertEquals(17105.52983567356, seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"), 1.1);

    assertEquals(seprator3rdStage.getGasOutStream().getFlowRate("kg/hr"),
        coolerLP.getOutletStream().getFlowRate("kg/hr"), 1e-4);

    // System.out.println("recycle flow " + recycle1.getOutletStream().getFlowRate("kg/hr"));
    // valveLP1.getOutletStream().getFluid().prettyPrint();
  }

  @Test
  public void runAntiSurgeProcess() throws InterruptedException {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 1.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.setMixingRule("classic");

    Stream gas_from_separator = new Stream("feed stream", thermoSystem);
    gas_from_separator.setPressure(55.0, "bara");
    gas_from_separator.setTemperature(30.0, "C");
    gas_from_separator.setFlowRate(7.0, "MSm3/day");
    gas_from_separator.run();

    Stream recyclegasstream = gas_from_separator.clone();
    recyclegasstream.setFlowRate(1.2, "MSm3/day");
    recyclegasstream.run();

    Mixer gasmixer = new Mixer("gas mixer");
    gasmixer.addStream(gas_from_separator);
    gasmixer.addStream(recyclegasstream);
    gasmixer.run();

    Compressor gascompressor = new Compressor("gas compressor");
    gascompressor.setInletStream(gasmixer.getOutletStream());
    gascompressor.setOutletPressure(90.0, "bara");
    gascompressor.run();
    gasmixer.run();

    Cooler gascooler = new Cooler("gas cooler");
    gascooler.setInletStream(gascompressor.getOutletStream());
    gascooler.setOutTemperature(30.0, "C");
    gascooler.run();

    Separator gassep = new Separator("gas separator");
    gassep.setInletStream(gascooler.getOutletStream());
    gassep.run();

    Splitter gassplitter = new Splitter("gas splitter");
    gassplitter.setInletStream(gassep.getGasOutStream());
    gassplitter.setFlowRates(new double[] {7.0, 1.2}, "MSm3/day");
    gassplitter.run();


    ThrottlingValve antisurgevalve = new ThrottlingValve("gas valve");
    antisurgevalve.setInletStream(gassplitter.getSplitStream(1));
    antisurgevalve.setOutletPressure(55.0, "bara");
    antisurgevalve.run();

    Recycle recycl = new Recycle("rec");
    recycl.addStream(antisurgevalve.getOutletStream());
    recycl.setOutletStream(recyclegasstream);
    recycl.run();

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(gas_from_separator);
    operations.add(recyclegasstream);
    operations.add(gasmixer);
    operations.add(gascooler);
    operations.add(gassep);
    operations.add(gassplitter);
    operations.add(antisurgevalve);
    operations.add(recycl);
    operations.run();

    assertEquals(6.9999999, gassplitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-4);
    assertEquals(1.2, gassplitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-4);



  }
}

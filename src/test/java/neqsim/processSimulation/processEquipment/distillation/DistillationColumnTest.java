package neqsim.processSimulation.processEquipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class DistillationColumnTest {

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach

  @Test
  public void DistillationColumnTest() throws Exception {

    neqsim.thermo.system.SystemInterface richTEG =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    richTEG.addComponent("nitrogen", 0.0003884521907420086);
    richTEG.addComponent("CO2", 0.3992611934362681);
    richTEG.addComponent("methane", 0.1707852619527612);
    richTEG.addComponent("ethane", 0.20533172990208282);
    richTEG.addComponent("propane", 0.28448628224749795);
    richTEG.addComponent("i-butane", 0.04538593257021818);
    richTEG.addComponent("n-butane", 0.0001078982825);
    richTEG.addComponent("i-pentane", 0.0008015009931573362);
    richTEG.addComponent("n-pentane", 0.00007597175884128077);
    richTEG.addComponent("n-hexane", 0.0000735238469338);
    richTEG.addComponent("n-heptane", 0.0);
    richTEG.addComponent("nC8", 0.0);
    richTEG.addComponent("nC9", 0.0);
    richTEG.addComponent("benzene", 0.001);
    richTEG.addComponent("water", 9.281170624865437);
    richTEG.addComponent("TEG", 88.61393191277175);
    richTEG.setMixingRule(10);
    richTEG.setMultiPhaseCheck(false);
    richTEG.init(0);

    neqsim.thermo.system.SystemInterface gasToReboiler =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    gasToReboiler.addComponent("nitrogen", 0.007104922868929818);
    gasToReboiler.addComponent("CO2", 4.944830745821265);
    gasToReboiler.addComponent("methane", 3.013439464714221);
    gasToReboiler.addComponent("ethane", 3.1119159322353815);
    gasToReboiler.addComponent("propane", 4.001381171330917);
    gasToReboiler.addComponent("i-butane", 0.6934008192075206);
    gasToReboiler.addComponent("n-butane", 0.684816349773283);
    gasToReboiler.addComponent("i-pentane", 0.24185783393270);
    gasToReboiler.addComponent("n-pentane", 0.0032322868124);
    gasToReboiler.addComponent("n-hexane", 0.0002651);
    gasToReboiler.addComponent("n-heptane", 0.0);
    gasToReboiler.addComponent("nC8", 0.0);
    gasToReboiler.addComponent("nC9", 0.0);
    gasToReboiler.addComponent("benzene", 0.000);
    gasToReboiler.addComponent("water", 63.419578687948665);
    gasToReboiler.addComponent("TEG", 4.293253985703371);
    gasToReboiler.setMixingRule(10);
    gasToReboiler.setMultiPhaseCheck(false);
    gasToReboiler.init(0);

    Stream richTEGStream = new Stream("richTEGS", richTEG);
    richTEGStream.setFlowRate(9400.0, "kg/hr");
    richTEGStream.setTemperature(100, "C");
    richTEGStream.setPressure(1.12, "bara");
    richTEGStream.run();

    Stream gasToReboilerStream = new Stream("gasToReboilerS", gasToReboiler);
    gasToReboilerStream.setFlowRate(290, "kg/hr");
    gasToReboilerStream.setTemperature(200, "C");
    gasToReboilerStream.setPressure(1.12, "bara");
    gasToReboilerStream.run();

    DistillationColumn column = new DistillationColumn(10, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(richTEGStream, 1);
    column.getReboiler().setOutTemperature(273.15 + 202);
    // column.getCondenser().setOutTemperature(273.15 + 95);
    column.getCondenser().setHeatInput(-30000.0);
    column.getTray(1).addStream(gasToReboilerStream);
    column.setTopPressure(1.12);
    column.setBottomPressure(1.12);
    column.setInternalDiameter(0.56);
    // while (!column.solved()) {
    column.run();
    // }

    double waterFlowRateInColumn =
        richTEGStream.getFluid().getPhase(0).getComponent("water").getFlowRate("kg/hr")
            + richTEGStream.getFluid().getPhase(1).getComponent("water").getFlowRate("kg/hr");
    double waterFlowRateInColumn2 = richTEGStream.getFluid().getComponent("water").getMolarMass()
        * richTEGStream.getFluid().getFlowRate("mole/hr")
        * richTEGStream.getFluid().getComponent("water").getz();
    assertEquals(waterFlowRateInColumn, waterFlowRateInColumn2, 0.00001);

    double waterFlowRateInColumnGasToReb = gasToReboilerStream.getFluid().getFlowRate("mole/hr")
        * gasToReboilerStream.getFluid().getComponent("water").getMolarMass()
        * gasToReboilerStream.getFluid().getComponent("water").getz();
    double waterFlowRateOutColumn = column.getGasOutStream().getFluid().getFlowRate("mole/hr")
        * column.getGasOutStream().getFluid().getComponent("water").getMolarMass()
        * column.getGasOutStream().getFluid().getComponent("water").getz();
    double waterFlowRateOutColumnLeanTEG =
        column.getLiquidOutStream().getFluid().getFlowRate("mole/hr")
            * column.getLiquidOutStream().getFluid().getComponent("water").getMolarMass()
            * column.getLiquidOutStream().getFluid().getComponent("water").getz();


    double totalWaterIn = waterFlowRateInColumn2 + waterFlowRateInColumnGasToReb;
    double totalWaterOut = waterFlowRateOutColumn + waterFlowRateOutColumnLeanTEG;

    System.out.println("Column in is " + totalWaterIn + " kg/hr");
    System.out.println("Column out is " + totalWaterOut + " kg/hr");
    System.out.println("Column is solved  " + column.solved());


    assertEquals(totalWaterIn, totalWaterOut, 0.1);

    System.out.println("Calc Water Flow rate via fluid component " + waterFlowRateInColumn);
    System.out.println("Calc Water Flow rate via molar mass and flow rate total "
        + waterFlowRateInColumn2 + " kg/hr");

  }
}

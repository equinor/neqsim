package neqsim.processsimulation.processsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.ReservoirTPsim;
import neqsim.process.measurementdevice.MultiPhaseMeter;

public class CombinedOilGasFieldTest {
  @Test
  public void testreservoirTPsim() {
    neqsim.thermo.system.SystemInterface wellFluid =
        new neqsim.thermo.system.SystemPrEos(273.15 + 30.0, 65.00);
    wellFluid.addComponent("nitrogen", 0.08);
    wellFluid.addComponent("CO2", 3.56);
    wellFluid.addComponent("methane", 87.36);
    wellFluid.addComponent("ethane", 4.02);
    wellFluid.addComponent("propane", 1.54);
    wellFluid.addComponent("i-butane", 0.2);
    wellFluid.addComponent("n-butane", 0.42);
    wellFluid.addComponent("i-pentane", 0.15);
    wellFluid.addComponent("n-pentane", 0.20);

    wellFluid.addTBPfraction("C6_Oil", 0.24, 84.99 / 1000.0, 695.0 / 1000.0);
    wellFluid.addTBPfraction("C7_Oil", 0.34, 97.87 / 1000.0, 718.0 / 1000.0);
    wellFluid.addTBPfraction("C8_Oil", 0.33, 111.54 / 1000.0, 729.0 / 1000.0);
    wellFluid.addTBPfraction("C9_Oil", 0.19, 126.1 / 1000.0, 749.0 / 1000.0);
    wellFluid.addTBPfraction("C10_Oil", 0.15, 140.14 / 1000.0, 760.0 / 1000.0);
    wellFluid.addTBPfraction("C11_Oil", 0.69, 175.0 / 1000.0, 830.0 / 1000.0);
    wellFluid.addTBPfraction("C12_Oil", 0.5, 280.0 / 1000.0, 914.0 / 1000.0);
    wellFluid.addTBPfraction("C13_Oil", 0.103, 560.0 / 1000.0, 980.0 / 1000.0);

    wellFluid.addTBPfraction("C6_Gas", 0.0, 84.0 / 1000.0, 684.0 / 1000.0);
    wellFluid.addTBPfraction("C7_Gas", 0.0, 97.9 / 1000.0, 742.0 / 1000.0);
    wellFluid.addTBPfraction("C8_Gas", 0.0, 111.5 / 1000.0, 770.0 / 1000.0);
    wellFluid.addTBPfraction("C9_Gas", 0.0, 126.1 / 1000.0, 790.0 / 1000.0);
    wellFluid.addTBPfraction("C10_Gas", 0.0, 140.14 / 1000.0, 805.0 / 1000.0);
    wellFluid.addTBPfraction("C11_Gas", 0.0, 175.0 / 1000.0, 815.0 / 1000.0);
    wellFluid.addTBPfraction("C12_Gas", 0.0, 280.0 / 1000.0, 835.0 / 1000.0);
    wellFluid.addTBPfraction("C13_Gas", 0.0, 450.0 / 1000.0, 850.0 / 1000.0);

    wellFluid.setMixingRule("classic");
    wellFluid.init(0);

    neqsim.thermo.system.SystemInterface wellFluidGasWell =
        (neqsim.thermo.system.SystemInterface) wellFluid.clone();
    wellFluidGasWell.setMolarComposition(
        new double[] {0.108, 3.379, 85.915, 4.250, 1.719, 0.275, 0.549, 0.201, 0.256, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.24, 0.34, 0.33, 0.19, 0.15, 0.69, 0.5, 0.03});

    ReservoirTPsim reservoirGasTPsim = new ReservoirTPsim("TPreservoir", wellFluidGasWell);
    reservoirGasTPsim.setTemperature(150.0, "C");
    reservoirGasTPsim.setPressure(250.0, "bara");
    reservoirGasTPsim.setFlowRate(50000.0, "kg/hr");
    reservoirGasTPsim.setProdPhaseName("gas");

    MultiPhaseMeter MPFMgas = new MultiPhaseMeter("Gas MPFM", reservoirGasTPsim.getOutStream());
    MPFMgas.setTemperature(60.0, "C");
    MPFMgas.setPressure(80.0, "bara");

    // ReservoirCVDsim reservoirCVD = new ReservoirCVDsim();
    // ReservoirDiffLibsim reservoirDiffLib = new ReservoirDiffLibsim();
    /*
     * neqsim.thermo.system.SystemInterface wellFluidOilWell =
     * (neqsim.thermo.system.SystemInterface) wellFluid.clone();
     * wellFluidOilWell.setMolarComposition( new double[] {0.047, 0.191, 39.022, 0.25, 0.053, 0.017,
     * 0.022, 0.021, 0.015, 0.057, 0.176, 0.181, 0.177, 0.81, 15.353, 30.738, 12.869, 0.0, 0.0, 0.0,
     * 0.0, 0.0, 0.0, 0.0, 0.0});
     *
     * Stream wellStreamOil = new Stream("Well Stream Oil Well", wellFluidOilWell);
     * wellStreamOil.setFlowRate(50000.0, "kg/hr"); wellStreamOil.setTemperature(100.0, "C");
     * wellStreamOil.setPressure(100.0, "bara");
     */

    // MultiPhaseMeter MPFMoil = new MultiPhaseMeter("Oil MPFM", wellStreamOil);
    // MPFMoil.setTemperature(60.0, "C");
    // MPFMoil.setPressure(20.0, "bara");

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(reservoirGasTPsim);
    operations.add(MPFMgas);
    operations.run();
    assertEquals(8834.875493073961, MPFMgas.getMeasuredValue("GOR_std", ""), 1.0);

    reservoirGasTPsim.setPressure(150.0, "bara");
    operations.run();
    assertEquals(14937.606339690177
    , MPFMgas.getMeasuredValue("GOR_std", ""), 1.0);
  }
}

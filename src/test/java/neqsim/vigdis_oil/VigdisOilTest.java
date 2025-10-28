package neqsim.vigdis_oil;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.process.equipment.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class VigdisOilTest {

  private SystemInterface inletFluid;

  @BeforeEach
  void setUp() {
    inletFluid = new SystemPrEos(273.15 + 15.0, 1.01325);
  }

  @Test
  void testFluidBuilding() {
    inletFluid.addComponent("H2O", 0.931778049);
    inletFluid.addComponent("nitrogen", 0.000136466);
    inletFluid.addComponent("CO2", 3.57691E-05);
    inletFluid.addComponent("methane", 0.0022155);
    inletFluid.addComponent("ethane", 0.001013874);
    inletFluid.addComponent("propane", 0.001735168);
    inletFluid.addComponent("i-butane", 0.000414214);
    inletFluid.addComponent("n-butane", 0.001353982);
    inletFluid.addComponent("i-pentane", 0.000710328);
    inletFluid.addComponent("n-pentane", 0.001078754);

    inletFluid.addTBPfraction("Snorre C6*", 0.0, 0.086178001, 0.664);
    inletFluid.addTBPfraction("Snorre C7*", 0.0, 0.096, 0.738);
    inletFluid.addTBPfraction("Snorre C8*", 0.0, 0.107, 0.765);
    inletFluid.addTBPfraction("Snorre C9*", 0.0, 0.121, 0.781);
    inletFluid.addTBPfraction("Snorre C10 - C13*", 0.0, 0.152921005, 0.804099976);
    inletFluid.addTBPfraction("Snorre C14 - C16*", 0.0, 0.205177002, 0.829900024);
    inletFluid.addTBPfraction("Snorre C17 - C20*", 0.0, 0.255292007, 0.849700012);
    inletFluid.addTBPfraction("Snorre C21 - C24*", 0.0, 0.309984985, 0.868400024);
    inletFluid.addTBPfraction("Snorre C25 - C28*", 0.0, 0.365141998, 0.883900024);
    inletFluid.addTBPfraction("Snorre C29 - C34*", 0.0, 0.433957001, 0.899799988);
    inletFluid.addTBPfraction("Snorre C35 - C41*", 0.0, 0.523888, 0.917200012);
    inletFluid.addTBPfraction("Snorre C42 - C52*", 0.0, 0.643973999, 0.936200012);
    inletFluid.addTBPfraction("Snorre C53 - C80*", 0.0, 0.867080017, 0.963400024);

    inletFluid.addTBPfraction("Vigdis C6*", 0.001621162, 0.08618, 0.6615);
    inletFluid.addTBPfraction("Vigdis C7 - C8*", 0.004552747, 0.09907, 0.750400024);
    inletFluid.addTBPfraction("Vigdis C9 - C12*", 0.00897758, 0.138639999, 0.8005);
    inletFluid.addTBPfraction("Vigdis C13 - C18*", 0.014539546, 0.213759995, 0.850200012);
    inletFluid.addTBPfraction("Vigdis C19 - C29*", 0.016563066, 0.333839996, 0.8955);
    inletFluid.addTBPfraction("Vigdis C30+*", 0.013273796, 0.525, 0.939799988);

    inletFluid.addTBPfraction("GFA C7+*", 0.0, 0.089099998, 0.748599976);

    inletFluid.setMixingRule("Classic");
    inletFluid.setMultiPhaseCheck(true);
    inletFluid.useVolumeCorrection(true);

    inletFluid.init(0);

    Stream inletStream = new Stream("vigdis oil inlet stream", inletFluid);
    inletStream.setPressure(33.0, "bara");
    inletStream.setTemperature(65.0, "C");
    inletStream.setFlowRate(508976.578491168, "kg/hr");
    inletStream.run();

    assertTrue(inletStream.getFluid().hasPhaseType("gas"), "Gas phase should be present");
    assertTrue(inletStream.getFluid().hasPhaseType("oil"), "Oil phase should be present");
    assertTrue(inletStream.getFluid().hasPhaseType("aqueous"), "Water phase should be present");
  }
}


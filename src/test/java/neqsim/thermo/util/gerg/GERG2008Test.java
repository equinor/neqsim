package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;

public class GERG2008Test {
  private GERG2008 gerg;

  @BeforeEach
  public void setUp() {
    gerg = new GERG2008();
    gerg.SetupGERG();
  }

  @Test
  public void testMolarMassGERG() {
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    assertEquals(16.04246, Mm.val, 1e-5);
  }

  @Test
  public void testPressureGERG() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    gerg.PressureGERG(T, D, x, P, Z);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
  }

  @Test
  public void testDensityGERG() {
    int iFlag = 0;
    double T = 300.0;
    double P = 1000.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    gerg.DensityGERG(iFlag, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.4077469060672, D.val, 1e-5);
  }

  @Test
  public void testPropertiesGERG() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);
    gerg.PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT, Kappa,
        A);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
    assertTrue(U.val != 0);
    assertTrue(H.val != 0);
    assertTrue(S.val != 0);
    assertTrue(Cv.val != 0);
    assertTrue(Cp.val != 0);
    assertTrue(W.val != 0);
    assertTrue(G.val != 0);
    assertTrue(JT.val != 0);
    assertTrue(Kappa.val != 0);
    assertTrue(A.val != 0);
    assertEquals(0.83232372466, Z.val, 1e-5);
  }

  @Test
  void testVegaCompressor() {
    SystemInterface GERGfluid = new neqsim.thermo.system.SystemGERG2008Eos(298.15, 10.0);

    GERGfluid.addComponent("methane", 0.8);
    GERGfluid.addComponent("ethane", 0.1);
    GERGfluid.addComponent("hydrogen", 0.1);
    GERGfluid.init(0);
    GERGfluid.init(1);

    GERGfluid.setNumberOfPhases(1);
    GERGfluid.setMaxNumberOfPhases(1);
    GERGfluid.setForcePhaseTypes(true);
    GERGfluid.setPhaseType(0, "GAS");
    //GERGfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");

    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);

    SRKfluid.addComponent("methane", 0.8);
    SRKfluid.addComponent("ethane", 0.1);
    SRKfluid.addComponent("hydrogen", 0.1);    SRKfluid.init(0);
    SRKfluid.init(1);

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");
    //SRKfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");

    neqsim.process.equipment.stream.Stream GERGstream =
        new neqsim.process.equipment.stream.Stream("gas", GERGfluid);
        GERGstream.setFlowRate(130.0, "MSm3/day");
        GERGstream.run();

    neqsim.process.equipment.stream.Stream SRKstream =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
    SRKstream.setFlowRate(130.0, "MSm3/day");
    SRKstream.run();

    neqsim.process.equipment.compressor.Compressor GERGcompressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", GERGstream);
        GERGcompressor.setOutletPressure(20.0, "bara");
        GERGcompressor.setPolytropicEfficiency(0.75);
        GERGcompressor.run();

    neqsim.process.equipment.compressor.Compressor SRKcompressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", SRKstream);
    SRKcompressor.setOutletPressure(20.0, "bara");
    SRKcompressor.setPolytropicEfficiency(0.75);
    SRKcompressor.setUseGERG2008(true);
    SRKcompressor.run();

    assertEquals(GERGcompressor.getOutletStream().getTemperature("C"), SRKcompressor.getOutletStream().getTemperature("C"), 1e-8);
    assertEquals(GERGcompressor.getPower("MW"), SRKcompressor.getPower("MW"), 1e-5);
    assertEquals(GERGcompressor.getPolytropicHead(), SRKcompressor.getPolytropicHead(), 1e-5);
  }
}

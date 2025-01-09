package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

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
}

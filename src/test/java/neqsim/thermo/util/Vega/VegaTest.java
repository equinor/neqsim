package neqsim.thermo.util.Vega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

public class VegaTest {
  private Vega Vega;

  @BeforeEach
  void setUp() {
    Vega = new Vega();
    Vega.SetupVega();
  }

  @Test
  void testDensityVega() {
    int iFlag = 0;
    double T = 273.15;
    double P = 1000.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    Vega.DensityVega(iFlag, T, P, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.43802, D.val, 1e-5);
  }

  @Test
  void testPropertiesVega() {
    double T = 273.15;
    int iFlag = 0;
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(30);
    doubleW P = new doubleW(1000.0d);
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

    Vega.DensityVega(iFlag, T, P.val, D, ierr, herr);

    Vega.propertiesVega(T, D.val, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);
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
    assertEquals(1.005253888311987, Z.val, 1e-5);

  }
}

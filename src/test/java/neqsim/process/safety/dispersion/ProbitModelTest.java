package neqsim.process.safety.dispersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ProbitModelTest {

  @Test
  void probitFiveGivesFiftyPercent() {
    // Y=5 → P=0.5
    ProbitModel m = new ProbitModel(5.0, 0.0, 1.0);
    assertEquals(0.5, m.probability(5.0), 1.0e-3);
  }

  @Test
  void h2sProbitMonotonicInDose() {
    ProbitModel h2s = ProbitModel.h2sFatality();
    // Dose D = C^1.43 * t with C in ppm, t in min.
    double dLow = Math.pow(100.0, 1.43) * 10.0;
    double dHigh = Math.pow(500.0, 1.43) * 30.0;
    assertTrue(h2s.probabilityFromDose(dHigh) > h2s.probabilityFromDose(dLow));
  }

  @Test
  void thermalProbitMonotonicInDose() {
    ProbitModel th = ProbitModel.thermalFatality();
    double low = th.probabilityFromDose(60.0 * Math.pow(5000.0, 4.0 / 3.0) / 1.0e4);
    double high = th.probabilityFromDose(60.0 * Math.pow(35000.0, 4.0 / 3.0) / 1.0e4);
    assertTrue(high > low);
  }
}

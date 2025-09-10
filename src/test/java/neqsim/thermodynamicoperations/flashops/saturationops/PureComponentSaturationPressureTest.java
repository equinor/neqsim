package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PureComponentSaturationPressureTest {

  static Stream<String> components() {
    return Stream.of("CO2", "methane", "ethane", "nitrogen", "propane");
  }

  @ParameterizedTest
  @MethodSource("components")
  public void testBubbleAndDewPressureRange(String comp) throws Exception {
    SystemSrkEos sys = new SystemSrkEos(220.0, 1.0);
    sys.addComponent(comp, 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    double tTrip = sys.getPhase(0).getComponent(comp).getTriplePointTemperature();
    double tCrit = sys.getPhase(0).getComponent(comp).getTC();
    for (double t = Math.max(tTrip + 1.0, tTrip * 1.01); t < tCrit - 5.0; t += 5.0) {
      sys.setTemperature(t);
      ops.bubblePointPressureFlash();
      double pBubble = sys.getPressure();
      assertTrue(pBubble > 0 && Double.isFinite(pBubble));
      sys.setTemperature(t);
      ops.dewPointPressureFlash();
      double pDew = sys.getPressure();
      assertTrue(pDew > 0 && Double.isFinite(pDew));
      assertEquals(pBubble, pDew, 1e-6);
    }
  }
}

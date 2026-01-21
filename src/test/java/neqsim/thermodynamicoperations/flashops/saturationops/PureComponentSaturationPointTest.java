package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PureComponentSaturationPointTest {
  static Stream<String> components() {
    return Stream.of("CO2", "methane", "ethane", "nitrogen", "propane");
  }

  @ParameterizedTest
  @MethodSource("components")
  public void testBubbleAndDewPointRange(String comp) throws Exception {
    SystemSrkEos sys = new SystemSrkEos(220.0, 1.0);
    sys.addComponent(comp, 1.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    double pTrip = sys.getPhase(0).getComponent(comp).getTriplePointPressure();
    double pCrit = sys.getPhase(0).getComponent(comp).getPC();
    double tTrip = sys.getPhase(0).getComponent(comp).getTriplePointTemperature();
    double tCrit = sys.getPhase(0).getComponent(comp).getTC();
    for (double p = Math.max(pTrip + 0.1, pTrip * 1.01); p < pCrit - 10.0; p += 5.0) {
      sys.setPressure(p);
      ops.bubblePointTemperatureFlash();
      double tBubble = sys.getTemperature();
      assertTrue(tBubble > tTrip && tBubble < tCrit && Double.isFinite(tBubble));
      sys.setPressure(p);
      ops.dewPointTemperatureFlash();
      double tDew = sys.getTemperature();
      assertTrue(tDew > tTrip && tDew < tCrit && Double.isFinite(tDew));
      assertEquals(tBubble, tDew, 1e-6);
    }
  }
}

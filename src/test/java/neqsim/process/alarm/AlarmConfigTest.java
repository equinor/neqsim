package neqsim.process.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmConfigTest {
  @Test
  void builderSanitizesNonFiniteDeadbandAndDelay() {
    AlarmConfig config = AlarmConfig.builder().deadband(Double.NaN)
        .delay(Double.POSITIVE_INFINITY).build();

    assertEquals(0.0, config.getDeadband());
    assertEquals(0.0, config.getDelay());
  }

  @Test
  void nonFiniteDelayDoesNotPreventAlarmActivation() {
    AlarmConfig config = AlarmConfig.builder().highLimit(10.0).delay(Double.NaN).build();
    AlarmState state = new AlarmState();

    List<AlarmEvent> events = state.evaluate(config, 11.0, 0.0, 1.0, "pressure");

    assertTrue(state.isActive());
    assertEquals(1, events.size());
    assertEquals(AlarmEventType.ACTIVATED, events.get(0).getType());
  }
}

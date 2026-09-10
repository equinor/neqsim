package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.powergeneration.CombinedCycleSystem;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.HRSG;
import neqsim.process.equipment.powergeneration.SteamTurbine;

/** Rating consistency across power generation strategy entry points. */
class PowerGenerationCapacityStrategyTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(strings = { "gas", "steam", "hrsg", "combined" })
  void equipmentRatingOverridesStrategyDefaultInBothCapacityApis(String type) {
    ProcessEquipmentInterface equipment = equipment(type);
    PowerGenerationCapacityStrategy strategy = new PowerGenerationCapacityStrategy(40000.0);
    setRating(equipment, 25.0);
    assertRating(strategy, equipment, type, 25000.0);
    setRating(equipment, 30.0);
    assertRating(strategy, equipment, type, 30000.0);
  }

  @ParameterizedTest
  @ValueSource(strings = { "gas", "steam", "hrsg", "combined" })
  void unsetRatingUsesTheConfiguredFallback(String type) {
    ProcessEquipmentInterface equipment = equipment(type);
    assertRating(new PowerGenerationCapacityStrategy(), equipment, type, 50000.0);
    assertRating(new PowerGenerationCapacityStrategy(40000.0), equipment, type, 40000.0);
  }

  private void assertRating(PowerGenerationCapacityStrategy strategy, ProcessEquipmentInterface equipment, String type,
      double expectedKW) {
    String constraintName = "hrsg".equals(type) ? "heatTransferred" : "combined".equals(type) ? "totalPower" : "power";
    assertEquals(expectedKW, strategy.getConstraints(equipment).get(constraintName).getDesignValue(), 1.0e-8);
    assertEquals(expectedKW, strategy.evaluateMaxCapacity(equipment), 1.0e-8);
  }

  private ProcessEquipmentInterface equipment(String type) {
    if ("gas".equals(type)) {
      return new GasTurbine("gas turbine");
    }
    if ("steam".equals(type)) {
      return new SteamTurbine("steam turbine");
    }
    if ("hrsg".equals(type)) {
      return new HRSG("heat recovery");
    }
    return new CombinedCycleSystem("combined cycle");
  }

  private void setRating(ProcessEquipmentInterface equipment, double powerMW) {
    if (equipment instanceof GasTurbine) {
      ((GasTurbine) equipment).setRatedPower(powerMW, "MW");
    } else if (equipment instanceof SteamTurbine) {
      ((SteamTurbine) equipment).setRatedPower(powerMW, "MW");
    } else if (equipment instanceof HRSG) {
      ((HRSG) equipment).setDesignHeatDuty(powerMW, "MW");
    } else {
      ((CombinedCycleSystem) equipment).setRatedTotalPower(powerMW, "MW");
    }
  }
}

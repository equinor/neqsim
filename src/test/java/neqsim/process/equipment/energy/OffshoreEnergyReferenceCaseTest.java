package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class OffshoreEnergyReferenceCaseTest {
  @Test
  void reproducesPublishedAcceptanceCriteria() {
    EnergyTimeSeriesResult result = OffshoreEnergyReferenceCase.run24HourCase();
    OffshoreEnergyReferenceCase.requireAcceptanceCriteria(result);
    assertEquals(6, result.getIntervals().size());
    assertEquals(312.0, result.getServedEnergyMWh(), 1.0e-9);
    assertEquals(4.0, OffshoreEnergyReferenceCase.getWindCurtailedEnergyMWh(result), 1.0e-9);
    assertEquals(144.0, OffshoreEnergyReferenceCase.getWindGeneratedEnergyMWh(result), 1.0e-9);
    assertEquals(168.0, OffshoreEnergyReferenceCase.getGasGeneratedEnergyMWh(result), 1.0e-9);
  }
}

package neqsim.process.mechanicaldesign.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.Api610PumpType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.AssessmentStatus;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.BearingType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.CheckStatus;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;

/** Tests for the value-only API 610 mechanical design screening calculator. */
class PumpApi610DesignCalculatorTest {
  @Test
  void completePassingAssessmentIsAuditable() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();
    calculator.setPumpType(Api610PumpType.OH2);
    calculator.setDutyPoint(100.0, 80.0, 3000.0, 850.0, 25.0);
    calculator.setBepPoint(100.0, 80.0, DataSource.VENDOR_CURVE);
    calculator.setNpsh(6.0, 4.0, DataSource.VENDOR_CURVE);
    calculator.setPressureBasis(5.0, 20.0, 90.0, DataSource.VENDOR_CURVE);
    calculator.setHydrostaticTestPressureBara(30.0);
    calculator.setDriverCriteria(1.10, new double[] { 22.0, 30.0, 37.0 });
    calculator.setBearingData(BearingType.BALL, 100.0, 5.0);
    calculator.setMechanicalEvidence(0.03, 4000.0, 0.8, 2.5);

    calculator.calculate();

    assertEquals(AssessmentStatus.PASS, calculator.getAssessmentStatus());
    assertEquals("POR", calculator.getOperatingRegion());
    assertEquals(30.0, calculator.getSelectedDriverPowerKw(), 1.0e-12);
    assertEquals(13.0, calculator.getRequiredCasingPressureBara(), 0.6);
    assertEquals(30.0, calculator.getPreliminaryHydrostaticTestPressureBara(), 1.0e-12);
    assertTrue(calculator.getBearingL10LifeHours() > 25000.0);
    assertEquals(CheckStatus.PASS, calculator.getCheck("head-rise").getStatus());
  }

  @Test
  void suppliedNonCompliantEvidenceFailsAssessment() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();
    calculator.setPumpType(Api610PumpType.OH2);
    calculator.setDutyPoint(50.0, 80.0, 3000.0, 850.0, 25.0);
    calculator.setBepPoint(100.0, 80.0, DataSource.VENDOR_CURVE);
    calculator.setNpsh(4.0, 4.0, DataSource.VENDOR_CURVE);
    calculator.setPressureBasis(5.0, 8.0, 84.0, DataSource.PURCHASER_INPUT);
    calculator.setHydrostaticTestPressureBara(10.0);
    calculator.setBearingData(BearingType.BALL, 5.0, 5.0);
    calculator.setMechanicalEvidence(0.08, 3300.0, 1.2, 4.0);

    calculator.calculate();

    assertEquals(AssessmentStatus.FAIL, calculator.getAssessmentStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("operating-region").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("npsh-margin").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("pressure-casing").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("hydrostatic-test").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("head-rise").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("bearing-life").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("shaft-deflection").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("critical-speed").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("nozzle-loads").getStatus());
    assertEquals(CheckStatus.FAIL, calculator.getCheck("vibration").getStatus());
  }

  @Test
  void missingVendorDataIsNeverTreatedAsPassing() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();

    calculator.calculate();

    assertEquals(AssessmentStatus.NOT_EVALUATED, calculator.getAssessmentStatus());
    assertEquals(CheckStatus.NOT_EVALUATED, calculator.getCheck("operating-region").getStatus());
    assertEquals(CheckStatus.NOT_EVALUATED, calculator.getCheck("pressure-casing").getStatus());
    assertEquals(CheckStatus.NOT_EVALUATED, calculator.getCheck("bearing-life").getStatus());
    assertTrue(calculator.getChecks().stream()
        .allMatch(check -> check.getStatus() == CheckStatus.NOT_EVALUATED));
  }

  @Test
  void bearingLifeAndDriverSelectionUseEngineeringUnits() {
    assertEquals(44444.444, PumpApi610DesignCalculator.calculateBearingL10LifeHours(100.0, 5.0,
        3000.0, BearingType.BALL), 0.01);
    assertTrue(Double.isNaN(PumpApi610DesignCalculator.calculateBearingL10LifeHours(100.0, 5.0,
        3000.0, BearingType.SLEEVE)));
    assertEquals(30.0,
        PumpApi610DesignCalculator.selectDriverRating(27.5, new double[] { 37.0, 22.0, 30.0 }), 1.0e-12);
    assertTrue(Double.isNaN(
        PumpApi610DesignCalculator.selectDriverRating(40.0, new double[] { 22.0, 30.0, 37.0 })));
  }

  @Test
  void seallessConstructionProducesScopeWarning() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();
    calculator.setPumpType(Api610PumpType.OH2);
    calculator.setDutyPoint(100.0, 80.0, 3000.0, 850.0, 25.0);
    calculator.setSeallessPump(true);

    calculator.calculate();

    assertEquals(AssessmentStatus.PASS_WITH_WARNINGS, calculator.getAssessmentStatus());
    assertNotNull(calculator.getCheck("standard-scope"));
    assertEquals(CheckStatus.WARNING, calculator.getCheck("standard-scope").getStatus());
  }
}

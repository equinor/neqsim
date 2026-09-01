package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;

/** Shared envelope lookup helpers for discipline design modules. */
final class DesignModuleSupport {
  private DesignModuleSupport() {
  }

  static EngineeringDesignEnvelope.GoverningValue requireMetric(EngineeringCaseRunReport report, String metricId) {
    EngineeringDesignEnvelope.GoverningValue value = report.getEnvelope().getGoverningValues().get(metricId);
    if (value == null) {
      throw new IllegalArgumentException("Design-case envelope is missing required metric " + metricId);
    }
    return value;
  }

  static double[] defaultPipeDiametersMeters() {
    return new double[] { 0.0266, 0.0409, 0.0525, 0.0779, 0.1023, 0.1282, 0.1541, 0.2027, 0.2545, 0.3032, 0.3334,
        0.3810, 0.4287, 0.4778, 0.5750, 0.6720, 0.7680, 0.8640, 0.9600 };
  }

  static double[] defaultCvCandidates() {
    return new double[] { 0.4, 0.63, 1.0, 1.6, 2.5, 4.0, 6.3, 10.0, 16.0, 25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0,
        630.0, 1000.0, 1600.0, 2500.0, 4000.0 };
  }
}

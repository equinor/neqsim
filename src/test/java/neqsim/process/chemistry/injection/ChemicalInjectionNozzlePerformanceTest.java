package neqsim.process.chemistry.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.injection.ChemicalInjectionNozzlePerformance.InjectionDevice;

/**
 * Tests for {@link ChemicalInjectionNozzlePerformance}.
 */
public class ChemicalInjectionNozzlePerformanceTest {

  /**
   * Builds a case representative of an H2S scavenger injection into a 20 inch separator gas outlet.
   *
   * @param device injection device to use
   * @return a configured, not yet evaluated model
   */
  private ChemicalInjectionNozzlePerformance separatorGasOutletCase(InjectionDevice device) {
    ChemicalInjectionNozzlePerformance model = new ChemicalInjectionNozzlePerformance();
    model.setInjectionDevice(device);
    model.setPipeInnerDiameter(0.4889);
    model.setGasVolumeFlow(2.21);
    model.setGasDensity(11.5);
    model.setGasViscosity(1.25e-5);
    model.setChemicalVolumeFlow(235.0);
    model.setChemicalDensity(1050.0);
    model.setChemicalViscosity(5.0e-3);
    model.setSurfaceTension(0.035);
    model.setNozzleDifferentialPressure(6.5);
    model.setSprayConeAngle(90.0);
    model.setInsertionDepth(0.135);
    return model;
  }

  @Test
  void gasVelocityFollowsPipeAreaAndFlow() {
    ChemicalInjectionNozzlePerformance model = separatorGasOutletCase(InjectionDevice.PLAIN_QUILL);
    model.evaluate();
    double area = Math.PI * 0.4889 * 0.4889 / 4.0;
    assertEquals(2.21 / area, model.getGasVelocity(), 1.0e-9);
    assertTrue(model.isEvaluated());
  }

  @Test
  void atomizerGivesFinerDropsAndMoreAreaThanPlainQuill() {
    ChemicalInjectionNozzlePerformance quill = separatorGasOutletCase(InjectionDevice.PLAIN_QUILL);
    quill.evaluate();
    ChemicalInjectionNozzlePerformance nozzle = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    nozzle.evaluate();

    assertTrue(nozzle.getSauterMeanDiameterMicron() < quill.getSauterMeanDiameterMicron(),
        "an atomizing nozzle must produce finer drops than a bare quill");
    assertTrue(nozzle.getInterfacialArea() > quill.getInterfacialArea(),
        "finer drops must create more interfacial area at the same chemical rate");
    assertTrue(nozzle.getWallImpingementLength() > quill.getWallImpingementLength(),
        "finer drops must stay entrained longer");
    assertTrue(nozzle.getDispersionIndex() > quill.getDispersionIndex(),
        "the dispersion index must reward the atomizing nozzle");
  }

  @Test
  void higherNozzlePressureGivesFinerDropsWithTheExpectedExponent() {
    ChemicalInjectionNozzlePerformance low = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    low.setNozzleDifferentialPressure(6.5);
    low.evaluate();
    ChemicalInjectionNozzlePerformance high = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    high.setNozzleDifferentialPressure(26.0);
    high.evaluate();

    // Lefebvre gives SMD proportional to dP^-0.5, so a four times higher dP halves the drop size.
    double ratio = low.getSauterMeanDiameter() / high.getSauterMeanDiameter();
    assertEquals(2.0, ratio, 0.02);
  }

  @Test
  void lowVelocityQuillIsFlaggedAndPenalised() {
    ChemicalInjectionNozzlePerformance model = separatorGasOutletCase(InjectionDevice.PLAIN_QUILL);
    model.setGasVolumeFlow(0.5);
    model.evaluate();
    assertTrue(model.getGasVelocity() < ChemicalInjectionNozzlePerformance.QUILL_MIN_GAS_VELOCITY);
    List<String> warnings = model.getWarnings();
    boolean flagged = false;
    for (String warning : warnings) {
      if (warning.startsWith("gas_velocity_below_quill_limit")) {
        flagged = true;
      }
    }
    assertTrue(flagged, "a bare quill below the velocity limit must be flagged");
    assertTrue(model.getDispersionIndex() < 0.2);
  }

  @Test
  void offCentreInjectionShortensTheDropFlightPath() {
    ChemicalInjectionNozzlePerformance centred = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    centred.setInsertionDepth(0.4889 / 2.0);
    centred.evaluate();
    ChemicalInjectionNozzlePerformance offCentre = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    offCentre.setInsertionDepth(0.100);
    offCentre.evaluate();

    assertTrue(offCentre.getWallImpingementLength() < centred.getWallImpingementLength(),
        "a shallower insertion must shorten the distance before the drops hit the wall");
    boolean flagged = false;
    for (String warning : offCentre.getWarnings()) {
      if (warning.startsWith("off_centre_injection")) {
        flagged = true;
      }
    }
    assertTrue(flagged);
  }

  @Test
  void resultMapCarriesTheScreeningOutputs() {
    ChemicalInjectionNozzlePerformance model = separatorGasOutletCase(InjectionDevice.FULL_CONE_NOZZLE);
    model.evaluate();
    assertTrue(model.toMap().containsKey("sauterMeanDiameter_micron"));
    assertTrue(model.toMap().containsKey("dispersionIndex"));
    assertFalse(model.toMap().isEmpty());
    double index = model.getDispersionIndex();
    assertTrue(index >= 0.0 && index <= 1.0);
  }
}

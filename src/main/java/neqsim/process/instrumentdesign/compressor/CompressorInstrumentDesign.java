package neqsim.process.instrumentdesign.compressor;

import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Instrument design for compressors.
 *
 * <p>
 * Determines the required instrumentation for a compressor following API 617 / API 670 practice. A
 * compressor typically requires:
 * </p>
 * <ul>
 * <li>Pressure: suction PT, discharge PT, differential pressure (surge detection)</li>
 * <li>Temperature: suction TT, discharge TT, bearing temperatures</li>
 * <li>Flow: suction FT (for anti-surge control)</li>
 * <li>Vibration: 2x VT per bearing (X-Y probes, API 670)</li>
 * <li>Speed: 1x ST (shaft speed transmitter)</li>
 * <li>Safety: PSHH (discharge overpressure), TSHH (discharge overtemperature), VSLL (low lube oil
 * pressure)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorInstrumentDesign extends InstrumentDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Number of bearings (each gets X-Y vibration probes). */
  private int numberOfBearings = 2;

  /** Whether anti-surge control instrumentation is included. */
  private boolean includeAntiSurge = true;

  /**
   * Constructor for CompressorInstrumentDesign.
   *
   * @param processEquipment the compressor equipment
   */
  public CompressorInstrumentDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    double maxPressure = getMaxDischargePressure();

    // === Pressure instrumentation ===
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Suction Pressure", 0.0, maxPressure, "bara", "AI"));
    getInstrumentList().add(new InstrumentSpecification("PT", "Discharge Pressure", 0.0,
        maxPressure * 1.2, "bara", "AI"));

    if (includeAntiSurge) {
      getInstrumentList().add(new InstrumentSpecification("PDT", "Differential Pressure (Surge)",
          0.0, maxPressure, "bar", "AI"));
    }

    // PSHH: Discharge overpressure trip
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(new InstrumentSpecification("PSHH", "Discharge Overpressure Trip",
          "DI", getDefaultSilLevel()));
    }

    // === Temperature instrumentation ===
    getInstrumentList()
        .add(new InstrumentSpecification("TT", "Suction Temperature", -50.0, 300.0, "degC", "AI"));
    getInstrumentList().add(
        new InstrumentSpecification("TT", "Discharge Temperature", -50.0, 400.0, "degC", "AI"));

    // Bearing temperatures
    for (int i = 1; i <= numberOfBearings; i++) {
      getInstrumentList().add(new InstrumentSpecification("TT", "Bearing " + i + " Temperature",
          0.0, 150.0, "degC", "AI"));
    }

    // TSHH: Discharge overtemperature trip
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(new InstrumentSpecification("TSHH", "Discharge Overtemperature Trip",
          "DI", getDefaultSilLevel()));
    }

    // === Flow instrumentation ===
    if (includeAntiSurge) {
      getInstrumentList().add(
          new InstrumentSpecification("FT", "Suction Flow (Anti-Surge)", 0.0, 100.0, "%", "AI"));
      // Anti-surge valve position output
      getInstrumentList().add(
          new InstrumentSpecification("FCV", "Anti-Surge Valve Output", 0.0, 100.0, "%", "AO"));
    }

    // === Vibration monitoring (API 670) ===
    for (int i = 1; i <= numberOfBearings; i++) {
      getInstrumentList().add(new InstrumentSpecification("VT", "Bearing " + i + " Vibration X",
          0.0, 250.0, "um p-p", "AI"));
      getInstrumentList().add(new InstrumentSpecification("VT", "Bearing " + i + " Vibration Y",
          0.0, 250.0, "um p-p", "AI"));
    }

    // Vibration high trip
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(
          new InstrumentSpecification("VSHH", "High Vibration Trip", "DI", getDefaultSilLevel()));
    }

    // === Speed measurement ===
    getInstrumentList()
        .add(new InstrumentSpecification("ST", "Shaft Speed", 0.0, 15000.0, "rpm", "AI"));

    // === Lube oil ===
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Lube Oil Pressure", 0.0, 10.0, "bara", "AI"));
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(new InstrumentSpecification("PSLL", "Low Lube Oil Pressure Trip",
          "DI", getDefaultSilLevel()));
    }
  }

  /**
   * Get maximum discharge pressure estimate.
   *
   * @return max discharge pressure in bara
   */
  private double getMaxDischargePressure() {
    try {
      double p = getProcessEquipment().getPressure();
      return p > 0 ? p * 1.5 : 100.0;
    } catch (Exception e) {
      return 100.0;
    }
  }

  /**
   * Get number of bearings.
   *
   * @return number of bearings
   */
  public int getNumberOfBearings() {
    return numberOfBearings;
  }

  /**
   * Set number of bearings.
   *
   * @param numberOfBearings number of bearings
   */
  public void setNumberOfBearings(int numberOfBearings) {
    this.numberOfBearings = numberOfBearings;
  }

  /**
   * Check if anti-surge instrumentation is included.
   *
   * @return true if included
   */
  public boolean isIncludeAntiSurge() {
    return includeAntiSurge;
  }

  /**
   * Set whether anti-surge instrumentation is included.
   *
   * @param includeAntiSurge true to include
   */
  public void setIncludeAntiSurge(boolean includeAntiSurge) {
    this.includeAntiSurge = includeAntiSurge;
  }
}

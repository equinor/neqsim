package neqsim.process.instrumentdesign.pipeline;

import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Instrument design for pipelines.
 *
 * <p>
 * Determines the required instrumentation for a pipeline segment. A pipeline typically requires:
 * </p>
 * <ul>
 * <li>Pressure: inlet PT, outlet PT</li>
 * <li>Temperature: inlet TT, outlet TT</li>
 * <li>Flow: inlet FT (custody transfer or operational metering)</li>
 * <li>Pig detection: pig signaller at inlet and outlet</li>
 * <li>Safety: PSHH (overpressure), PSLL (low pressure / leak detection)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipelineInstrumentDesign extends InstrumentDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Whether pig detection instruments are included. */
  private boolean includePigDetection = true;

  /** Whether leak detection instrumentation is included. */
  private boolean includeLeakDetection = true;

  /**
   * Constructor for PipelineInstrumentDesign.
   *
   * @param processEquipment the pipeline equipment
   */
  public PipelineInstrumentDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    // === Pressure instrumentation ===
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Inlet Pressure", 0.0, 200.0, "bara", "AI"));
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Outlet Pressure", 0.0, 200.0, "bara", "AI"));

    // PSHH: Overpressure protection
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(
          new InstrumentSpecification("PSHH", "Overpressure Trip", "DI", getDefaultSilLevel()));
    }

    // === Temperature instrumentation ===
    getInstrumentList()
        .add(new InstrumentSpecification("TT", "Inlet Temperature", -50.0, 200.0, "degC", "AI"));
    getInstrumentList()
        .add(new InstrumentSpecification("TT", "Outlet Temperature", -50.0, 200.0, "degC", "AI"));

    // === Flow instrumentation ===
    getInstrumentList()
        .add(new InstrumentSpecification("FT", "Pipeline Flow", 0.0, 100.0, "%", "AI"));

    // === Leak detection ===
    if (includeLeakDetection) {
      // PSLL: Low pressure alarm for leak detection
      if (isIncludeSafetyInstruments()) {
        getInstrumentList().add(new InstrumentSpecification("PSLL", "Low Pressure (Leak Detection)",
            "DI", getDefaultSilLevel()));
      }
    }

    // === Pig detection ===
    if (includePigDetection) {
      getInstrumentList().add(new InstrumentSpecification("ZS", "Pig Signaller Inlet", "DI", 0));
      getInstrumentList().add(new InstrumentSpecification("ZS", "Pig Signaller Outlet", "DI", 0));
    }
  }

  /**
   * Check if pig detection is included.
   *
   * @return true if included
   */
  public boolean isIncludePigDetection() {
    return includePigDetection;
  }

  /**
   * Set whether pig detection is included.
   *
   * @param includePigDetection true to include
   */
  public void setIncludePigDetection(boolean includePigDetection) {
    this.includePigDetection = includePigDetection;
  }

  /**
   * Check if leak detection is included.
   *
   * @return true if included
   */
  public boolean isIncludeLeakDetection() {
    return includeLeakDetection;
  }

  /**
   * Set whether leak detection is included.
   *
   * @param includeLeakDetection true to include
   */
  public void setIncludeLeakDetection(boolean includeLeakDetection) {
    this.includeLeakDetection = includeLeakDetection;
  }
}

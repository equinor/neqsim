package neqsim.process.instrumentdesign;

import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * Specification for a single instrument in a process plant.
 *
 * <p>
 * Represents an instrument data sheet entry per ISA-5.1 / ISA-20 conventions. Each specification
 * defines what to measure, the type of instrument, its range, signal output, hazardous area
 * classification, and optional SIL rating.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class InstrumentSpecification implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** ISA-5.1 functional identification (e.g. "PT", "TT", "LT", "FT", "PSH", "LSLL"). */
  private String isaSymbol;

  /** Tag number (e.g. "PT-1001A"). Generated or manually assigned. */
  private String tagNumber;

  /** Service description (e.g. "HP Separator Gas Outlet Pressure"). */
  private String service;

  /** Full instrument type name (e.g. "PressureTransmitter"). */
  private String instrumentType;

  /** Measurement range minimum in engineering units. */
  private double rangeMin;

  /** Measurement range maximum in engineering units. */
  private double rangeMax;

  /** Engineering unit for the measurement range (e.g. "bara", "degC", "%"). */
  private String rangeUnit;

  /** Output signal type (e.g. "4-20mA HART", "Foundation Fieldbus", "Discrete 24VDC"). */
  private String outputSignal = "4-20mA HART";

  /** Process connection size (e.g. "1/2 NPT", "DN15 RF"). */
  private String connectionSize = "1/2 NPT";

  /** Wetted parts material (e.g. "316SS", "Hastelloy C276"). */
  private String material = "316SS";

  /** Hazardous area zone classification (e.g. "Zone 1", "Zone 2"). */
  private String hazardousAreaZone = "Zone 1";

  /** Explosion protection marking (e.g. "Ex ia IIC T4 Ga"). */
  private String exProtection = "Ex ia IIC T4 Ga";

  /**
   * I/O type: "AI" (analog input), "AO" (analog output), "DI" (digital input), "DO" (digital
   * output).
   */
  private String ioType = "AI";

  /** SIL rating (0 = no SIL, 1-3 = SIL level per IEC 61508). */
  private int silRating = 0;

  /** Whether this instrument is safety-related (part of SIS). */
  private boolean safetyRelated = false;

  /** Estimated unit cost in USD. */
  private double estimatedCostUSD = 0.0;

  /** The runtime measurement device class this spec maps to. */
  private String deviceClassName;

  /**
   * Constructor for InstrumentSpecification.
   *
   * @param isaSymbol ISA-5.1 functional identification (e.g. "PT", "TT")
   * @param service service description
   * @param rangeMin measurement range minimum
   * @param rangeMax measurement range maximum
   * @param rangeUnit engineering unit for range
   * @param ioType I/O type ("AI", "AO", "DI", "DO")
   */
  public InstrumentSpecification(String isaSymbol, String service, double rangeMin, double rangeMax,
      String rangeUnit, String ioType) {
    this.isaSymbol = isaSymbol;
    this.service = service;
    this.rangeMin = rangeMin;
    this.rangeMax = rangeMax;
    this.rangeUnit = rangeUnit;
    this.ioType = ioType;
    this.instrumentType = mapIsaSymbolToType(isaSymbol);
    this.estimatedCostUSD = estimateDefaultCost(isaSymbol);
  }

  /**
   * Constructor for discrete/switch instruments (no range).
   *
   * @param isaSymbol ISA-5.1 functional identification (e.g. "PSH", "LSLL")
   * @param service service description
   * @param ioType I/O type ("DI" or "DO")
   * @param silRating SIL level (0-3)
   */
  public InstrumentSpecification(String isaSymbol, String service, String ioType, int silRating) {
    this.isaSymbol = isaSymbol;
    this.service = service;
    this.ioType = ioType;
    this.silRating = silRating;
    this.safetyRelated = silRating > 0;
    this.instrumentType = mapIsaSymbolToType(isaSymbol);
    this.estimatedCostUSD = estimateDefaultCost(isaSymbol);
    if (silRating > 0) {
      this.outputSignal = "Discrete 24VDC";
    }
  }

  /**
   * Map ISA-5.1 symbol to instrument type name.
   *
   * @param symbol ISA symbol
   * @return instrument type name
   */
  private String mapIsaSymbolToType(String symbol) {
    if (symbol == null) {
      return "Unknown";
    }
    String upper = symbol.toUpperCase().trim();
    if (upper.startsWith("PT") || upper.startsWith("PI")) {
      return "PressureTransmitter";
    } else if (upper.startsWith("PS")) {
      return "PressureSwitch";
    } else if (upper.startsWith("TT") || upper.startsWith("TI") || upper.startsWith("TE")) {
      return "TemperatureTransmitter";
    } else if (upper.startsWith("TS")) {
      return "TemperatureSwitch";
    } else if (upper.startsWith("LT") || upper.startsWith("LI")) {
      return "LevelTransmitter";
    } else if (upper.startsWith("LS")) {
      return "LevelSwitch";
    } else if (upper.startsWith("FT") || upper.startsWith("FI") || upper.startsWith("FE")) {
      return "FlowTransmitter";
    } else if (upper.startsWith("AT") || upper.startsWith("AI") || upper.startsWith("AE")) {
      return "Analyser";
    } else if (upper.startsWith("VT") || upper.startsWith("VI")) {
      return "VibrationTransmitter";
    } else if (upper.startsWith("ST") || upper.startsWith("SI")) {
      return "SpeedTransmitter";
    } else if (upper.startsWith("ZS") || upper.startsWith("ZT")) {
      return "PositionTransmitter";
    } else if (upper.startsWith("XV") || upper.startsWith("XS")) {
      return "SolenoidValve";
    } else {
      return "Instrument";
    }
  }

  /**
   * Estimate default cost in USD based on instrument type.
   *
   * @param symbol ISA symbol
   * @return estimated cost in USD
   */
  private double estimateDefaultCost(String symbol) {
    if (symbol == null) {
      return 2000.0;
    }
    String upper = symbol.toUpperCase().trim();
    if (upper.startsWith("PT") || upper.startsWith("PS")) {
      return 3500.0;
    } else if (upper.startsWith("TT") || upper.startsWith("TE") || upper.startsWith("TS")) {
      return 2500.0;
    } else if (upper.startsWith("LT") || upper.startsWith("LS")) {
      return 5000.0;
    } else if (upper.startsWith("FT") || upper.startsWith("FE")) {
      return 8000.0;
    } else if (upper.startsWith("AT") || upper.startsWith("AE")) {
      return 15000.0;
    } else if (upper.startsWith("VT")) {
      return 4000.0;
    } else if (upper.startsWith("XV")) {
      return 6000.0;
    } else if (upper.startsWith("ZS") || upper.startsWith("ZT")) {
      return 3000.0;
    } else {
      return 2000.0;
    }
  }

  /**
   * Check if this is an analog instrument.
   *
   * @return true if AI or AO
   */
  public boolean isAnalog() {
    return "AI".equals(ioType) || "AO".equals(ioType);
  }

  /**
   * Check if this is a digital/discrete instrument.
   *
   * @return true if DI or DO
   */
  public boolean isDigital() {
    return "DI".equals(ioType) || "DO".equals(ioType);
  }

  // === Getters and Setters ===

  /**
   * Get ISA-5.1 functional identification.
   *
   * @return ISA symbol
   */
  public String getIsaSymbol() {
    return isaSymbol;
  }

  /**
   * Set ISA-5.1 functional identification.
   *
   * @param isaSymbol ISA symbol
   */
  public void setIsaSymbol(String isaSymbol) {
    this.isaSymbol = isaSymbol;
  }

  /**
   * Get tag number.
   *
   * @return tag number
   */
  public String getTagNumber() {
    return tagNumber;
  }

  /**
   * Set tag number.
   *
   * @param tagNumber tag number
   */
  public void setTagNumber(String tagNumber) {
    this.tagNumber = tagNumber;
  }

  /**
   * Get service description.
   *
   * @return service description
   */
  public String getService() {
    return service;
  }

  /**
   * Set service description.
   *
   * @param service service description
   */
  public void setService(String service) {
    this.service = service;
  }

  /**
   * Get instrument type name.
   *
   * @return instrument type name
   */
  public String getInstrumentType() {
    return instrumentType;
  }

  /**
   * Set instrument type name.
   *
   * @param instrumentType instrument type name
   */
  public void setInstrumentType(String instrumentType) {
    this.instrumentType = instrumentType;
  }

  /**
   * Get measurement range minimum.
   *
   * @return range minimum in engineering units
   */
  public double getRangeMin() {
    return rangeMin;
  }

  /**
   * Set measurement range minimum.
   *
   * @param rangeMin range minimum in engineering units
   */
  public void setRangeMin(double rangeMin) {
    this.rangeMin = rangeMin;
  }

  /**
   * Get measurement range maximum.
   *
   * @return range maximum in engineering units
   */
  public double getRangeMax() {
    return rangeMax;
  }

  /**
   * Set measurement range maximum.
   *
   * @param rangeMax range maximum in engineering units
   */
  public void setRangeMax(double rangeMax) {
    this.rangeMax = rangeMax;
  }

  /**
   * Get range unit.
   *
   * @return range unit string
   */
  public String getRangeUnit() {
    return rangeUnit;
  }

  /**
   * Set range unit.
   *
   * @param rangeUnit range unit string
   */
  public void setRangeUnit(String rangeUnit) {
    this.rangeUnit = rangeUnit;
  }

  /**
   * Get output signal type.
   *
   * @return output signal type
   */
  public String getOutputSignal() {
    return outputSignal;
  }

  /**
   * Set output signal type.
   *
   * @param outputSignal output signal type
   */
  public void setOutputSignal(String outputSignal) {
    this.outputSignal = outputSignal;
  }

  /**
   * Get process connection size.
   *
   * @return connection size
   */
  public String getConnectionSize() {
    return connectionSize;
  }

  /**
   * Set process connection size.
   *
   * @param connectionSize connection size
   */
  public void setConnectionSize(String connectionSize) {
    this.connectionSize = connectionSize;
  }

  /**
   * Get wetted parts material.
   *
   * @return material
   */
  public String getMaterial() {
    return material;
  }

  /**
   * Set wetted parts material.
   *
   * @param material material
   */
  public void setMaterial(String material) {
    this.material = material;
  }

  /**
   * Get hazardous area zone classification.
   *
   * @return hazardous area zone
   */
  public String getHazardousAreaZone() {
    return hazardousAreaZone;
  }

  /**
   * Set hazardous area zone classification.
   *
   * @param hazardousAreaZone hazardous area zone
   */
  public void setHazardousAreaZone(String hazardousAreaZone) {
    this.hazardousAreaZone = hazardousAreaZone;
  }

  /**
   * Get explosion protection marking.
   *
   * @return Ex protection marking
   */
  public String getExProtection() {
    return exProtection;
  }

  /**
   * Set explosion protection marking.
   *
   * @param exProtection Ex protection marking
   */
  public void setExProtection(String exProtection) {
    this.exProtection = exProtection;
  }

  /**
   * Get I/O type.
   *
   * @return I/O type ("AI", "AO", "DI", "DO")
   */
  public String getIoType() {
    return ioType;
  }

  /**
   * Set I/O type.
   *
   * @param ioType I/O type ("AI", "AO", "DI", "DO")
   */
  public void setIoType(String ioType) {
    this.ioType = ioType;
  }

  /**
   * Get SIL rating.
   *
   * @return SIL rating (0-3)
   */
  public int getSilRating() {
    return silRating;
  }

  /**
   * Set SIL rating.
   *
   * @param silRating SIL rating (0-3)
   */
  public void setSilRating(int silRating) {
    this.silRating = silRating;
    this.safetyRelated = silRating > 0;
  }

  /**
   * Check if this instrument is safety-related.
   *
   * @return true if part of SIS
   */
  public boolean isSafetyRelated() {
    return safetyRelated;
  }

  /**
   * Set whether this instrument is safety-related.
   *
   * @param safetyRelated true if part of SIS
   */
  public void setSafetyRelated(boolean safetyRelated) {
    this.safetyRelated = safetyRelated;
  }

  /**
   * Get estimated unit cost in USD.
   *
   * @return estimated cost in USD
   */
  public double getEstimatedCostUSD() {
    return estimatedCostUSD;
  }

  /**
   * Set estimated unit cost in USD.
   *
   * @param estimatedCostUSD estimated cost in USD
   */
  public void setEstimatedCostUSD(double estimatedCostUSD) {
    this.estimatedCostUSD = estimatedCostUSD;
  }

  /**
   * Get the runtime device class name.
   *
   * @return device class name
   */
  public String getDeviceClassName() {
    return deviceClassName;
  }

  /**
   * Set the runtime device class name.
   *
   * @param deviceClassName device class name
   */
  public void setDeviceClassName(String deviceClassName) {
    this.deviceClassName = deviceClassName;
  }
}

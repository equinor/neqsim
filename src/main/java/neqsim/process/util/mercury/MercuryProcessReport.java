package neqsim.process.util.mercury;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Mercury in a calculated process: every stream, every unit operation and the overall mercury balance.
 *
 * <p>
 * Created by {@link MercuryProcessAnalyzer}. The report is a snapshot; run the analyzer again after the process is
 * recalculated.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryProcessReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Streams in the order they are found in the process. */
  private final List<MercuryStreamResult> streams;

  /** Unit operations in the order they are found in the process. */
  private final List<MercuryUnitResult> units;

  /** Molar mass of mercury in kg/mol. */
  private final double mercuryMolarMass;

  /**
   * Create a report.
   *
   * @param streams mercury in each stream
   * @param units mercury balance of each unit operation
   * @param mercuryMolarMass molar mass of mercury in kg/mol
   */
  MercuryProcessReport(List<MercuryStreamResult> streams, List<MercuryUnitResult> units, double mercuryMolarMass) {
    this.streams = Collections.unmodifiableList(new ArrayList<MercuryStreamResult>(streams));
    this.units = Collections.unmodifiableList(new ArrayList<MercuryUnitResult>(units));
    this.mercuryMolarMass = mercuryMolarMass;
  }

  /**
   * Get the mercury in every stream of the process.
   *
   * @return unmodifiable list in the order the streams are found in the process
   */
  public List<MercuryStreamResult> getStreams() {
    return streams;
  }

  /**
   * Get the mercury in one stream.
   *
   * @param name label of the stream in the report, the unit operation it leaves and the stream name joined by a dot
   * (for example {@code "cold separator.gasOutStream"}), or the stream name when only one stream has that name
   * @return mercury in the stream
   * @throws IllegalArgumentException if no stream, or more than one stream, matches the name
   */
  public MercuryStreamResult getStream(String name) {
    MercuryStreamResult match = null;
    for (MercuryStreamResult stream : streams) {
      if (stream.getLabel().equals(name) || (stream.getSourceUnit() + "." + stream.getStreamName()).equals(name)) {
        return stream;
      }
      if (stream.getStreamName().equals(name)) {
        if (match != null) {
          throw new IllegalArgumentException(
              "several streams are named " + name + "; use the label " + match.getLabel() + " or " + stream.getLabel());
        }
        match = stream;
      }
    }
    if (match == null) {
      throw new IllegalArgumentException("no stream named " + name + " in the mercury report");
    }
    return match;
  }

  /**
   * Get the streams that carry free mercury.
   *
   * @return streams that hold more mercury than their fluid phases dissolve
   */
  public List<MercuryStreamResult> getStreamsWithFreeMercury() {
    List<MercuryStreamResult> result = new ArrayList<MercuryStreamResult>();
    for (MercuryStreamResult stream : streams) {
      if (stream.hasFreeMercury()) {
        result.add(stream);
      }
    }
    return result;
  }

  /**
   * Get the mercury balance of every unit operation with connected streams.
   *
   * @return unmodifiable list in the order the unit operations are found in the process
   */
  public List<MercuryUnitResult> getUnits() {
    return units;
  }

  /**
   * Get the mercury balance of one unit operation.
   *
   * @param name name of the unit operation
   * @return mercury balance of the unit operation
   * @throws IllegalArgumentException if the report has no unit operation with the name
   */
  public MercuryUnitResult getUnit(String name) {
    for (MercuryUnitResult unit : units) {
      if (unit.getName().equals(name)) {
        return unit;
      }
    }
    throw new IllegalArgumentException("no unit operation named " + name + " in the mercury report");
  }

  /**
   * Get the mercury entering the process with its feed streams.
   *
   * @param unit "mole/sec", "mole/hr", "kg/sec", "kg/min", "kg/hr", "kg/day", "kg/yr", "g/sec", "g/hr", "g/day" or
   * "g/yr"
   * @return flow of mercury
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getFeedMercuryFlowRate(String unit) {
    double moles = 0.0;
    for (MercuryStreamResult stream : streams) {
      if (stream.isFeed()) {
        moles += stream.getMercuryFlowRate("mole/sec");
      }
    }
    return MercuryPhaseEquilibrium.convertMercuryFlow(moles, mercuryMolarMass, unit);
  }

  /**
   * Get the mercury leaving the process with its product streams.
   *
   * @param unit flow unit, see {@link #getFeedMercuryFlowRate(String)}
   * @return flow of mercury, dissolved and free
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getProductMercuryFlowRate(String unit) {
    double moles = 0.0;
    for (MercuryStreamResult stream : streams) {
      if (stream.isProduct()) {
        moles += stream.getMercuryFlowRate("mole/sec");
      }
    }
    return MercuryPhaseEquilibrium.convertMercuryFlow(moles, mercuryMolarMass, unit);
  }

  /**
   * Get the mercury that stays in the unit operations of the process.
   *
   * @param unit flow unit, see {@link #getFeedMercuryFlowRate(String)}
   * @return sum over the unit operations of mercury entering minus mercury leaving
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getRetainedMercuryFlowRate(String unit) {
    double moles = 0.0;
    for (MercuryUnitResult result : units) {
      moles += result.getRetainedMercuryFlowRate("mole/sec");
    }
    return MercuryPhaseEquilibrium.convertMercuryFlow(moles, mercuryMolarMass, unit);
  }

  /**
   * Get the free mercury held back in the separators of the process.
   *
   * @param unit flow unit, see {@link #getFeedMercuryFlowRate(String)}
   * @return sum of the accumulation rates of free mercury in separators with free mercury accumulation enabled
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getFreeMercuryAccumulationRate(String unit) {
    double moles = 0.0;
    for (MercuryUnitResult result : units) {
      moles += result.getFreeMercuryAccumulationRate("mole/sec");
    }
    return MercuryPhaseEquilibrium.convertMercuryFlow(moles, mercuryMolarMass, unit);
  }

  /**
   * Get the mercury that the report does not account for.
   *
   * @param unit flow unit, see {@link #getFeedMercuryFlowRate(String)}
   * @return feed mercury minus product mercury minus mercury retained in the unit operations; not zero when a stream is
   * used by more than one unit operation or a unit operation does not report its streams
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getMercuryBalanceError(String unit) {
    return getFeedMercuryFlowRate(unit) - getProductMercuryFlowRate(unit) - getRetainedMercuryFlowRate(unit);
  }

  /**
   * Get the highest degree of mercury saturation in the process.
   *
   * @return highest saturation ratio of the streams; above one means free mercury in at least one stream
   */
  public double getMaximumSaturationRatio() {
    double maximum = 0.0;
    for (MercuryStreamResult stream : streams) {
      maximum = Math.max(maximum, stream.getSaturationRatio());
    }
    return maximum;
  }

  /**
   * Check whether free mercury forms anywhere in the process.
   *
   * @return true when a stream carries free mercury or a separator holds free mercury back
   */
  public boolean hasFreeMercury() {
    for (MercuryStreamResult stream : streams) {
      if (stream.hasFreeMercury()) {
        return true;
      }
    }
    for (MercuryUnitResult unit : units) {
      if (unit.getFreeMercuryAccumulationRate("mole/sec") > 0.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the report as JSON.
   *
   * @return JSON text with the overall balance, the streams and the unit operations; flows in g/day, gas concentrations
   * in ug/Sm3 and liquid concentrations in ug/kg
   */
  public String toJson() {
    Map<String, Object> balance = new LinkedHashMap<String, Object>();
    balance.put("feed_g_day", getFeedMercuryFlowRate("g/day"));
    balance.put("product_g_day", getProductMercuryFlowRate("g/day"));
    balance.put("retainedInUnits_g_day", getRetainedMercuryFlowRate("g/day"));
    balance.put("freeMercuryAccumulation_g_day", getFreeMercuryAccumulationRate("g/day"));
    balance.put("balanceError_g_day", getMercuryBalanceError("g/day"));
    balance.put("maximumSaturationRatio", getMaximumSaturationRatio());
    balance.put("freeMercury", hasFreeMercury());
    List<Object> streamMaps = new ArrayList<Object>();
    for (MercuryStreamResult stream : streams) {
      streamMaps.add(stream.toMap());
    }
    List<Object> unitMaps = new ArrayList<Object>();
    for (MercuryUnitResult unit : units) {
      unitMaps.add(unit.toMap());
    }
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("mercuryBalance", balance);
    report.put("streams", streamMaps);
    report.put("units", unitMaps);
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(report);
  }
}

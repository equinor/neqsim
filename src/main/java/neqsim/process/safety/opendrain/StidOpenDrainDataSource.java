package neqsim.process.safety.opendrain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Normalized STID/P&amp;ID and tagreader JSON bridge for the open-drain review engine.
 *
 * <p>
 * This class does not connect to STID or plant historians directly. Retrieval, OCR, P&amp;ID
 * interpretation, and tagreader reads remain in Python/devtools or task workflows. The Java core
 * consumes normalized extracts so the review remains deterministic and testable.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidOpenDrainDataSource implements OpenDrainReviewDataSource {
  /** Normalized STID JSON object. */
  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID/P&amp;ID/tagreader JSON text
   */
  public StidOpenDrainDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized STID/P&amp;ID/tagreader JSON object
   */
  public StidOpenDrainDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized STID/P&amp;ID/tagreader JSON object
   * @return data source
   */
  public static StidOpenDrainDataSource fromJsonObject(JsonObject source) {
    return new StidOpenDrainDataSource(source);
  }

  /**
   * Reads normalized open-drain review input.
   *
   * @return open-drain review input
   */
  @Override
  public OpenDrainReviewInput read() {
    OpenDrainReviewInput input = new OpenDrainReviewInput();
    if (source.has("projectName")) {
      input.setProjectName(source.get("projectName").getAsString());
    }
    if (source.has("defaultLiquidLeakRateKgPerS")) {
      input.setDefaultLiquidLeakRateKgPerS(source.get("defaultLiquidLeakRateKgPerS").getAsDouble());
    }
    addArray(input, "openDrainAreas");
    addArray(input, "drainAreas");
    addArray(input, "areaDrains");
    addArray(input, "drainSystems");
    addArray(input, "helideckDrains");
    addArray(input, "temporaryStorageAreas");
    addArray(input, "lineList");
    addArray(input, "equipment");
    return input;
  }

  /**
   * Adds records from a named array.
   *
   * @param input input receiving records
   * @param key source array key
   */
  private void addArray(OpenDrainReviewInput input, String key) {
    if (!source.has(key) || !source.get(key).isJsonArray()) {
      return;
    }
    JsonArray array = source.getAsJsonArray(key);
    for (int i = 0; i < array.size(); i++) {
      JsonElement element = array.get(i);
      if (element.isJsonObject()) {
        mergeItem(input, fromRecord(element.getAsJsonObject(), key));
      }
    }
  }

  /**
   * Merges one parsed item into the input by area identifier.
   *
   * @param input target input
   * @param item parsed review item
   */
  private void mergeItem(OpenDrainReviewInput input, OpenDrainReviewItem item) {
    OpenDrainReviewInput single = new OpenDrainReviewInput();
    single.addItem(item);
    input.mergeFrom(single);
  }

  /**
   * Converts one normalized STID-like record to a review item.
   *
   * @param record source record
   * @param sourceKey name of the source array
   * @return review item
   */
  private OpenDrainReviewItem fromRecord(JsonObject record, String sourceKey) {
    OpenDrainReviewItem item = OpenDrainReviewItem.fromMap(toMap(record));
    item.addSourceReference(sourceKey);
    return item;
  }

  /**
   * Converts a JSON object to a map.
   *
   * @param object JSON object
   * @return map representation
   */
  private Map<String, Object> toMap(JsonObject object) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      map.put(entry.getKey(), toObject(entry.getValue()));
    }
    return map;
  }

  /**
   * Converts a JSON element to Java primitives, maps, and lists.
   *
   * @param element JSON element to convert
   * @return converted Java object
   */
  private Object toObject(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    if (element.isJsonObject()) {
      return toMap(element.getAsJsonObject());
    }
    if (element.isJsonArray()) {
      List<Object> list = new ArrayList<Object>();
      for (JsonElement child : element.getAsJsonArray()) {
        list.add(toObject(child));
      }
      return list;
    }
    if (element.getAsJsonPrimitive().isBoolean()) {
      return Boolean.valueOf(element.getAsBoolean());
    }
    if (element.getAsJsonPrimitive().isNumber()) {
      return Double.valueOf(element.getAsDouble());
    }
    return element.getAsString();
  }
}

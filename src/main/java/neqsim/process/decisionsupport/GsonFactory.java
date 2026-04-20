package neqsim.process.decisionsupport;

import java.lang.reflect.Type;
import java.time.Instant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Shared Gson configuration for decision support classes.
 *
 * <p>
 * Provides a pre-configured {@link Gson} instance that handles {@link Instant} serialization (as
 * ISO-8601 strings) and allows special floating point values like {@code Double.NaN}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
final class GsonFactory {

  private static final Gson INSTANCE =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
          .registerTypeAdapter(Instant.class, new InstantSerializer())
          .registerTypeAdapter(Instant.class, new InstantDeserializer()).create();

  /**
   * Private constructor to prevent instantiation.
   */
  private GsonFactory() {}

  /**
   * Returns the shared Gson instance configured for decision support classes.
   *
   * @return the configured Gson instance
   */
  static Gson instance() {
    return INSTANCE;
  }

  /**
   * Serializer for {@link Instant} as ISO-8601 strings.
   */
  private static class InstantSerializer implements JsonSerializer<Instant> {
    /**
     * Serializes an Instant to its ISO-8601 string representation.
     *
     * @param src the Instant to serialize
     * @param typeOfSrc the type of the source object
     * @param context the serialization context
     * @return a JsonPrimitive containing the ISO-8601 string
     */
    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.toString());
    }
  }

  /**
   * Deserializer for {@link Instant} from ISO-8601 strings.
   */
  private static class InstantDeserializer implements JsonDeserializer<Instant> {
    /**
     * Deserializes an ISO-8601 string to an Instant.
     *
     * @param json the JSON element to deserialize
     * @param typeOfT the type of the target object
     * @param context the deserialization context
     * @return the deserialized Instant
     * @throws JsonParseException if the string cannot be parsed
     */
    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return Instant.parse(json.getAsString());
    }
  }
}

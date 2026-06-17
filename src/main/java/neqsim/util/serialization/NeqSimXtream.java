package neqsim.util.serialization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.AnyTypePermission;

/**
 * NeqSimXtream class for serializing and deserializing NeqSim objects.
 *
 * <p>
 * Provides compressed XML serialization using XStream with ZIP compression. The resulting .neqsim
 * files are compact and portable.
 * </p>
 *
 * <p>
 * Features:
 * <ul>
 * <li>Automatic ThreadLocal field exclusion</li>
 * <li>ZIP compression for compact storage</li>
 * <li>Full object graph preservation with ID references</li>
 * <li>Graceful handling of unknown fields/classes for cross-version compatibility</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class NeqSimXtream {
  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(NeqSimXtream.class);

  /**
   * Opens and deserializes an object from a compressed .neqsim file.
   *
   * @param filename the path to the .neqsim file
   * @return the deserialized object
   * @throws IOException if the file cannot be read or is not a valid .neqsim file
   * @throws FileNotFoundException if the file does not exist or process.xml is not found in ZIP
   */
  public static Object openNeqsim(String filename) throws IOException {
    File file = new File(filename);
    if (!file.exists()) {
      throw new FileNotFoundException("File not found: " + filename);
    }
    if (!file.canRead()) {
      throw new IOException("Cannot read file (permission denied): " + filename);
    }

    XStream xstream = createConfiguredXStream();

    try (BufferedInputStream fin = new BufferedInputStream(new FileInputStream(filename));
        ZipInputStream zin = new ZipInputStream(fin)) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if ("process.xml".equals(entry.getName())) {
          try (InputStreamReader reader = new InputStreamReader(zin, "UTF-8")) {
            Object result = xstream.fromXML(reader);
            logger.debug("Successfully loaded object from: " + filename);
            return result;
          }
        }
      }
      throw new FileNotFoundException("process.xml not found in zip file: " + filename);
    } catch (ExceptionInInitializerError e) {
      throw new IOException(
          "Failed to deserialize: a class static initializer failed. "
              + "Ensure JVM has --add-opens flags for java.base modules. Caused by: "
              + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()),
          e);
    } catch (NoClassDefFoundError e) {
      throw new IOException(
          "Failed to deserialize: required class not found: " + e.getMessage(), e);
    }
  }

  /**
   * Saves an object to a compressed .neqsim file.
   *
   * <p>
   * The object is serialized to XML using XStream and compressed using ZIP compression.
   * </p>
   *
   * @param javaobject the object to serialize (typically ProcessSystem or ProcessModel)
   * @param filename the path to save to (recommended extension: .neqsim)
   * @return true if save was successful, false otherwise
   */
  public static boolean saveNeqsim(Object javaobject, String filename) {
    if (javaobject == null) {
      logger.error("Cannot save null object");
      return false;
    }
    if (filename == null || filename.trim().isEmpty()) {
      logger.error("Invalid filename: " + filename);
      return false;
    }

    // Ensure parent directory exists
    File file = new File(filename);
    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.error("Failed to create directory: " + parentDir.getAbsolutePath());
        return false;
      }
    }

    XStream xstream = createConfiguredXStream();

    try (BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(filename));
        ZipOutputStream zout = new ZipOutputStream(fout);
        OutputStreamWriter writer = new OutputStreamWriter(zout, "UTF-8")) {
      ZipEntry entry = new ZipEntry("process.xml");
      zout.putNextEntry(entry);

      xstream.toXML(javaobject, writer);
      writer.flush();

      zout.closeEntry();
      logger.debug("Successfully saved object to: " + filename);
      return true;
    } catch (Exception e) {
      logger.error("Error saving to file: " + filename, e);
      return false;
    }
  }

  /**
   * Creates a configured XStream instance with sensible defaults for NeqSim serialization.
   *
   * <p>
   * Uses the default reflection provider (sun.misc.Unsafe when available) to avoid
   * ExceptionInInitializerError that occurs with PureJavaReflectionProvider on JDK 11+. Includes
   * mapper wrappers to skip ThreadLocal fields and gracefully ignore unknown elements for
   * cross-version compatibility.
   * </p>
   *
   * @return a configured XStream instance
   */
  private static XStream createConfiguredXStream() {
    XStream xstream = new XStream() {
      @Override
      protected MapperWrapper wrapMapper(MapperWrapper next) {
        return new ThreadLocalSkipMapper(new UnknownElementIgnorer(next));
      }
    };
    xstream.setMode(XStream.ID_REFERENCES);
    xstream.addPermission(AnyTypePermission.ANY);
    xstream.allowTypesByWildcard(new String[] {"neqsim.**"});
    return xstream;
  }

  /**
   * Mapper that skips ThreadLocal fields during serialization. ThreadLocal fields cannot be
   * serialized and would cause errors.
   */
  private static class ThreadLocalSkipMapper extends MapperWrapper {
    /**
     * Creates a ThreadLocalSkipMapper.
     *
     * @param wrapped the wrapped mapper
     */
    ThreadLocalSkipMapper(MapperWrapper wrapped) {
      super(wrapped);
    }

    @Override
    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
      if (isThreadLocalField(definedIn, fieldName)) {
        return false;
      }
      return super.shouldSerializeMember(definedIn, fieldName);
    }

    private boolean isThreadLocalField(Class<?> definedIn, String fieldName) {
      Class<?> clazz = definedIn;
      while (clazz != null) {
        try {
          java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
          return ThreadLocal.class.isAssignableFrom(field.getType());
        } catch (NoSuchFieldException e) {
          clazz = clazz.getSuperclass();
        }
      }
      return false;
    }
  }

  /**
   * Mapper that silently ignores unknown elements during deserialization. This allows .neqsim files
   * saved with newer versions (containing additional fields or classes) to be loaded by older
   * versions without errors.
   */
  private static class UnknownElementIgnorer extends MapperWrapper {
    /**
     * Creates an UnknownElementIgnorer.
     *
     * @param wrapped the wrapped mapper
     */
    UnknownElementIgnorer(MapperWrapper wrapped) {
      super(wrapped);
    }

    @Override
    public Class realClass(String elementName) {
      try {
        return super.realClass(elementName);
      } catch (CannotResolveClassException e) {
        logger.debug("Ignoring unknown class during deserialization: " + elementName);
        return null;
      }
    }

    @Override
    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
      if (definedIn == null) {
        return false;
      }
      return super.shouldSerializeMember(definedIn, fieldName);
    }
  }
}

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
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
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
    xstream.addPermission(AnyTypePermission.ANY);

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
    xstream.allowTypesByWildcard(new String[] {"neqsim.**"});

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

  private static XStream createConfiguredXStream() {
    XStream xstream = new XStream(new PureJavaReflectionProvider()) {
      @Override
      protected MapperWrapper wrapMapper(MapperWrapper next) {
        return new MapperWrapper(next) {
          @Override
          public boolean shouldSerializeMember(Class definedIn, String fieldName) {
            // Skip ThreadLocal fields - they cannot be serialized
            try {
              java.lang.reflect.Field field = definedIn.getDeclaredField(fieldName);
              if (ThreadLocal.class.isAssignableFrom(field.getType())) {
                return false;
              }
            } catch (NoSuchFieldException e) {
              // Field not found in this class, check parent classes
              Class<?> parent = definedIn.getSuperclass();
              while (parent != null) {
                try {
                  java.lang.reflect.Field field = parent.getDeclaredField(fieldName);
                  if (ThreadLocal.class.isAssignableFrom(field.getType())) {
                    return false;
                  }
                  break;
                } catch (NoSuchFieldException ex) {
                  parent = parent.getSuperclass();
                }
              }
            }
            return super.shouldSerializeMember(definedIn, fieldName);
          }
        };
      }
    };
    xstream.setMode(XStream.ID_REFERENCES);
    xstream.addPermission(AnyTypePermission.ANY);
    return xstream;
  }
}

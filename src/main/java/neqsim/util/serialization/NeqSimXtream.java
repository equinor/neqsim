package neqsim.util.serialization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.security.AnyTypePermission;

/**
 * <p>
 * NeqSimXtream class.
 * </p>
 *
 * @author esol
 */
public class NeqSimXtream {
  /**
   * <p>
   * openNeqsim.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   * @throws java.io.IOException if any.
   */
  public static Object openNeqsim(String filename) throws IOException {
    XStream xstream = createConfiguredXStream();
    xstream.addPermission(AnyTypePermission.ANY);

    try (BufferedInputStream fin = new BufferedInputStream(new FileInputStream(filename));
        ZipInputStream zin = new ZipInputStream(fin)) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if ("process.xml".equals(entry.getName())) {
          try (InputStreamReader reader = new InputStreamReader(zin, "UTF-8")) {
            return xstream.fromXML(reader);
          }
        }
      }
      throw new FileNotFoundException("process.xml not found in zip file.");
    }
  }

  /**
   * <p>
   * saveNeqsim.
   * </p>
   *
   * @param javaobject a {@link java.lang.Object} object
   * @param filename a {@link java.lang.String} object
   * @return a boolean
   */
  public static boolean saveNeqsim(Object javaobject, String filename) {
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
      // writer and zout will be closed automatically by try-with-resources
      return true;
    } catch (Exception e) {
      System.err.println("[saveNeqsim] Error saving file: " + e);
      return false;
    }
  }

  private static XStream createConfiguredXStream() {
    XStream xstream = new XStream(new PureJavaReflectionProvider());
    XStream.setupDefaultSecurity(xstream);
    return xstream;
  }
}

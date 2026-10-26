package neqsim.util.serialization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.ReflectionProviderWrapper;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.core.util.HierarchicalStreams;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.ReaderWrapper;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.AnyTypePermission;

/**
 * NeqSimXtream class for serializing and deserializing NeqSim objects.
 *
 * <p>
 * Provides compressed XML serialization using XStream with ZIP compression. The resulting .neqsim files are compact and
 * portable.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic ThreadLocal field exclusion</li>
 * <li>ZIP compression for compact storage, written atomically (a failed save never leaves a truncated file)</li>
 * <li>Full object graph preservation with ID references</li>
 * <li>A {@code neqsim-meta.properties} entry recording the NeqSim and Java versions that wrote the file</li>
 * <li>Cross-version loading: unknown classes and fields are skipped, renamed fields are mapped, and files written
 * without the metadata entry (NeqSim versions before it was introduced) are loaded in legacy mode</li>
 * </ul>
 *
 * <p>
 * XStream creates objects without running constructors, so a field added to a class after a file was written would
 * otherwise keep its JVM default (null, 0 or false) instead of its declared initial value. In legacy mode every field
 * that is absent from the stored XML is restored from a freshly constructed template instance of the same class, and
 * classes that gained a private {@code readObject} hook can still be read from the plain field layout used by older
 * versions; the hook is then run with a no-op {@link ObjectInputStream} so its post-load repairs apply. What was
 * skipped, mapped and restored is available from {@link #getLastLoadReport()}.
 * </p>
 *
 * @author esol
 * @version 1.1
 */
public class NeqSimXtream {
  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(NeqSimXtream.class);

  /** Name of the ZIP entry holding the serialized object graph. */
  public static final String PROCESS_ENTRY = "process.xml";

  /** Name of the ZIP entry holding file metadata (writer versions, save time). */
  public static final String META_ENTRY = "neqsim-meta.properties";

  /** Format version written to the metadata entry. */
  public static final String FORMAT_VERSION = "2";

  /** Fields renamed since older file versions: old serialized name to current field name. */
  private static final Map<String, String> RENAMED_FIELDS;

  static {
    Map<String, String> renamed = new HashMap<String, String>();
    renamed.put("tagName", "tagNumber");
    RENAMED_FIELDS = Collections.unmodifiableMap(renamed);
  }

  /** Report of the most recent load on the calling thread. */
  private static final ThreadLocal<LoadReport> LAST_LOAD_REPORT = new ThreadLocal<LoadReport>();

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

    Properties meta = null;
    byte[] processXml = null;
    try (BufferedInputStream fin = new BufferedInputStream(new FileInputStream(filename));
        ZipInputStream zin = new ZipInputStream(fin)) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if (META_ENTRY.equals(entry.getName())) {
          meta = new Properties();
          meta.load(new InputStreamReader(zin, "UTF-8"));
        } else if (PROCESS_ENTRY.equals(entry.getName())) {
          processXml = readFully(zin);
        }
      }
    }
    if (processXml == null) {
      throw new FileNotFoundException(PROCESS_ENTRY + " not found in zip file: " + filename);
    }

    LoadReport report = new LoadReport(filename, meta);
    XStream xstream = createConfiguredXStream(report);
    try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(processXml), "UTF-8")) {
      Object result = xstream.fromXML(reader);
      LAST_LOAD_REPORT.set(report);
      report.log();
      return result;
    } catch (ExceptionInInitializerError e) {
      throw new IOException("Failed to deserialize: a class static initializer failed. "
          + "Ensure JVM has --add-opens flags for java.base modules. Caused by: "
          + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
    } catch (NoClassDefFoundError e) {
      throw new IOException("Failed to deserialize: required class not found: " + e.getMessage(), e);
    }
  }

  /**
   * Returns the report of the most recent {@link #openNeqsim(String)} call on the calling thread.
   *
   * @return the last load report, or null if nothing has been loaded on this thread
   */
  public static LoadReport getLastLoadReport() {
    return LAST_LOAD_REPORT.get();
  }

  /**
   * Saves an object to a compressed .neqsim file.
   *
   * <p>
   * The object is serialized to XML using XStream and compressed using ZIP compression. The file is first written to a
   * temporary file in the same directory and moved into place only after serialization succeeded, so a failed save
   * leaves any existing file untouched and never leaves a truncated file behind.
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
      logger.error("Invalid filename: {}", filename);
      return false;
    }

    File file = new File(filename).getAbsoluteFile();
    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
      logger.error("Failed to create directory: {}", parentDir.getAbsolutePath());
      return false;
    }

    XStream xstream = createConfiguredXStream(null);
    File tempFile = null;
    try {
      tempFile = File.createTempFile(file.getName() + ".", ".tmp", parentDir);
      try (BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(tempFile));
          ZipOutputStream zout = new ZipOutputStream(fout)) {
        zout.putNextEntry(new ZipEntry(META_ENTRY));
        createMetadata(javaobject).store(new OutputStreamWriter(zout, "UTF-8"), "NeqSim file metadata");
        zout.closeEntry();

        zout.putNextEntry(new ZipEntry(PROCESS_ENTRY));
        OutputStreamWriter writer = new OutputStreamWriter(zout, "UTF-8");
        xstream.toXML(javaobject, writer);
        writer.flush();
        zout.closeEntry();
      }
      moveIntoPlace(tempFile, file);
      logger.debug("Successfully saved object to: {}", filename);
      return true;
    } catch (Exception e) {
      logger.error("Error saving to file: {}", filename, e);
      return false;
    } finally {
      if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
        tempFile.deleteOnExit();
      }
    }
  }

  /**
   * Moves a completed temporary file onto the target path, atomically when the file system supports it.
   *
   * @param source the completed temporary file
   * @param target the destination file
   * @throws IOException if the move fails
   */
  private static void moveIntoPlace(File source, File target) throws IOException {
    try {
      Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Builds the metadata stored next to the serialized object graph.
   *
   * @param javaobject the object being saved
   * @return the metadata properties
   */
  private static Properties createMetadata(Object javaobject) {
    Properties meta = new Properties();
    meta.setProperty("formatVersion", FORMAT_VERSION);
    meta.setProperty("neqsimVersion", getNeqSimVersion());
    meta.setProperty("javaVersion", System.getProperty("java.version", "unknown"));
    meta.setProperty("savedAtEpochMillis", Long.toString(System.currentTimeMillis()));
    meta.setProperty("rootClass", javaobject.getClass().getName());
    return meta;
  }

  /**
   * Returns the NeqSim version from the JAR manifest.
   *
   * @return the implementation version, or "unknown" when not running from a packaged JAR
   */
  private static String getNeqSimVersion() {
    Package pkg = NeqSimXtream.class.getPackage();
    String version = pkg == null ? null : pkg.getImplementationVersion();
    return version == null ? "unknown" : version;
  }

  /**
   * Reads an input stream to its end.
   *
   * @param in the stream to read
   * @return the bytes read
   * @throws IOException if reading fails
   */
  private static byte[] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[65536];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /**
   * Creates a configured XStream instance with sensible defaults for NeqSim serialization.
   *
   * <p>
   * Uses the default reflection provider (sun.misc.Unsafe when available) to avoid ExceptionInInitializerError that
   * occurs with PureJavaReflectionProvider on JDK 11+. Includes mapper wrappers to skip ThreadLocal fields and to
   * tolerate classes, fields and renamed fields from other versions, plus a converter for NeqSim classes that reads
   * both the current and the legacy field layout.
   * </p>
   *
   * @param report load report to fill, or null when the instance is used for saving
   * @return a configured XStream instance
   */
  private static XStream createConfiguredXStream(final LoadReport report) {
    XStream xstream = new XStream() {
      @Override
      protected MapperWrapper wrapMapper(MapperWrapper next) {
        return new ThreadLocalSkipMapper(new CompatibilityMapper(next, report));
      }
    };
    xstream.setMode(XStream.ID_REFERENCES);
    xstream.addPermission(AnyTypePermission.ANY);
    xstream.allowTypesByWildcard(new String[] {"neqsim.**"});

    FieldWriteRecorder recorder = new FieldWriteRecorder(xstream.getReflectionProvider());
    SerializableConverter serializableConverter = new SerializableConverter(xstream.getMapper(), recorder,
        xstream.getClassLoaderReference());
    xstream.registerConverter(new NeqSimObjectConverter(xstream.getMapper(), recorder, serializableConverter, report),
        XStream.PRIORITY_NORMAL);
    return xstream;
  }

  /**
   * Mapper that skips ThreadLocal fields during serialization. ThreadLocal fields cannot be serialized and would cause
   * errors.
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

    /** {@inheritDoc} */
    @Override
    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
      if (isThreadLocalField(definedIn, fieldName)) {
        return false;
      }
      return super.shouldSerializeMember(definedIn, fieldName);
    }

    /**
     * Checks whether a field is a ThreadLocal.
     *
     * @param definedIn the class to start the search in
     * @param fieldName the field name
     * @return true if the field exists and is a ThreadLocal
     */
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
   * Mapper that tolerates content written by other NeqSim versions: unknown classes resolve to null and renamed fields
   * are mapped to their current names. Everything mapped is recorded in the load report.
   */
  private static class CompatibilityMapper extends MapperWrapper {
    /** Load report to fill; null when saving. */
    private final LoadReport report;

    /**
     * Creates a CompatibilityMapper.
     *
     * @param wrapped the wrapped mapper
     * @param report the load report to fill, or null
     */
    CompatibilityMapper(MapperWrapper wrapped, LoadReport report) {
      super(wrapped);
      this.report = report;
    }

    /** {@inheritDoc} */
    @Override
    public Class realClass(String elementName) {
      try {
        return super.realClass(elementName);
      } catch (CannotResolveClassException e) {
        logger.debug("Ignoring unknown class during deserialization: {}", elementName);
        if (report != null) {
          report.count(report.unknownClasses, elementName);
        }
        return null;
      }
    }

    /** {@inheritDoc} */
    @Override
    public String realMember(Class type, String serialized) {
      String member = super.realMember(type, serialized);
      String renamed = RENAMED_FIELDS.get(member);
      if (renamed != null && type != null && findField(type, member) == null && findField(type, renamed) != null) {
        if (report != null) {
          report.count(report.renamedFields, type.getName() + "." + member + " -> " + renamed);
        }
        return renamed;
      }
      return member;
    }

    /**
     * Skips removed and transient fields while reading either XML layout. The custom SerializableConverter asks
     * directly about the declaring class rather than walking the hierarchy to {@code Object}.
     *
     * @param definedIn the class declaring the member
     * @param fieldName the member name
     * @return false for absent or transient NeqSim fields, otherwise the wrapped mapper's answer
     */
    @Override
    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
      if (definedIn == null) {
        return false;
      }
      Field field = report != null && definedIn.getName().startsWith("neqsim.") ? findField(definedIn, fieldName)
          : null;
      if (definedIn == Object.class || (report != null && definedIn.getName().startsWith("neqsim.")
          && (field == null || Modifier.isTransient(field.getModifiers())))) {
        logger.debug("Ignoring absent or transient field during deserialization: {}", fieldName);
        if (report != null) {
          report.count(report.ignoredElements, fieldName);
        }
        return false;
      }
      return super.shouldSerializeMember(definedIn, fieldName);
    }
  }

  /**
   * Reflection provider wrapper that records which fields XStream writes into each object while it is being read, and
   * never writes transient fields (older files may contain values for fields that are now transient caches).
   */
  private static class FieldWriteRecorder extends ReflectionProviderWrapper {
    /** Fields written per object currently being unmarshalled. */
    private final Map<Object, Set<String>> written = new IdentityHashMap<Object, Set<String>>();

    /** Cache of whether a field key refers to a transient field. */
    private final Map<String, Boolean> transientFields = new HashMap<String, Boolean>();

    /** Whether recording is active. */
    private boolean recording = false;

    /**
     * Creates a FieldWriteRecorder.
     *
     * @param wrapped the reflection provider to delegate to
     */
    FieldWriteRecorder(ReflectionProvider wrapped) {
      super(wrapped);
    }

    /** {@inheritDoc} */
    @Override
    public void writeField(Object object, String fieldName, Object value, Class definedIn) {
      String key = fieldKey(definedIn, fieldName);
      if (isTransient(key, definedIn == null ? object.getClass() : definedIn, fieldName)) {
        return;
      }
      if (recording) {
        fieldsFor(object).add(key);
      }
      super.writeField(object, fieldName, value, definedIn);
    }

    /**
     * Returns the field keys already populated or restored for an object.
     *
     * @param object the object being read
     * @return its mutable field-key set
     */
    Set<String> fieldsFor(Object object) {
      Set<String> fields = written.get(object);
      if (fields == null) {
        fields = new HashSet<String>();
        written.put(object, fields);
      }
      return fields;
    }

    /**
     * Checks, with caching, whether a field is transient.
     *
     * @param key the field key
     * @param type the class to start the search in
     * @param fieldName the field name
     * @return true if the field exists and is transient
     */
    private boolean isTransient(String key, Class<?> type, String fieldName) {
      Boolean cached = transientFields.get(key);
      if (cached == null) {
        Field field = findField(type, fieldName);
        cached = field != null && Modifier.isTransient(field.getModifiers());
        transientFields.put(key, cached);
      }
      return cached;
    }

    /**
     * Returns and forgets the fields recorded for an object.
     *
     * @param object the unmarshalled object
     * @return the recorded field keys, never null
     */
    Set<String> take(Object object) {
      Set<String> fields = written.remove(object);
      return fields == null ? Collections.<String>emptySet() : fields;
    }
  }

  /**
   * Converter for NeqSim classes. Writes exactly as the default XStream converters do (custom layout for classes with
   * readObject/writeObject, plain field layout otherwise) and reads both layouts. For legacy files it restores the
   * initial value of every field missing from the XML and runs private readObject hooks.
   */
  private static class NeqSimObjectConverter extends ReflectionConverter {
    /** Converter used for the custom layout of classes with readObject/writeObject. */
    private final SerializableConverter serializableConverter;

    /** Records fields written during unmarshalling. */
    private final FieldWriteRecorder recorder;

    /** Load report, null when saving. */
    private final LoadReport report;

    /** Classes for which no template instance could be constructed during this load. */
    private final Set<Class<?>> classesWithoutTemplate = new HashSet<Class<?>>();

    /** Cache of instance fields per class. */
    private final Map<Class<?>, List<Field>> fieldCache = new HashMap<Class<?>, List<Field>>();

    /** At most one constructor template per object while its class hierarchy is being read. */
    private final Map<Object, Object> templates = new IdentityHashMap<Object, Object>();

    /**
     * Creates a NeqSimObjectConverter.
     *
     * @param mapper the XStream mapper
     * @param recorder the recording reflection provider
     * @param serializableConverter converter for the custom readObject/writeObject layout
     * @param report the load report, or null when saving
     */
    NeqSimObjectConverter(Mapper mapper, FieldWriteRecorder recorder, SerializableConverter serializableConverter,
        LoadReport report) {
      super(mapper, recorder);
      this.recorder = recorder;
      this.serializableConverter = serializableConverter;
      this.report = report;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canConvert(Class type) {
      if (type == null || !type.getName().startsWith("neqsim.") || type.isArray() || type.isInterface() || type.isEnum()
          || Enum.class.isAssignableFrom(type) || Externalizable.class.isAssignableFrom(type)
          || Proxy.isProxyClass(type) || type.isSynthetic() || type.getName().contains("$$Lambda")) {
        return false;
      }
      return super.canConvert(type);
    }

    /** {@inheritDoc} */
    @Override
    public void marshal(Object original, HierarchicalStreamWriter writer, MarshallingContext context) {
      if (serializableConverter.canConvert(original.getClass())) {
        serializableConverter.marshal(original, writer, context);
      } else {
        super.marshal(original, writer, context);
      }
    }

    /** {@inheritDoc} */
    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
      Class<?> type = context.getRequiredType();
      String layout = reader.getAttribute(mapper.aliasForSystemAttribute("serialization"));
      boolean custom = "custom".equals(layout) && serializableConverter.canConvert(type);
      boolean legacy = report != null && report.isLegacyFormat();
      Object instance = instantiateNewInstance(reader, context);
      recorder.recording = legacy;
      try {
        Object result = custom
            ? serializableConverter.doUnmarshal(instance, legacy ? restoringReader(reader, instance) : reader, context)
            : doUnmarshal(instance, reader, context);
        if (legacy) {
          Object template = restoreMissingFields(result, recorder.fieldsFor(result), null);
          if (template != null && result instanceof LegacyLoadRepairable) {
            try {
              ((LegacyLoadRepairable) result).repairAfterLegacyLoad(template);
            } catch (RuntimeException e) {
              report.count(report.readObjectHookFailures, result.getClass().getName() + ".repairAfterLegacyLoad");
              throw new ConversionException("Legacy repair failed for " + result.getClass().getName(), e);
            }
          }
          if (!custom && serializableConverter.canConvert(result.getClass())) {
            runReadObjectHooks(result);
          }
        }
        return serializationMembers.callReadResolve(result);
      } finally {
        recorder.take(instance);
        templates.remove(instance);
      }
    }

    /**
     * Restores each custom default block before defaultReadObject returns to the class's hook. This preserves any
     * dependent values calculated by that hook and avoids invoking a custom readObject twice.
     *
     * @param reader the original reader, positioned at the custom object
     * @param result the object being populated
     * @return a reader that observes only this object's class and default-block boundaries
     */
    private HierarchicalStreamReader restoringReader(HierarchicalStreamReader reader, final Object result) {
      return new ReaderWrapper(reader) {
        /** Depth within this object's custom layout; nested objects use their own converters. */
        private int depth;

        /** Class whose default fields are currently being read. */
        private Class<?> declaringClass;

        /** {@inheritDoc} */
        @Override
        public void moveDown() {
          super.moveDown();
          depth++;
          if (depth == 1 && !"unserializable-parents".equals(getNodeName())) {
            declaringClass = HierarchicalStreams.readClassType(this, mapper);
          }
        }

        /** {@inheritDoc} */
        @Override
        public void moveUp() {
          if (depth == 2 && "default".equals(getNodeName()) && declaringClass != null) {
            restoreMissingFields(result, recorder.fieldsFor(result), declaringClass);
          }
          super.moveUp();
          depth--;
        }
      };
    }

    /**
     * Returns the cached instance fields of a class.
     *
     * @param type the class
     * @return the instance fields
     */
    private List<Field> cachedFields(Class<?> type) {
      List<Field> fields = fieldCache.get(type);
      if (fields == null) {
        fields = instanceFields(type);
        fieldCache.put(type, fields);
      }
      return fields;
    }

    /**
     * Restores the declared initial value of fields that were not present in the XML, taking them from a freshly
     * constructed template instance of the same class.
     *
     * @param result the unmarshalled object
     * @param written field keys already populated or restored; newly considered fields are added to avoid overwriting
     * hook repairs during the final pass
     * @param declaringClass the class whose default block just ended, or null for the final pass
     * @return the constructor template, or null if none was needed or could be constructed
     */
    private Object restoreMissingFields(Object result, Set<String> written, Class<?> declaringClass) {
      List<Field> missing = new ArrayList<Field>();
      for (Field field : cachedFields(result.getClass())) {
        if ((declaringClass != null && field.getDeclaringClass() != declaringClass)
            || written.contains(fieldKey(field.getDeclaringClass(), field.getName()))
            || !mapper.shouldSerializeMember(field.getDeclaringClass(), field.getName())) {
          continue;
        }
        try {
          if (field.getType().isPrimitive() || field.get(result) == null) {
            missing.add(field);
            written.add(fieldKey(field.getDeclaringClass(), field.getName()));
          }
        } catch (IllegalAccessException e) {
          logger.debug("Cannot read field {}: {}", field, e.getMessage());
        }
      }
      if (missing.isEmpty()) {
        return templates.get(result);
      }
      Object template = templates.get(result);
      if (template == null) {
        template = createTemplate(result);
        if (template != null) {
          templates.put(result, template);
        }
      }
      if (template == null) {
        for (Field field : missing) {
          report.count(report.fieldsWithoutTemplate, describe(field));
        }
        return null;
      }
      for (Field field : missing) {
        try {
          Object current = field.get(result);
          Object value = field.get(template);
          if (field.getType().isPrimitive()) {
            if (!value.equals(current)) {
              field.set(result, value);
              report.count(report.restoredFields, describe(field));
            }
          } else if (value != null) {
            if (isSafeToTransplant(value, template)) {
              field.set(result, value);
              report.count(report.restoredFields, describe(field));
            } else {
              Object empty = emptyContainerLike(value);
              if (empty != null) {
                field.set(result, empty);
                report.count(report.restoredFields, describe(field));
                report.count(report.restoredAsEmpty, describe(field));
              } else {
                report.count(report.fieldsWithoutTemplate, describe(field));
              }
            }
          }
        } catch (IllegalAccessException e) {
          report.count(report.fieldsWithoutTemplate, describe(field));
        }
      }
      return template;
    }

    /**
     * Constructs a template instance whose field initializers supply default values.
     *
     * @param loaded the loaded object the template is for
     * @return the template, or null if no constructor could be used
     */
    private Object createTemplate(Object loaded) {
      Class<?> type = loaded.getClass();
      if (classesWithoutTemplate.contains(type) || Modifier.isAbstract(type.getModifiers())) {
        return null;
      }
      List<Object[]> attempts = new ArrayList<Object[]>();
      attempts.add(new Object[] {new Class<?>[0], new Object[0]});
      attempts.add(new Object[] {new Class<?>[] {String.class}, new Object[] {"template"}});
      if (loaded instanceof neqsim.thermo.component.ComponentInterface) {
        String componentName = ((neqsim.thermo.component.ComponentInterface) loaded).getComponentName();
        attempts.add(new Object[] {new Class<?>[] {String.class, double.class, double.class, int.class},
            new Object[] {componentName, 1.0, 1.0, 0}});
      }
      for (Object[] attempt : attempts) {
        try {
          Constructor<?> constructor = type.getDeclaredConstructor((Class<?>[]) attempt[0]);
          constructor.setAccessible(true);
          return constructor.newInstance((Object[]) attempt[1]);
        } catch (NoSuchMethodException e) {
          continue;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | RuntimeException
            | LinkageError e) {
          logger.debug("Template constructor failed for {}: {}", type.getName(), e.toString());
        }
      }
      classesWithoutTemplate.add(type);
      return null;
    }

    /**
     * Runs the private readObject hooks of the class hierarchy, superclass first, with a no-op object input stream so
     * that post-deserialization repairs apply to objects read from the legacy plain layout.
     *
     * @param result the unmarshalled object
     * @throws ConversionException if a hook rejects the loaded state or cannot complete
     */
    private void runReadObjectHooks(Object result) {
      List<Class<?>> hierarchy = new ArrayList<Class<?>>();
      for (Class<?> type = result.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
        hierarchy.add(0, type);
      }
      for (Class<?> type : hierarchy) {
        Method hook;
        try {
          hook = type.getDeclaredMethod("readObject", ObjectInputStream.class);
        } catch (NoSuchMethodException e) {
          continue;
        }
        if (!Modifier.isPrivate(hook.getModifiers()) || Modifier.isStatic(hook.getModifiers())) {
          continue;
        }
        try {
          hook.setAccessible(true);
          hook.invoke(result, new NoOpObjectInputStream());
          report.count(report.readObjectHooks, type.getName());
        } catch (Exception e) {
          Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
          logger.debug("readObject hook of {} failed on legacy object: {}", type.getName(), String.valueOf(cause));
          report.count(report.readObjectHookFailures, type.getName());
          throw new ConversionException("Legacy readObject hook failed for " + type.getName(), cause);
        }
      }
    }
  }

  /**
   * Object input stream for invoking readObject hooks outside Java serialization. {@code defaultReadObject} does
   * nothing because the fields have already been set from the XML.
   */
  private static class NoOpObjectInputStream extends ObjectInputStream {
    /**
     * Creates a NoOpObjectInputStream.
     *
     * @throws IOException never in practice; declared by the superclass constructor
     */
    NoOpObjectInputStream() throws IOException {
      super();
    }

    /** {@inheritDoc} */
    @Override
    public void defaultReadObject() {
      // Fields are already populated by XStream.
    }
  }

  /**
   * Returns the non-static fields declared by a class and its superclasses.
   *
   * @param type the class
   * @return the instance fields, made accessible where possible
   */
  private static List<Field> instanceFields(Class<?> type) {
    List<Field> fields = new ArrayList<Field>();
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
            || field.isSynthetic()) {
          continue;
        }
        try {
          field.setAccessible(true);
          fields.add(field);
        } catch (RuntimeException e) {
          logger.debug("Field {} is not accessible: {}", field, e.getMessage());
        }
      }
    }
    return fields;
  }

  /**
   * Finds a field by name in a class or its superclasses.
   *
   * @param type the class to search
   * @param name the field name
   * @return the field, or null if not found
   */
  private static Field findField(Class<?> type, String name) {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        continue;
      }
    }
    return null;
  }

  /**
   * Creates an empty collection or map of the same class as a template value whose entries cannot be reused.
   *
   * @param value the template field value
   * @return an empty instance of the same class, or null if the value is not a collection or map or cannot be created
   */
  private static Object emptyContainerLike(Object value) {
    if (!(value instanceof Collection) && !(value instanceof Map)) {
      return null;
    }
    try {
      Constructor<?> constructor = value.getClass().getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Checks whether a template field value can be moved to the loaded object without dragging along state that belongs
   * to the template (for example an object or a lambda that refers back to the template).
   *
   * @param value the template field value
   * @param template the template instance
   * @return true if the value can be reused by the loaded object
   */
  private static boolean isSafeToTransplant(Object value, Object template) {
    if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character
        || value instanceof Enum) {
      return true;
    }
    Class<?> type = value.getClass();
    if (type.isArray()) {
      if (type.getComponentType().isPrimitive()) {
        return true;
      }
      for (Object element : (Object[]) value) {
        if (element != null) {
          return false;
        }
      }
      return true;
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value).isEmpty();
    }
    if (value instanceof Map) {
      return ((Map<?, ?>) value).isEmpty();
    }
    if (type.getName().startsWith("neqsim.")) {
      return !refersTo(value, template, 3, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
    }
    return false;
  }

  /**
   * Checks whether an object graph refers to a target object within a limited depth.
   *
   * @param object the object to scan
   * @param target the object to look for
   * @param depth remaining depth
   * @param visited objects already scanned
   * @return true if the target is reachable, or if the object cannot be inspected
   */
  private static boolean refersTo(Object object, Object target, int depth, Set<Object> visited) {
    if (object == target) {
      return true;
    }
    if (object == null || depth == 0 || !visited.add(object)) {
      return false;
    }
    if (object instanceof Collection) {
      for (Object element : (Collection<?>) object) {
        if (refersTo(element, target, depth - 1, visited)) {
          return true;
        }
      }
      return false;
    }
    if (object instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
        if (refersTo(entry.getKey(), target, depth - 1, visited)
            || refersTo(entry.getValue(), target, depth - 1, visited)) {
          return true;
        }
      }
      return false;
    }
    Class<?> type = object.getClass();
    if (type.isArray()) {
      if (type.getComponentType().isPrimitive()) {
        return false;
      }
      for (Object element : (Object[]) object) {
        if (refersTo(element, target, depth - 1, visited)) {
          return true;
        }
      }
      return false;
    }
    if (!type.getName().startsWith("neqsim.")) {
      return false;
    }
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
          continue;
        }
        try {
          field.setAccessible(true);
          if (refersTo(field.get(object), target, depth - 1, visited)) {
            return true;
          }
        } catch (IllegalAccessException | RuntimeException e) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Builds the key used to identify a field.
   *
   * @param declaringClass the class declaring the field
   * @param fieldName the field name
   * @return the key
   */
  private static String fieldKey(Class<?> declaringClass, String fieldName) {
    return (declaringClass == null ? "?" : declaringClass.getName()) + "#" + fieldName;
  }

  /**
   * Describes a field as SimpleClassName.field for reports.
   *
   * @param field the field
   * @return the description
   */
  private static String describe(Field field) {
    return field.getDeclaringClass().getName() + "." + field.getName();
  }

  /**
   * Report of what a {@link #openNeqsim(String)} call skipped, mapped and restored to read a file.
   *
   * @author esol
   * @version 1.0
   */
  public static class LoadReport {
    /** File that was loaded. */
    private final String filename;

    /** Metadata entry of the file, or null for files written before metadata was introduced. */
    private final Properties metadata;

    /** Unknown field elements that were skipped, with counts. */
    final Map<String, Integer> ignoredElements = new TreeMap<String, Integer>();

    /** Unknown classes that were skipped, with counts. */
    final Map<String, Integer> unknownClasses = new TreeMap<String, Integer>();

    /** Renamed fields that were mapped, with counts. */
    final Map<String, Integer> renamedFields = new TreeMap<String, Integer>();

    /** Fields restored to their declared initial value, with counts. */
    final Map<String, Integer> restoredFields = new TreeMap<String, Integer>();

    /** Restored collection or map fields whose template entries were not reusable, so an empty one was set. */
    final Map<String, Integer> restoredAsEmpty = new TreeMap<String, Integer>();

    /** Fields missing from the file that could not be restored, with counts. */
    final Map<String, Integer> fieldsWithoutTemplate = new TreeMap<String, Integer>();

    /** readObject hooks run on objects read from the legacy layout, with counts. */
    final Map<String, Integer> readObjectHooks = new TreeMap<String, Integer>();

    /** readObject hooks that failed, with counts. */
    final Map<String, Integer> readObjectHookFailures = new TreeMap<String, Integer>();

    /**
     * Creates a LoadReport.
     *
     * @param filename the file being loaded
     * @param metadata the metadata entry, or null if absent
     */
    LoadReport(String filename, Properties metadata) {
      this.filename = filename;
      this.metadata = metadata;
    }

    /**
     * Increments a counter.
     *
     * @param counters the counter map
     * @param key the key to increment
     */
    void count(Map<String, Integer> counters, String key) {
      Integer old = counters.get(key);
      counters.put(key, old == null ? 1 : old + 1);
    }

    /**
     * Returns the loaded file name.
     *
     * @return the file name
     */
    public String getFilename() {
      return filename;
    }

    /**
     * Returns whether the file was written before the metadata entry existed, so legacy repairs were applied.
     *
     * @return true for files without a metadata entry
     */
    public boolean isLegacyFormat() {
      return metadata == null;
    }

    /**
     * Returns the NeqSim version that wrote the file.
     *
     * @return the writer version, "unknown" if not recorded by the writer, or null for legacy files
     */
    public String getWriterVersion() {
      return metadata == null ? null : metadata.getProperty("neqsimVersion", "unknown");
    }

    /**
     * Returns the unknown field elements that were skipped.
     *
     * @return element name to count
     */
    public Map<String, Integer> getIgnoredElements() {
      return Collections.unmodifiableMap(ignoredElements);
    }

    /**
     * Returns the unknown classes that were skipped.
     *
     * @return class name to count
     */
    public Map<String, Integer> getUnknownClasses() {
      return Collections.unmodifiableMap(unknownClasses);
    }

    /**
     * Returns the renamed fields that were mapped to their current names.
     *
     * @return "class.old -&gt; new" to count
     */
    public Map<String, Integer> getRenamedFields() {
      return Collections.unmodifiableMap(renamedFields);
    }

    /**
     * Returns the fields that were missing from the file and restored to their declared initial value.
     *
     * @return "class.field" to count
     */
    public Map<String, Integer> getRestoredFields() {
      return Collections.unmodifiableMap(restoredFields);
    }

    /**
     * Returns the restored collection or map fields that were set empty because the template's entries referred to the
     * template (for example energy ports registered by a constructor).
     *
     * @return "class.field" to count
     */
    public Map<String, Integer> getRestoredAsEmpty() {
      return Collections.unmodifiableMap(restoredAsEmpty);
    }

    /**
     * Returns the fields that were missing from the file and could not be restored; they keep their JVM default.
     *
     * @return "class.field" to count
     */
    public Map<String, Integer> getFieldsWithoutTemplate() {
      return Collections.unmodifiableMap(fieldsWithoutTemplate);
    }

    /**
     * Returns the readObject hooks run on objects read from the legacy layout.
     *
     * @return class name to count
     */
    public Map<String, Integer> getReadObjectHooks() {
      return Collections.unmodifiableMap(readObjectHooks);
    }

    /**
     * Returns the readObject hooks that failed on objects read from the legacy layout.
     *
     * @return class name to count
     */
    public Map<String, Integer> getReadObjectHookFailures() {
      return Collections.unmodifiableMap(readObjectHookFailures);
    }

    /**
     * Returns whether the file loaded without any compatibility handling.
     *
     * @return true if nothing was skipped, mapped or restored
     */
    public boolean isClean() {
      return !isLegacyFormat() && ignoredElements.isEmpty() && unknownClasses.isEmpty() && renamedFields.isEmpty()
          && restoredFields.isEmpty() && fieldsWithoutTemplate.isEmpty();
    }

    /** Logs a summary: a warning when compatibility handling was needed, debug otherwise. */
    void log() {
      if (isClean()) {
        logger.debug("Loaded {} (written by NeqSim {})", filename, getWriterVersion());
        return;
      }
      logger.warn("Loaded {} with cross-version handling: {}", filename, toString());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return "LoadReport{legacyFormat=" + isLegacyFormat() + ", writerVersion=" + getWriterVersion()
          + ", ignoredElements=" + ignoredElements.keySet() + ", unknownClasses=" + unknownClasses.keySet()
          + ", renamedFields=" + renamedFields.keySet() + ", restoredFields=" + restoredFields.size()
          + ", restoredAsEmpty=" + restoredAsEmpty.keySet() + ", fieldsWithoutTemplate=" + fieldsWithoutTemplate.size()
          + ", readObjectHookFailures=" + readObjectHookFailures.keySet() + "}";
    }
  }
}

package neqsim.mcp.runners;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Simulation state persistence for the NeqSim MCP server.
 *
 * <p>
 * Provides save/load/version-track capabilities for simulation sessions. Persists process
 * definitions and results to disk as JSON files that can be shared, version-controlled, and
 * restored across server restarts.
 * </p>
 *
 * <p>
 * Capabilities:
 * <ul>
 * <li>Save session state to versioned JSON files</li>
 * <li>Load and restore sessions from saved files</li>
 * <li>List all saved simulations with metadata</li>
 * <li>Compare two saved versions (diff)</li>
 * <li>Export session as a standalone shareable JSON</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class StatePersistenceRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Default root directory for NeqSim MCP state files. */
  private static final String DEFAULT_ROOT_DIR =
      System.getProperty("user.home") + File.separator + ".neqsim";

  /** System property allowing storage directories outside the NeqSim state root. */
  private static final String ALLOW_EXTERNAL_DIR_PROPERTY = "neqsim.mcp.allowExternalStateDir";

  /** Environment variable allowing storage directories outside the NeqSim state root. */
  private static final String ALLOW_EXTERNAL_DIR_ENV = "NEQSIM_MCP_ALLOW_EXTERNAL_STATE_DIR";

  /** Default directory for saved simulations. */
  private static volatile String storageDir =
      DEFAULT_ROOT_DIR + File.separator + "saved_simulations";

  /**
   * Private constructor — all methods are static.
   */
  private StatePersistenceRunner() {}

  /**
   * Main entry point for state persistence operations.
   *
   * @param json JSON with action and parameters
   * @return JSON with results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "save":
          return saveState(input);
        case "load":
          return loadState(input);
        case "list":
          return listSaved(input);
        case "delete":
          return deleteSaved(input);
        case "compare":
          return compareVersions(input);
        case "export":
          return exportSession(input);
        case "setStorageDir":
          return setStorageDirectory(input);
        case "getInfo":
          return getInfo();
        default:
          return errorJson("UNKNOWN_ACTION", "Unknown persistence action: " + action,
              "Use: save, load, list, delete, compare, export, setStorageDir, getInfo");
      }
    } catch (Exception e) {
      return errorJson("PERSISTENCE_ERROR", e.getMessage(), "Check JSON format and file paths");
    }
  }

  /**
   * Saves the current state of a session to a versioned JSON file.
   *
   * @param input JSON with sessionId, optional name and description
   * @return JSON confirmation with file path
   */
  private static String saveState(JsonObject input) {
    String sessionId = input.has("sessionId") ? input.get("sessionId").getAsString() : "";
    String name = input.has("name") ? input.get("name").getAsString() : sessionId;
    String description = input.has("description") ? input.get("description").getAsString() : "";
    String version = input.has("version") ? input.get("version").getAsString() : "1.0";

    if (sessionId.isEmpty()) {
      return errorJson("MISSING_SESSION", "sessionId is required",
          "Provide the sessionId from an active session");
    }

    // Get the session state from SessionRunner by requesting it
    String sessionState =
        SessionRunner.run("{\"action\": \"getState\", \"sessionId\": \"" + sessionId + "\"}");

    JsonObject stateObj = JsonParser.parseString(sessionState).getAsJsonObject();
    if (stateObj.has("status") && "error".equals(stateObj.get("status").getAsString())) {
      return sessionState; // Forward error
    }

    // Build the save envelope
    JsonObject saveEnvelope = new JsonObject();
    saveEnvelope.addProperty("format", "neqsim-saved-state");
    saveEnvelope.addProperty("formatVersion", "1.0.0");
    saveEnvelope.addProperty("name", name);
    saveEnvelope.addProperty("description", description);
    saveEnvelope.addProperty("version", version);
    saveEnvelope.addProperty("sessionId", sessionId);
    saveEnvelope.addProperty("savedAt", Instant.now().toString());
    saveEnvelope.addProperty("neqsimVersion", "3.10.0");
    saveEnvelope.add("sessionState", stateObj);

    // If input has processDefinition (raw JSON that built the process), include it
    if (input.has("processDefinition")) {
      saveEnvelope.add("processDefinition", input.get("processDefinition"));
    }

    // Write to disk
    try {
      ensureStorageDir();

      // Use sanitized name for filename
      String safeName = sanitizeFilename(name);
      String filename = safeName + "_v" + version + ".json";
      Path filePath = resolveStorageFile(filename);

      // Don't overwrite — append incrementing suffix
      int suffix = 1;
      while (Files.exists(filePath)) {
        filename = safeName + "_v" + version + "_" + suffix + ".json";
        filePath = resolveStorageFile(filename);
        suffix++;
      }

      try (FileWriter writer = new FileWriter(filePath.toFile())) {
        GSON.toJson(saveEnvelope, writer);
      }

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("filePath", filePath.toString());
      response.addProperty("filename", filename);
      response.addProperty("name", name);
      response.addProperty("version", version);
      response.addProperty("fileSize", Files.size(filePath));
      return GSON.toJson(response);

    } catch (IOException e) {
      return errorJson("SAVE_FAILED", "Failed to save state: " + e.getMessage(),
          "Check write permissions for: " + storageDir);
    }
  }

  /**
   * Loads a saved simulation state and creates a new session from it.
   *
   * @param input JSON with filename (or filePath)
   * @return JSON with new sessionId and restored state
   */
  private static String loadState(JsonObject input) {
    String filename = input.has("filename") ? input.get("filename").getAsString() : "";
    String filePath = input.has("filePath") ? input.get("filePath").getAsString() : "";

    Path path;
    if (filePath.isEmpty() && filename.isEmpty()) {
      return errorJson("MISSING_FILE", "filename or filePath is required",
          "Use 'list' action to see available saved states");
    }

    try {
      path = resolveReadableStoragePath(filename, filePath);
    } catch (IOException e) {
      return errorJson("INVALID_PATH", e.getMessage(),
          "Use a saved-state filename from the configured storage directory");
    }

    if (!Files.exists(path)) {
      return errorJson("FILE_NOT_FOUND", "File not found: " + path,
          "Use 'list' action to see available saved states");
    }

    try {
      String content;
      try (FileReader reader = new FileReader(path.toFile())) {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
          sb.append(buffer, 0, read);
        }
        content = sb.toString();
      }

      JsonObject envelope = JsonParser.parseString(content).getAsJsonObject();

      // If this has a processDefinition, use it to recreate the session
      if (envelope.has("processDefinition")) {
        // Create session from the process definition
        JsonObject createCmd = new JsonObject();
        createCmd.addProperty("action", "create");
        String savedName = envelope.has("name") ? envelope.get("name").getAsString() : "Restored";
        createCmd.addProperty("name", savedName + " (restored)");
        createCmd.add("processJson", envelope.get("processDefinition"));

        String createResult = SessionRunner.run(GSON.toJson(createCmd));
        JsonObject createObj = JsonParser.parseString(createResult).getAsJsonObject();

        // Add metadata about the restore
        createObj.addProperty("restoredFrom", path.toString());
        createObj.addProperty("originalVersion",
            envelope.has("version") ? envelope.get("version").getAsString() : "unknown");
        createObj.addProperty("originalSavedAt",
            envelope.has("savedAt") ? envelope.get("savedAt").getAsString() : "unknown");
        return GSON.toJson(createObj);
      }

      // Otherwise just return the saved content with metadata
      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("restoredFrom", path.toString());
      response.add("savedState", envelope);
      response.addProperty("note",
          "State loaded. No processDefinition found — session state is informational only.");
      return GSON.toJson(response);

    } catch (IOException e) {
      return errorJson("LOAD_FAILED", "Failed to load state: " + e.getMessage(),
          "Check file exists and is readable");
    }
  }

  /**
   * Lists all saved simulations with metadata.
   *
   * @param input optional JSON with filter criteria
   * @return JSON with list of saved states
   */
  private static String listSaved(JsonObject input) {
    try {
      ensureStorageDir();
      File dir = storageDirectoryPath().toFile();
      File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("storageDir", storageDir);

      JsonArray savedList = new JsonArray();
      if (files != null) {
        for (File file : files) {
          JsonObject entry = new JsonObject();
          entry.addProperty("filename", file.getName());
          entry.addProperty("fileSize", file.length());
          entry.addProperty("lastModified", Instant.ofEpochMilli(file.lastModified()).toString());

          // Try to read metadata without loading full content
          try (FileReader reader = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[2048]; // Read just header
            int read = reader.read(buffer);
            if (read > 0) {
              sb.append(buffer, 0, read);
              String partial = sb.toString();
              // Try to extract name and version
              if (partial.contains("\"name\"")) {
                try {
                  JsonObject obj =
                      JsonParser.parseString(partial.endsWith("}") ? partial : partial + "}}}")
                          .getAsJsonObject();
                  if (obj.has("name")) {
                    entry.addProperty("name", obj.get("name").getAsString());
                  }
                  if (obj.has("version")) {
                    entry.addProperty("version", obj.get("version").getAsString());
                  }
                  if (obj.has("description")) {
                    entry.addProperty("description", obj.get("description").getAsString());
                  }
                } catch (Exception e) {
                  // Partial parse failed — that's okay
                }
              }
            }
          } catch (IOException e) {
            entry.addProperty("readError", e.getMessage());
          }

          savedList.add(entry);
        }
      }

      response.add("saved", savedList);
      response.addProperty("count", savedList.size());
      return GSON.toJson(response);

    } catch (IOException e) {
      return errorJson("LIST_FAILED", "Failed to list saved states: " + e.getMessage(),
          "Check storage directory permissions");
    }
  }

  /**
   * Deletes a saved state file.
   *
   * @param input JSON with filename
   * @return JSON confirmation
   */
  private static String deleteSaved(JsonObject input) {
    String filename = input.has("filename") ? input.get("filename").getAsString() : "";
    if (filename.isEmpty()) {
      return errorJson("MISSING_FILE", "filename is required",
          "Use 'list' action to see available saved states");
    }

    Path path;
    try {
      path = resolveStorageFile(filename);
    } catch (IOException e) {
      return errorJson("INVALID_FILENAME", e.getMessage(), "Use only the saved-state filename");
    }
    if (!Files.exists(path)) {
      return errorJson("FILE_NOT_FOUND", "File not found: " + filename,
          "Use 'list' to see available files");
    }

    try {
      Files.delete(path);
      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("deleted", filename);
      return GSON.toJson(response);
    } catch (IOException e) {
      return errorJson("DELETE_FAILED", "Failed to delete: " + e.getMessage(),
          "Check file permissions");
    }
  }

  /**
   * Compares two saved versions and produces a diff.
   *
   * @param input JSON with file1 and file2 filenames
   * @return JSON with differences
   */
  private static String compareVersions(JsonObject input) {
    String file1 = input.has("file1") ? input.get("file1").getAsString() : "";
    String file2 = input.has("file2") ? input.get("file2").getAsString() : "";

    if (file1.isEmpty() || file2.isEmpty()) {
      return errorJson("MISSING_FILES", "Both file1 and file2 are required",
          "Provide filenames of two saved states to compare");
    }

    try {
      Path path1 = resolveStorageFile(file1);
      Path path2 = resolveStorageFile(file2);
      String content1 = new String(Files.readAllBytes(path1), StandardCharsets.UTF_8);
      String content2 = new String(Files.readAllBytes(path2), StandardCharsets.UTF_8);

      JsonObject obj1 = JsonParser.parseString(content1).getAsJsonObject();
      JsonObject obj2 = JsonParser.parseString(content2).getAsJsonObject();

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");

      // Compare metadata
      JsonObject metadataDiff = new JsonObject();
      compareField(metadataDiff, obj1, obj2, "name");
      compareField(metadataDiff, obj1, obj2, "version");
      compareField(metadataDiff, obj1, obj2, "savedAt");
      response.add("metadataDiff", metadataDiff);

      // Compare state
      boolean statesEqual = false;
      if (obj1.has("sessionState") && obj2.has("sessionState")) {
        String state1 = GSON.toJson(obj1.get("sessionState"));
        String state2 = GSON.toJson(obj2.get("sessionState"));
        statesEqual = state1.equals(state2);
      }
      response.addProperty("statesEqual", statesEqual);

      // Compare process definitions
      boolean defsEqual = false;
      if (obj1.has("processDefinition") && obj2.has("processDefinition")) {
        String def1 = GSON.toJson(obj1.get("processDefinition"));
        String def2 = GSON.toJson(obj2.get("processDefinition"));
        defsEqual = def1.equals(def2);
      }
      response.addProperty("processDefinitionsEqual", defsEqual);

      // File size comparison
      response.addProperty("file1Size", content1.length());
      response.addProperty("file2Size", content2.length());

      return GSON.toJson(response);

    } catch (IOException e) {
      return errorJson("COMPARE_FAILED", "Failed to compare: " + e.getMessage(),
          "Check that both files exist in " + storageDir);
    }
  }

  /**
   * Exports a session as a standalone shareable JSON document.
   *
   * @param input JSON with sessionId
   * @return JSON with complete standalone session
   */
  private static String exportSession(JsonObject input) {
    String sessionId = input.has("sessionId") ? input.get("sessionId").getAsString() : "";
    if (sessionId.isEmpty()) {
      return errorJson("MISSING_SESSION", "sessionId is required",
          "Provide the sessionId of the session to export");
    }

    // Get full session state
    String sessionState =
        SessionRunner.run("{\"action\": \"getState\", \"sessionId\": \"" + sessionId + "\"}");

    JsonObject stateObj = JsonParser.parseString(sessionState).getAsJsonObject();

    // Build export document
    JsonObject exportDoc = new JsonObject();
    exportDoc.addProperty("format", "neqsim-exported-session");
    exportDoc.addProperty("formatVersion", "1.0.0");
    exportDoc.addProperty("exportedAt", Instant.now().toString());
    exportDoc.addProperty("neqsimVersion", "3.10.0");
    exportDoc.addProperty("sessionId", sessionId);
    exportDoc.add("sessionState", stateObj);

    // Include instructions for importing
    exportDoc.addProperty("importInstructions",
        "Load this file using the statePersistence tool with action 'load' and filePath.");

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.add("exportedSession", exportDoc);
    response.addProperty("note",
        "Copy the exportedSession JSON to share. Use 'save' to persist to disk.");
    return GSON.toJson(response);
  }

  /**
   * Sets the storage directory for saved simulations.
   *
   * @param input JSON with directory path
   * @return JSON confirmation
   */
  private static String setStorageDirectory(JsonObject input) {
    String dir = input.has("directory") ? input.get("directory").getAsString() : "";
    if (dir.isEmpty()) {
      return errorJson("MISSING_DIR", "directory is required",
          "Provide the directory path for saving simulations");
    }
    Path requestedDir;
    try {
      requestedDir = Paths.get(dir).toAbsolutePath().normalize();
      if (!isStoragePathAllowed(requestedDir)) {
        return errorJson("DIR_OUTSIDE_SANDBOX",
            "Storage directory is outside the NeqSim MCP state sandbox: " + requestedDir,
            "Use a path below " + storageRootPath()
                + " or set NEQSIM_MCP_ALLOW_EXTERNAL_STATE_DIR=true explicitly.");
      }
      storageDir = requestedDir.toString();
    } catch (RuntimeException e) {
      return errorJson("INVALID_DIR", "Invalid storage directory: " + e.getMessage(),
          "Provide a valid directory path");
    }

    try {
      ensureStorageDir();
    } catch (IOException e) {
      return errorJson("DIR_CREATE_FAILED", "Cannot create directory: " + e.getMessage(),
          "Check permissions for: " + dir);
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("storageDir", storageDir);
    return GSON.toJson(response);
  }

  /**
   * Returns info about the persistence system.
   *
   * @return JSON with status
   */
  private static String getInfo() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("storageDir", storageDir);
    response.addProperty("dirExists", Files.exists(storageDirectoryPathUnchecked()));

    try {
      ensureStorageDir();
      File dir = storageDirectoryPath().toFile();
      File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
      response.addProperty("savedCount", files != null ? files.length : 0);
      if (files != null) {
        long totalSize = 0;
        for (File f : files) {
          totalSize += f.length();
        }
        response.addProperty("totalSizeBytes", totalSize);
      }
    } catch (IOException e) {
      response.addProperty("error", e.getMessage());
    }

    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Ensures the storage directory exists.
   *
   * @throws IOException if directory cannot be created
   */
  private static void ensureStorageDir() throws IOException {
    Path dir = storageDirectoryPath();
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }
  }

  /**
   * Resolves the configured storage directory and verifies the sandbox boundary.
   *
   * @return normalized storage directory path
   * @throws IOException if the directory is outside the allowed sandbox
   */
  private static Path storageDirectoryPath() throws IOException {
    Path dir = storageDirectoryPathUnchecked();
    if (!isStoragePathAllowed(dir)) {
      throw new IOException(
          "Storage directory is outside the allowed NeqSim MCP state sandbox: " + dir);
    }
    return dir;
  }

  /**
   * Resolves the configured storage directory without throwing checked exceptions.
   *
   * @return normalized storage directory path
   */
  private static Path storageDirectoryPathUnchecked() {
    return Paths.get(storageDir).toAbsolutePath().normalize();
  }

  /**
   * Resolves a saved-state filename below the configured storage directory.
   *
   * @param filename filename without path separators
   * @return normalized file path inside the storage directory
   * @throws IOException if the filename escapes the storage directory
   */
  private static Path resolveStorageFile(String filename) throws IOException {
    validateFilename(filename);
    Path base = storageDirectoryPath();
    Path resolved = base.resolve(filename).normalize();
    if (!resolved.startsWith(base)) {
      throw new IOException("Resolved path escapes the storage directory: " + filename);
    }
    return resolved;
  }

  /**
   * Resolves a load path, accepting legacy filePath values only when they remain inside storage.
   *
   * @param filename saved-state filename
   * @param filePath legacy file path value
   * @return normalized readable path inside the storage directory
   * @throws IOException if the path is invalid or outside the storage directory
   */
  private static Path resolveReadableStoragePath(String filename, String filePath)
      throws IOException {
    Path base = storageDirectoryPath();
    if (filePath != null && !filePath.isEmpty()) {
      Path requested = Paths.get(filePath).toAbsolutePath().normalize();
      if (!requested.startsWith(base)) {
        throw new IOException(
            "filePath must remain inside the configured storage directory: " + base);
      }
      return requested;
    }
    return resolveStorageFile(filename);
  }

  /**
   * Validates saved-state filenames.
   *
   * @param filename filename to validate
   * @throws IOException if the filename is empty or contains path traversal characters
   */
  private static void validateFilename(String filename) throws IOException {
    if (filename == null || filename.isEmpty()) {
      throw new IOException("Filename is required");
    }
    if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
      throw new IOException("Filename contains invalid path traversal characters: " + filename);
    }
  }

  /**
   * Returns the NeqSim MCP state sandbox root path.
   *
   * @return normalized root path
   */
  private static Path storageRootPath() {
    return Paths.get(DEFAULT_ROOT_DIR).toAbsolutePath().normalize();
  }

  /**
   * Checks whether a storage path is allowed by sandbox policy.
   *
   * @param path normalized path to check
   * @return true if the path is under the sandbox or explicit external storage is enabled
   */
  private static boolean isStoragePathAllowed(Path path) {
    return path.startsWith(storageRootPath()) || isExternalStorageAllowed();
  }

  /**
   * Checks whether external storage directories are explicitly enabled.
   *
   * @return true if external storage is enabled by property or environment
   */
  private static boolean isExternalStorageAllowed() {
    String property = System.getProperty(ALLOW_EXTERNAL_DIR_PROPERTY, "false");
    String env = System.getenv(ALLOW_EXTERNAL_DIR_ENV);
    return "true".equalsIgnoreCase(property) || "true".equalsIgnoreCase(env);
  }

  /**
   * Sanitizes a name for use as a filename.
   *
   * @param name the name to sanitize
   * @return a safe filename
   */
  private static String sanitizeFilename(String name) {
    return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
  }

  /**
   * Compares a string field between two JSON objects.
   *
   * @param diff the diff object to populate
   * @param obj1 first object
   * @param obj2 second object
   * @param field the field name
   */
  private static void compareField(JsonObject diff, JsonObject obj1, JsonObject obj2,
      String field) {
    String val1 = obj1.has(field) ? obj1.get(field).getAsString() : "(missing)";
    String val2 = obj2.has(field) ? obj2.get(field).getAsString() : "(missing)";
    if (!val1.equals(val2)) {
      JsonObject fieldDiff = new JsonObject();
      fieldDiff.addProperty("file1", val1);
      fieldDiff.addProperty("file2", val2);
      diff.add(field, fieldDiff);
    }
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation how to fix
   * @return the error JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}

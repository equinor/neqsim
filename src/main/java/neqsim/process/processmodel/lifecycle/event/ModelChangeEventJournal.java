package neqsim.process.processmodel.lifecycle.event;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Append-only JSON-lines journal providing durable replay and idempotency for model-change events. */
public final class ModelChangeEventJournal implements ModelChangeEventPublisher {
  private final Path file;
  private final List<ModelChangeEvent> history = new ArrayList<ModelChangeEvent>();
  private final Map<String, ModelChangeEvent> byIdempotencyKey = new LinkedHashMap<String, ModelChangeEvent>();

  public ModelChangeEventJournal(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    this.file = file;
    load();
  }

  @Override
  public synchronized ModelChangePublishResult publish(ModelChangeEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("event must not be null");
    }
    ModelChangeEvent previous = byIdempotencyKey.get(event.getIdempotencyKey());
    if (previous != null) {
      InMemoryModelChangeEventBus.requireSamePayload(previous, event);
      return new ModelChangePublishResult(ModelChangePublishResult.Status.DUPLICATE, previous.getEventId(), 0,
          Collections.<String>emptyList());
    }
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(file, (event.toCompactJson() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not append model-change event journal " + file, ex);
    }
    byIdempotencyKey.put(event.getIdempotencyKey(), event);
    history.add(event);
    return new ModelChangePublishResult(ModelChangePublishResult.Status.PUBLISHED, event.getEventId(), 0,
        Collections.<String>emptyList());
  }

  public synchronized int replay(ModelChangeEventSubscriber subscriber) {
    if (subscriber == null) {
      throw new IllegalArgumentException("subscriber must not be null");
    }
    for (ModelChangeEvent event : history) {
      subscriber.onModelChange(event);
    }
    return history.size();
  }

  public synchronized List<ModelChangeEvent> getHistory() {
    return Collections.unmodifiableList(new ArrayList<ModelChangeEvent>(history));
  }

  private void load() throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    if (!Files.isRegularFile(file)) {
      throw new IOException("Model-change journal is not a regular file: " + file);
    }
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.trim().isEmpty()) {
          continue;
        }
        ModelChangeEvent event;
        try {
          event = ModelChangeEvent.fromJson(line);
        } catch (RuntimeException ex) {
          throw new IOException("Invalid model-change event at journal line " + lineNumber, ex);
        }
        ModelChangeEvent previous = byIdempotencyKey.get(event.getIdempotencyKey());
        if (previous != null) {
          try {
            InMemoryModelChangeEventBus.requireSamePayload(previous, event);
          } catch (IllegalArgumentException ex) {
            throw new IOException("Idempotency collision at journal line " + lineNumber, ex);
          }
          continue;
        }
        byIdempotencyKey.put(event.getIdempotencyKey(), event);
        history.add(event);
      }
    }
  }
}

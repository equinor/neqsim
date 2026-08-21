---
title: "Saving and Loading Process Simulations in NeqSim"
description: "Choose and validate NeqSim process persistence formats in Java and Python."
---

# Saving and Loading Process Simulations in NeqSim

NeqSim has two different persistence models:

- a compressed `.neqsim` archive stores the Java object graph for a `ProcessSystem` or
  `ProcessModel`; and
- lifecycle JSON stores a portable, reviewable engineering-state representation.

They serve different purposes. Use the full object graph when the same application needs to
restart a model. Use lifecycle JSON for versioned state, comparison, interchange, or applying
values to a compatible model definition.

## Choose a format

| Need | API | What is stored | Load behavior |
| --- | --- | --- | --- |
| Restart one process | `ProcessSystem.saveToNeqsim()` | Full XStream object graph in a ZIP archive | `ProcessSystem.loadFromNeqsim()` loads and runs it |
| Restart several processes | `ProcessModel.saveToNeqsim()` | Full `ProcessModel` object graph in a ZIP archive | `ProcessModel.loadFromNeqsim()` loads and runs it |
| Review or version state | `ProcessSystemState` / `ProcessModelState` | Selected equipment, stream, topology, metadata, and execution state | Validate, then reconstruct supported content or apply it to a matching model |
| Inspect XML while debugging | `save_xml()` / `open_xml()` in neqsim-python | Uncompressed XStream XML | Low-level wrapper; the caller runs a restored process |
| Read an older binary file | `saveAuto()` / `loadAuto()` with another extension | Legacy Java serialization | Compatibility path, not the preferred new format |

The `.neqsim` extension is used by both full-object archives and some compressed lifecycle-state
helpers. The API that writes the file determines its contents; the extension alone does not.

> **Security boundary:** XStream deserialization can instantiate classes from the serialized
> object graph. Load `.neqsim` and XML files only from trusted sources. Do not expose these load
> methods directly to untrusted uploads.

## Java: `ProcessSystem` archives

### Save and load

Check both failure signals. Saving returns `false`; the convenience loader logs a failure and
returns `null`. The loader runs a successfully restored process before returning it.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 10.0);
fluid.addComponent("ethane", 5.0);
fluid.setMixingRule("classic");

ProcessSystem process = new ProcessSystem("my process");
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
process.add(feed);
process.run();

if (!process.saveToNeqsim("my_process.neqsim")) {
    throw new IllegalStateException("Could not save my_process.neqsim");
}

ProcessSystem loaded = ProcessSystem.loadFromNeqsim("my_process.neqsim");
if (loaded == null) {
    throw new IllegalStateException("Could not load my_process.neqsim");
}
```

The archive is a ZIP file containing one UTF-8 `process.xml` entry. Compression depends on the
actual model graph, so measure representative files instead of assuming a fixed ratio.

### Exact `saveAuto()` and `loadAuto()` behavior

`ProcessSystem.saveAuto()` selects the writer from the filename:

- `.neqsim` calls `saveToNeqsim()`;
- `.json` calls `exportStateToFile()`; and
- every other extension uses legacy Java binary serialization.

`ProcessSystem.loadAuto()` does **not** load lifecycle JSON. It loads `.neqsim` with
`loadFromNeqsim()` and routes every other extension to the legacy binary `open()` method. To read
JSON, load a `ProcessSystemState` and apply it to a compatible, already-built process:

```java
import neqsim.process.processmodel.lifecycle.ProcessSystemState;

ProcessSystemState state = ProcessSystemState.loadFromFile("process_state.json");
ProcessSystemState.ValidationResult validation = state.validate();
if (!validation.isValid()) {
    throw new IllegalArgumentException(validation.getErrors().toString());
}
state.applyTo(existingProcess);
existingProcess.run();
```

`applyTo()` updates the matching process structure; it is not a promise that arbitrary equipment
and connections will be recreated from JSON. `toProcessSystem()` reconstructs only the equipment
types supported by the lifecycle implementation.

### Low-level archive API

Use `NeqSimXtream` when the object type is not known until runtime or when the caller needs the
checked `IOException` from opening an archive:

```java
import java.io.IOException;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.serialization.NeqSimXtream;

if (!NeqSimXtream.saveNeqsim(process, "my_process.neqsim")) {
    throw new IllegalStateException("Serialization failed");
}

Object restored = NeqSimXtream.openNeqsim("my_process.neqsim");
if (!(restored instanceof ProcessSystem)) {
    throw new IOException("Archive does not contain a ProcessSystem");
}
ProcessSystem restoredProcess = (ProcessSystem) restored;
restoredProcess.run();
```

Do not add unrestricted XStream permissions in application code as a substitute for the NeqSim
helper. The helper configures reference handling and skips `ThreadLocal` fields, but its load path
still assumes trusted input.

## Java: `ProcessModel` archives and state

The same full-object pattern applies to a multi-process model:

```java
import neqsim.process.processmodel.ProcessModel;

ProcessModel model = new ProcessModel();
model.add("upstream", upstreamProcess);
model.add("downstream", downstreamProcess);
model.run();

if (!model.saveToNeqsim("field_model.neqsim")) {
    throw new IllegalStateException("Could not save field model");
}
ProcessModel restoredModel = ProcessModel.loadFromNeqsim("field_model.neqsim");
if (restoredModel == null) {
    throw new IllegalStateException("Could not load field model");
}
```

Unlike `ProcessSystem.loadAuto()`, `ProcessModel.loadAuto()` recognizes `.json` and calls
`loadStateFromFile()`. The lifecycle representation captures model metadata, per-process state,
execution configuration, and inter-process connection records. See
[Process Model Lifecycle Management](../process/lifecycle/process_model_lifecycle) for its schema,
validation, comparison, and migration APIs.

## Python: neqsim-python archives

The Python wrappers return `False` from `save_neqsim()` and `None` from `open_neqsim()` when the
underlying operation fails. Check those values before publishing or using a file. The low-level
Python loader does not run a restored process automatically.

```python
import neqsim
from neqsim.process import clearProcess, getProcess, runProcess, separator, stream
from neqsim.thermo import fluid

clearProcess()
feed_fluid = fluid("srk")
feed_fluid.addComponent("methane", 0.9)
feed_fluid.addComponent("ethane", 0.1)
feed_fluid.setTemperature(30.0, "C")
feed_fluid.setPressure(50.0, "bara")
feed_fluid.setTotalFlowRate(10.0, "MSm3/day")

feed = stream("feed", feed_fluid)
separator("separator", feed)
runProcess()
process = getProcess()

if not neqsim.save_neqsim(process, "my_process.neqsim"):
    raise OSError("Could not save my_process.neqsim")

loaded_process = neqsim.open_neqsim("my_process.neqsim")
if loaded_process is None:
    raise OSError("Could not load my_process.neqsim")
loaded_process.run()
```

`save_xml()` and `open_xml()` provide uncompressed XML for debugging. They have the same trusted-
input boundary as `.neqsim` archives and are not a safe interchange parser for arbitrary XML.

## Publish files transactionally

`saveToNeqsim()` and `save_neqsim()` open the destination before serializing. If serialization
fails, they report failure but a partial or truncated destination can remain. Do not treat file
existence as proof of success.

For durable checkpoints:

1. save to a temporary path in the destination directory;
2. require a successful return value;
3. reopen the temporary archive and verify its expected Java type;
4. run the restored process and check the engineering acceptance criteria; and
5. replace the published checkpoint with the verified temporary file using an atomic move when
   the filesystem supports it.

Delete a failed temporary file. Preserve the previous verified checkpoint until the replacement
has passed the round trip.

## Portability and compatibility

### Embedded JVM hosts

XStream cannot reflect into several JDK collection implementations when the JVM does not open
`java.util`. This matters in embedded hosts such as neqsim-python, which normally run without
Surefire's `--add-opens` options. A reachable unsupported collection can produce a
`No converter available` error and a truncated output file.

NeqSim regression coverage now checks that empty and recycle-bearing `ProcessSystem` graphs do not
retain the known unsupported JDK collection types, and it round-trips a process containing a
`Recycle`. If the error recurs for another equipment graph, record the failing equipment and full
exception, remove the partial file, and report a minimal model that reproduces the graph.

Do not add JVM-wide `--add-opens` flags merely to make a process serializable. That can hide a
non-portable stored field and behave differently between Maven tests and embedded applications.

### Version compatibility

Full-object archives contain Java class and field names. Test a representative archive before a
NeqSim upgrade, keep the creating NeqSim version with the artifact, and retain a previous verified
checkpoint. Unknown fields may be ignored by the configured reader, but renamed or removed classes,
changed equipment graphs, and changed solver behavior can still prevent loading or change results.

Lifecycle JSON is easier to inspect and migrate, but it is a selective state model rather than a
lossless copy of every Java object. Record schema version, model version, provenance, units, and the
base model definition used for `applyTo()`.

## Troubleshooting

### Save returned `false`

- Read the logged root exception; do not continue with the destination file.
- Remove the partial temporary file.
- For `No converter available` or module-access errors, reduce the model to the equipment that
  introduces the stored collection and report it as a portability defect.
- Confirm the parent directory is writable and has enough free space.

### Load returned `null` or `None`

- Confirm the file is a ZIP archive with a `process.xml` entry.
- Confirm it was created with a compatible NeqSim version.
- Confirm the expected root type (`ProcessSystem` versus `ProcessModel`).
- Do not retry an untrusted or visibly truncated file with broader XStream permissions.

### Loaded values differ

- Remember that the Java convenience loaders run the model after deserialization.
- Compare inputs, solver settings, NeqSim version, and convergence diagnostics, not only stored
  outlet values.
- For lifecycle JSON, verify that `validate()` succeeds and that `applyTo()` targets the same
  equipment names and topology.

## API summary

| API | Failure signal | Runs after load? |
| --- | --- | --- |
| `ProcessSystem.saveToNeqsim(path)` | `false` | Not applicable |
| `ProcessSystem.loadFromNeqsim(path)` | `null` | Yes |
| `ProcessModel.saveToNeqsim(path)` | `false` | Not applicable |
| `ProcessModel.loadFromNeqsim(path)` | `null` | Yes |
| `NeqSimXtream.saveNeqsim(object, path)` | `false` | Not applicable |
| `NeqSimXtream.openNeqsim(path)` | `IOException` or incompatible object | No |
| `neqsim.save_neqsim(object, path)` | `False` | Not applicable |
| `neqsim.open_neqsim(path)` | `None` | No |

## See also

- [ProcessSystem](../process/processmodel/process_system)
- [ProcessModel](../process/processmodel/process_model)
- [Process Model Lifecycle Management](../process/lifecycle/process_model_lifecycle)
- [NeqSim Python](https://github.com/equinor/neqsim-python)
- [XStream security](https://x-stream.github.io/security.html)

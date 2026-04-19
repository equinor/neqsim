"""
NeqSim Development Setup — JVM bootstrap + class imports for Jupyter notebooks.

Usage in any notebook::

    from neqsim_dev_setup import neqsim_init, neqsim_classes
    ns = neqsim_init(recompile=True)   # cell 1: start JVM
    ns = neqsim_classes(ns)            # cell 2: import classes

    # Use classes
    fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)

Re-running the init cell after editing Java code will compile, restart
the kernel, and load fresh classes automatically.
"""
import jpype
from pathlib import Path
import subprocess
import os
import types


# ── Project root detection ──
# Priority order:
# 1. Explicit project_root parameter (in neqsim_init)
# 2. NEQSIM_PROJECT_ROOT environment variable
# 3. Walk upward from this file's location (works when devtools/ is in the repo)
# 4. Walk upward from CWD (works when notebook is inside the repo)
# 5. Well-known default paths


def _find_project_root():
    """Auto-detect the neqsim project root by searching for pom.xml + mvnw."""
    # From env var
    env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    if env_root:
        p = Path(env_root)
        if (p / "pom.xml").exists():
            return p

    # Walk up from this file (works when installed in <repo>/devtools/)
    candidates = [Path(__file__).resolve().parent.parent]

    # Walk up from CWD (works when notebook is run from inside the repo)
    cwd = Path.cwd().resolve()
    candidates.append(cwd)
    for parent in cwd.parents:
        candidates.append(parent)
        if len(parent.parts) <= 2:
            break  # stop at drive root

    # Well-known default paths
    candidates.append(Path.home() / "Documents" / "GitHub" / "neqsim2")
    candidates.append(Path.home() / "Documents" / "GitHub" / "neqsim")

    for c in candidates:
        if (c / "pom.xml").exists() and (
            (c / "mvnw.cmd").exists() or (c / "mvnw").exists()
        ):
            return c

    return None


_PROJECT_ROOT = _find_project_root() or Path(__file__).resolve().parent.parent


def _run_compile(root):
    """Run mvnw compile and raise on failure."""
    print("Compiling... ", end="", flush=True)
    mvnw = root / "mvnw.cmd" if os.name == "nt" else root / "mvnw"
    result = subprocess.run(
        [str(mvnw), "compile", "-q"],
        cwd=str(root),
        capture_output=True,
        text=True,
        timeout=120,
        env={**os.environ, "JAVA_HOME": os.environ.get("JAVA_HOME", "")},
    )
    if result.returncode == 0:
        print("OK")
    else:
        print("FAILED")
        print(result.stdout)
        print(result.stderr)
        raise RuntimeError("Maven compile failed — see output above")


def neqsim_init(project_root=None, extra_classpath=None, recompile=False, verbose=True):
    """
    Start the JVM with the NeqSim project classpath.

    If the JVM is already running (from a previous call), it automatically
    recompiles (if requested) and restarts the kernel so fresh classes are
    loaded. On a fresh kernel it just compiles and starts the JVM directly.

    Parameters
    ----------
    project_root : str or Path, optional
        Path to the neqsim project root. Defaults to the built-in path.
    extra_classpath : list of str, optional
        Additional classpath entries to append.
    recompile : bool
        If True, run ``mvnw compile`` before starting the JVM so the
        latest Java changes are picked up.
    verbose : bool
        Print classpath and JVM info.

    Returns
    -------
    ns : namespace object
        Object with PROJECT_ROOT and JClass shortcut.
        Pass to ``neqsim_classes(ns)`` to add class imports.
    """
    root = Path(project_root) if project_root else _PROJECT_ROOT

    # Validate the root has a pom.xml
    if not (root / "pom.xml").exists():
        raise FileNotFoundError(
            f"NeqSim project root not found at {root}. "
            "Set NEQSIM_PROJECT_ROOT env var or pass project_root= parameter."
        )

    if verbose:
        print(f"NeqSim project root: {root}")

    # If JVM is already running, handle based on recompile flag
    if jpype.isJVMStarted():
        if recompile:
            # Must restart to reload freshly compiled classes
            print("JVM already running — compiling before kernel restart... ",
                  end="", flush=True)
            _run_compile(root)
            import IPython
            IPython.Application.instance().kernel.do_shutdown(restart=True)
            return None  # won't reach here (kernel restarts)
        else:
            # Reuse current JVM — no restart needed
            if verbose:
                print("JVM already running — reusing existing JVM")
            ns = types.SimpleNamespace()
            ns.PROJECT_ROOT = root
            ns.JClass = jpype.JClass
            return ns

    if recompile:
        _run_compile(root)

    classes_dir = root / "target" / "classes"
    resources_dir = root / "src" / "main" / "resources"

    # The shade plugin produces the shaded JAR as the main artifact
    # (neqsim-X.Y.Z.jar) and renames the original to original-neqsim-X.Y.Z.jar.
    # Exclude original-*, -sources, -javadoc JARs.
    all_jars = sorted(root.glob("target/neqsim-*.jar"))
    shaded_jars = [
        j for j in all_jars
        if not j.name.startswith("original-")
        and "-sources" not in j.name
        and "-javadoc" not in j.name
    ]
    if not shaded_jars:
        raise FileNotFoundError(
            f"No NeqSim JAR found in {root / 'target'}. "
            "Run: mvnw.cmd package -DskipTests"
        )
    shaded_jar = shaded_jars[-1]

    classpath = [str(classes_dir), str(resources_dir), str(shaded_jar)]
    if extra_classpath:
        classpath.extend(extra_classpath)

    if verbose:
        print("Classpath:")
        for i, cp in enumerate(classpath, 1):
            print(f"  {i}. {cp}")

    # Suppress Java 22+ restricted method warnings from JPype native access
    # Only add the flag if the JVM supports it (Java 22+)
    jvm_args = []
    import subprocess, re
    try:
        _ver = subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True)
        _m = re.search(r'"(\d+)', _ver)
        if _m and int(_m.group(1)) >= 22:
            jvm_args.append("--enable-native-access=ALL-UNNAMED")
    except Exception:
        pass
    jpype.startJVM(*jvm_args, classpath=classpath, convertStrings=True)
    if verbose:
        print(f"\nJVM started: {jpype.getDefaultJVMPath()}")

    ns = types.SimpleNamespace()
    ns.PROJECT_ROOT = root
    ns.JClass = jpype.JClass

    if verbose:
        print("Ready — call neqsim_classes(ns) to import classes")

    return ns


def neqsim_classes(ns):
    """
    Populate the namespace with commonly used NeqSim Java classes.

    Call this in a separate cell after ``neqsim_init()`` so you can
    customise which classes are loaded per notebook.

    Parameters
    ----------
    ns : namespace object
        The namespace returned by ``neqsim_init()``.

    Returns
    -------
    ns : namespace object
        Same object, now with class attributes like ns.SystemSrkEos, etc.
    """
    JClass = jpype.JClass

    # Thermo systems
    ns.SystemSrkEos = JClass("neqsim.thermo.system.SystemSrkEos")
    ns.SystemPrEos = JClass("neqsim.thermo.system.SystemPrEos")
    ns.SystemSrkCPAstatoil = JClass("neqsim.thermo.system.SystemSrkCPAstatoil")
    ns.ThermodynamicOperations = JClass(
        "neqsim.thermodynamicoperations.ThermodynamicOperations"
    )

    # Process model
    ns.ProcessSystem = JClass("neqsim.process.processmodel.ProcessSystem")
    ns.ProcessModel = JClass("neqsim.process.processmodel.ProcessModel")
    ns.ProcessModule = JClass("neqsim.process.processmodel.ProcessModule")
    ns.Stream = JClass("neqsim.process.equipment.stream.Stream")
    ns.Separator = JClass("neqsim.process.equipment.separator.Separator")
    ns.ThreePhaseSeparator = JClass(
        "neqsim.process.equipment.separator.ThreePhaseSeparator"
    )
    ns.Compressor = JClass("neqsim.process.equipment.compressor.Compressor")
    ns.Cooler = JClass("neqsim.process.equipment.heatexchanger.Cooler")
    ns.Heater = JClass("neqsim.process.equipment.heatexchanger.Heater")
    ns.HeatExchanger = JClass(
        "neqsim.process.equipment.heatexchanger.HeatExchanger"
    )
    ns.Mixer = JClass("neqsim.process.equipment.mixer.Mixer")
    ns.Splitter = JClass("neqsim.process.equipment.splitter.Splitter")
    ns.ThrottlingValve = JClass(
        "neqsim.process.equipment.valve.ThrottlingValve")
    ns.AdiabaticPipe = JClass(
        "neqsim.process.equipment.pipeline.AdiabaticPipe")
    ns.PipeBeggsAndBrills = JClass(
        "neqsim.process.equipment.pipeline.PipeBeggsAndBrills"
    )
    ns.Pump = JClass("neqsim.process.equipment.pump.Pump")
    ns.Manifold = JClass("neqsim.process.equipment.manifold.Manifold")
    ns.StreamSaturatorUtil = JClass(
        "neqsim.process.equipment.util.StreamSaturatorUtil"
    )
    ns.Recycle = JClass("neqsim.process.equipment.util.Recycle")
    ns.Adjuster = JClass("neqsim.process.equipment.util.Adjuster")
    ns.SetPoint = JClass("neqsim.process.equipment.util.SetPoint")
    ns.SpreadsheetBlock = JClass("neqsim.process.equipment.util.SpreadsheetBlock")
    ns.Expander = JClass("neqsim.process.equipment.expander.Expander")
    ns.DistillationColumn = JClass(
        "neqsim.process.equipment.distillation.DistillationColumn"
    )
    ns.SimpleAbsorber = JClass(
        "neqsim.process.equipment.absorber.SimpleAbsorber"
    )
    ns.ComponentSplitter = JClass(
        "neqsim.process.equipment.splitter.ComponentSplitter"
    )
    ns.GibbsReactor = JClass("neqsim.process.equipment.reactor.GibbsReactor")
    ns.PlugFlowReactor = JClass(
        "neqsim.process.equipment.reactor.PlugFlowReactor"
    )
    ns.StirredTankReactor = JClass(
        "neqsim.process.equipment.reactor.StirredTankReactor"
    )

    # Bioprocessing / bioenergy
    ns.AnaerobicDigester = JClass(
        "neqsim.process.equipment.reactor.AnaerobicDigester"
    )
    ns.FermentationReactor = JClass(
        "neqsim.process.equipment.reactor.FermentationReactor"
    )
    ns.BiomassCharacterization = JClass(
        "neqsim.thermo.characterization.BiomassCharacterization"
    )
    ns.BiogasUpgrader = JClass(
        "neqsim.process.equipment.splitter.BiogasUpgrader"
    )
    ns.SustainabilityMetrics = JClass(
        "neqsim.process.util.fielddevelopment.SustainabilityMetrics"
    )
    ns.BiogasToGridModule = JClass(
        "neqsim.process.processmodel.biorefinery.BiogasToGridModule"
    )
    ns.WasteToEnergyCHPModule = JClass(
        "neqsim.process.processmodel.biorefinery.WasteToEnergyCHPModule"
    )
    ns.GasificationSynthesisModule = JClass(
        "neqsim.process.processmodel.biorefinery.GasificationSynthesisModule"
    )

    print("All NeqSim classes imported OK")
    return ns


def neqsim_compile(project_root=None, restart_kernel=True):
    """
    Compile the NeqSim project and optionally restart the Jupyter kernel.

    Parameters
    ----------
    project_root : str or Path, optional
        Path to the neqsim project root.
    restart_kernel : bool
        If True, restart the kernel after successful compile.
    """
    root = Path(project_root) if project_root else _PROJECT_ROOT
    _run_compile(root)
    if restart_kernel:
        print("Restarting kernel to pick up changes...")
        import IPython
        IPython.Application.instance().kernel.do_shutdown(restart=True)

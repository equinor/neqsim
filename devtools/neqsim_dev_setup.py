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


# ── Project root (auto-detected: this file lives in <project>/devtools/) ──
_PROJECT_ROOT = Path(__file__).resolve().parent.parent


def _run_compile(root):
    """Run mvnw compile and raise on failure."""
    print("Compiling... ", end="", flush=True)
    result = subprocess.run(
        [str(root / "mvnw.cmd"), "compile", "-q"],
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

    # If JVM is already running, we must restart the kernel to reload classes
    if jpype.isJVMStarted():
        if recompile:
            print("JVM already running — compiling before restart... ", end="", flush=True)
            _run_compile(root)
        else:
            print("JVM already running — restarting kernel to reload classes...")
        import IPython
        IPython.Application.instance().kernel.do_shutdown(restart=True)
        return None  # won't reach here

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

    jpype.startJVM(classpath=classpath, convertStrings=True)
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

    # Process
    ns.ProcessSystem = JClass("neqsim.process.processmodel.ProcessSystem")
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
    ns.ThrottlingValve = JClass("neqsim.process.equipment.valve.ThrottlingValve")
    ns.AdiabaticPipe = JClass("neqsim.process.equipment.pipeline.AdiabaticPipe")
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

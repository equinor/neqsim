"""Paired same-JVM diagnostic after noisy separate-fork runtime controls.

Isolated URL classloaders keep complete baseline/candidate NeqSim worlds separate.
Both are warmed and interleaved in one JVM to share scheduling and GC conditions.
Reflection overhead is present equally on both sides. This is a narrow diagnostic,
not a replacement for end-to-end separate-fork timing or a general speed claim.
"""
import argparse
import json
import math
import os
from pathlib import Path
import statistics
import time

import jpype


def main():
    """Measure six fixed windows of eight alternating 50-trial samples per model."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--baseline-classes', required=True)
    parser.add_argument('--probe-classes', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    jpype.startJVM('-Xms128m', '-Xmx1024m', classpath=args.classpath.split(os.pathsep))
    cls = jpype.JClass
    array = jpype.JArray
    class_array = array(cls('java.lang.Class'))
    object_array = array(cls('java.lang.Object'))
    url_array = array(cls('java.net.URL'))
    double = cls('java.lang.Double')
    integer = cls('java.lang.Integer')
    boolean = cls('java.lang.Boolean')
    string = cls('java.lang.String')
    parent = cls('java.lang.ClassLoader').getSystemClassLoader().getParent()
    systems = {}
    for name in ['baseline', 'candidate']:
        paths = [args.probe_classes] + args.classpath.split(os.pathsep)
        if name == 'baseline':
            paths.insert(0, args.baseline_classes)
        urls = [cls('java.io.File')(p).toURI().toURL() for p in paths]
        loader = cls('java.net.URLClassLoader')(url_array(urls), parent)
        fluid_class = loader.loadClass('neqsim.thermo.system.SystemSrkEos')
        cvd_class = loader.loadClass('neqsim.pvtsimulation.simulation.ConstantVolumeDepletion')
        methods = {str(m.getName()) for m in cvd_class.getDeclaredMethods()}
        if ('runDryPhaseChangingFixedCellDepletion' in methods) != (name == 'candidate'):
            raise RuntimeError('Wrong baseline/candidate source world')
        if fluid_class.getClassLoader() != loader or cvd_class.getClassLoader() != loader:
            raise RuntimeError('NeqSim worlds are not isolated')
        constructor = fluid_class.getConstructor(class_array([double.TYPE, double.TYPE]))
        fluid = constructor.newInstance(object_array([double(350), double(200)]))

        def invoke(method, types, values):
            return fluid_class.getMethod(method, class_array(types)).invoke(fluid, object_array(values))

        invoke('addComponent', [string.class_, double.TYPE], [string('methane'), double(.6)])
        invoke('addComponent', [string.class_, double.TYPE], [string('n-decane'), double(.4)])
        invoke('setMixingRule', [string.class_], [string('classic')])
        invoke('setMultiPhaseCheck', [boolean.TYPE], [boolean(True)])
        invoke('useVolumeCorrection', [boolean.TYPE], [boolean(False)])
        invoke('init', [integer.TYPE], [integer(0)])
        probe = loader.loadClass('FixedCellRuntimeProbe')
        run = probe.getDeclaredMethod('run', class_array([loader.loadClass('neqsim.thermo.system.SystemInterface')]))
        run.setAccessible(True)
        systems[name] = dict(loader=loader, run=run, input=object_array([fluid]))
    cpu = cls('java.lang.management.ManagementFactory').getThreadMXBean()
    if not cpu.isCurrentThreadCpuTimeSupported():
        raise RuntimeError('Main-thread CPU timing unavailable')
    for i in range(100):
        for name in ['baseline', 'candidate']:
            state = systems[name]
            state['run'].invoke(None, state['input'])
    windows = []
    for window in range(6):
        record = dict(baseline=[], candidate=[])
        for sample in range(8):
            order = ['baseline', 'candidate'] if (window+sample) % 2 == 0 else ['candidate', 'baseline']
            for name in order:
                state = systems[name]
                checksum = 0.
                cpu_start = cpu.getCurrentThreadCpuTime()
                started = time.perf_counter()
                for trial in range(50):
                    checksum += float(state['run'].invoke(None, state['input']))
                elapsed = time.perf_counter()-started
                cpu_seconds = (cpu.getCurrentThreadCpuTime()-cpu_start)/1e9
                record[name].append(dict(seconds=elapsed, mainThreadCpuSeconds=cpu_seconds,
                                         checksumVolumeM3=checksum))
            if record['baseline'][-1]['checksumVolumeM3'] != record['candidate'][-1]['checksumVolumeM3']:
                raise RuntimeError('Paired outputs differ')
        for key in ['seconds', 'mainThreadCpuSeconds']:
            record[key+'Ratio'] = (statistics.median(r[key] for r in record['candidate'])
                                   / statistics.median(r[key] for r in record['baseline']))
        windows.append(record)
    report = dict(protocol='Same-JVM isolated worlds; 100 warmups/model; 6 windows; 8 alternating samples/model/window; 50 trials/sample',
                  jvmOptions=['-Xms128m', '-Xmx1024m'], javaVersion=str(cls('java.lang.System').getProperty('java.version')),
                  windows=windows)
    for key in ['secondsRatio', 'mainThreadCpuSecondsRatio']:
        values = [r[key] for r in windows]
        report[key+'Range'] = [min(values), max(values)]
        report[key+'GeometricMean'] = math.exp(statistics.mean(math.log(x) for x in values))
    Path(args.output).write_text(json.dumps(report, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'windows'}))


if __name__ == '__main__':
    main()

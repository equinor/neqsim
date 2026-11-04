"""Exercise Java/JPype handoff and distinct immutable synthetic EOS versions.

This is a consumer integration check, not the independent reference. Volume
correction on/off is a declared model-basis contrast, not a laboratory tune or
reservoir-volume fit. Supply only candidate classes/resources and dependencies;
do not mix a packaged NeqSim JAR into the classpath.
"""
import argparse
import json
import os
from pathlib import Path

import jpype


def main():
    """Check actual checkpoint selection, separator identity and sample ownership."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    jpype.startJVM(classpath=args.classpath.split(os.pathsep))
    cls = jpype.JClass
    fluid = cls('neqsim.thermo.system.SystemSrkEos')(275, 100)
    fluid.addComponent('methane', .95)
    fluid.addComponent('n-butane', .05)
    fluid.setMixingRule('classic')
    fluid.setMultiPhaseCheck(True)
    fluid.useVolumeCorrection(False)
    fluid.init(0)
    tuned = fluid.clone()
    tuned.useVolumeCorrection(True)
    serializer = cls('org.apache.commons.lang3.SerializationUtils')
    before = [bytes(serializer.serialize(f)) for f in [fluid, tuned]]
    model = cls('neqsim.pvtsimulation.util.CalibratedPvtModel')(
        fluid, tuned, 'synthetic dry sample', 'unshifted SRK/classic',
        'declared translated SRK/classic consistency contrast',
        'synthetic EOS-basis control; not measured calibration or field-volume fitting',
        ['whole-experiment-control'])
    separator = cls('neqsim.pvtsimulation.util.PvtSeparatorPath')(
        [1.5, 1.01325], [303.15, 298.15], 'final actual volumes at 1.01325 bara / 298.15 K')
    generator = cls('neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator')
    rows = []
    for selected in [False, True]:
        result = generator.replayNativeDryPhaseChangingFixedCell(
            model, selected, 'synthetic dry initial-volume experiment; public consumer control',
            275, [100, 60, 50, 30, 20, 5, 2], separator)
        if result.isTuned() != selected or not result.getFixedCellReference().allowsDryPhaseChanges():
            raise RuntimeError('Checkpoint or explicit protocol identity lost')
        snapshots = result.getSnapshots()
        if snapshots[0].getRetainedFluid().hasPhaseType('oil') or not snapshots[1].getRetainedFluid().hasPhaseType('oil'):
            raise RuntimeError('Expected independent dry phase appearance missing')
        rows.append(dict(tuned=selected, referenceVolumeM3=result.getFixedCellReference().getReferenceVolumeM3(),
                         phases=[[str(s.getRetainedFluid().getPhase(i).getType().getDesc())
                                  for i in range(s.getRetainedFluid().getNumberOfPhases())] for s in snapshots],
                         separatorReference=str(result.getSeparatorPath().getReference())))
    if rows[0]['referenceVolumeM3'] == rows[1]['referenceVolumeM3']:
        raise RuntimeError('Distinct declared EOS volume bases were not selected')
    if before != [bytes(serializer.serialize(f)) for f in [fluid, tuned]]:
        raise RuntimeError('Caller checkpoint input mutated')
    Path(args.output).write_text(json.dumps(rows, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(rows))


if __name__ == '__main__':
    main()

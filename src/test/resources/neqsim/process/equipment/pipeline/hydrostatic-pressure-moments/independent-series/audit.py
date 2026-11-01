"""Independent 80-digit binomial-series check of exact-head Java pressure geometry."""
from decimal import Decimal as Q, localcontext
from pathlib import Path
import csv
import hashlib
import json
import subprocess
import tempfile
from validate_native import read_native

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / 'pom.xml').is_file())
TEMP = tempfile.TemporaryDirectory()
CLASSES = Path(TEMP.name)
PROBE = CLASSES / 'PressureMomentProbe.java'
PROBE.write_text('import java.io.BufferedReader;\nimport java.io.InputStreamReader;\nimport java.io.PrintWriter;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\nimport java.nio.file.Paths;\nimport neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;\n/** Read-only probe of published pressure moments.\n * @author NeqSim\n * @version 1.0\n */\npublic class PressureMomentProbe {\n  /** Evaluate supplied sections without changing any solver state.\n   * @param args output CSV path\n   * @throws Exception if reading or writing fails\n   */\n  public static void main(String[] args) throws Exception {\n    GeometryCalculator geometry = new GeometryCalculator();\n    try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));\n        PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {\n      output.println("diameter_m,depth_m,liquid_m3,gas_m3,liquid_m2,gas_m2");\n      String line;\n      while ((line = input.readLine()) != null) {\n        String[] fields = line.split(",");\n        double diameter = Double.parseDouble(fields[0]);\n        double depth = Double.parseDouble(fields[1]);\n        GeometryCalculator.StratifiedPressureMoments value = geometry.calculateStratifiedPressureMoments(depth, diameter);\n        output.println(diameter + "," + depth + "," + value.getLiquidMoment() + "," + value.getGasMoment()\n            + "," + value.getLiquidArea() + "," + value.getGasArea());\n      }\n    }\n  }\n}\n')
SOURCE = REPO / 'src/main/java/neqsim/process/equipment/pipeline/twophasepipe/closure/GeometryCalculator.java'
raw = SOURCE.read_bytes()
if hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() != 'e62e0a03a8eeb92d9cbad04b109142501a0903a4':
    raise ValueError('Unexpected pressure-moment source blob')
subprocess.run(['java', '--module', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '8', '-d', str(CLASSES), str(SOURCE), str(PROBE)], check=True)
fractions = sorted(set([0.0, 0.25, 0.5, 0.75, 1.0] + [10.0**-k for k in range(1, 17)] + [1.0 - 10.0**-k for k in range(1, 17)]))
inputs = ''.join(f'{d!r},{d*f!r}\n' for d in [0.001, 0.01, 0.2, 1.0148, 3.0, 10.0] for f in fractions)
(ROOT / 'inputs.csv').write_text(inputs)
subprocess.run(['java', '-cp', str(CLASSES), 'PressureMomentProbe', str(ROOT / 'native.csv')], input=inputs, text=True, check=True)

def segment(h, d, terms):
    """Integrate each binomial term analytically, including circular chord width."""
    f = h / d
    coefficient = Q(1)
    power = Q(1)
    area_sum = Q(0)
    moment_sum = Q(0)
    for n in range(terms):
        a = Q(n) + Q('1.5')
        area_sum += coefficient * power / a
        moment_sum += coefficient * power / (a * (a + 1))
        coefficient *= (Q(n) - Q('0.5')) / Q(n + 1)
        power *= f
    area = 2 * h * (d*h).sqrt() * area_sum
    return area, 2 * h*h * (d*h).sqrt() * moment_sum

rows = []
with localcontext() as context:
    context.prec = 80
    # Independent pi via Machin's formula with convergent rational arctangent series.
    def atan(x):
        total = Q(0)
        for n in range(140):
            total += (-1)**n * x**(2*n+1) / Q(2*n+1)
        return total
    pi = 16 * atan(Q(1)/5) - 4 * atan(Q(1)/239)
    for native in read_native(ROOT / 'native.csv', inputs):
        d, h = [Q.from_float(float(native[k])) for k in ['diameter_m', 'depth_m']]
        b = min(h, d-h)
        area, moment = segment(b, d, 160)
        area_coarse, moment_coarse = segment(b, d, 100)
        whole_area = pi*d*d/4
        whole_moment = (h-d/2)*whole_area
        al, ag, jl, jg = (area, whole_area-area, moment, whole_moment-moment) if h <= d/2 else (whole_area-area, area, whole_moment+moment, -moment)
        errors = [abs(Q.from_float(float(native[key]))-reference) for key, reference in [('liquid_m3', jl), ('gas_m3', jg)]]
        limits = [Q('1e-14')*d**3 + Q('1e-10')*abs(v) for v in [jl, jg]]
        if not all(error <= limit for error, limit in zip(errors, limits)):
            raise ValueError('Moment tolerance failed: ' + str(native))
        # Additional relative trace check avoids the loose absolute full-pipe scale.
        rel_m = abs(Q.from_float(float(native['liquid_m3' if h <= d/2 else 'gas_m3']))- (jl if h <= d/2 else jg))/moment if moment else Q(0)
        rel_a = abs(Q.from_float(float(native['liquid_m2' if h <= d/2 else 'gas_m2']))-area)/area if area else Q(0)
        refinement = abs(moment-moment_coarse)/d**3
        rows.append({**native, 'reference_liquid_m3':str(jl), 'reference_gas_m3':str(jg), 'error_over_D3':str(max(errors)/d**3), 'minority_relative_moment_error':str(rel_m), 'minority_relative_area_error':str(rel_a), 'series_refinement_over_D3':str(refinement)})
with (ROOT/'audit.csv').open('w') as output:
    writer = csv.DictWriter(output, fieldnames=list(rows[0]), lineterminator="\n"); writer.writeheader(); writer.writerows(rows)
summary = {'reference_head':'2561eb8df00baab7f7b33f55ca0c6b27c629f8ac',
           'source_parent_sha':'3dbfef974a2e8d50ce59f44030b2c5dad0f7ad4b',
           'source_blob':'e62e0a03a8eeb92d9cbad04b109142501a0903a4',
           'rows':len(rows),'precision':80,'terms':[100,160]}
for key in ['error_over_D3','minority_relative_moment_error','minority_relative_area_error','series_refinement_over_D3']:
    summary['maximum_'+key]=str(max(Q(row[key]) for row in rows))
(ROOT/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))

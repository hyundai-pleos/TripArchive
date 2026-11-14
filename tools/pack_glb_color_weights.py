#!/usr/bin/env python3
"""Pack glTF colours/weights into standard normalized integer accessors.

Positions, normals, UVs, joint indices and animation keys are not quantized.
Weight rounding uses largest remainders so each tuple sums to exactly 65535.
"""
import argparse
import copy
import json
import math
import struct
import tempfile
from pathlib import Path
from build_character_gaits import Glb
from compact_glb_losslessly import compact, records


def pack(source, destination):
    glb = Glb(source)
    original = Glb(source)
    converted = {}
    max_error = {'COLOR_0': 0.0, 'WEIGHTS_0': 0.0}
    counts = {'COLOR_0': 0, 'WEIGHTS_0': 0}
    for mesh in glb.doc['meshes']:
        for primitive in mesh['primitives']:
            for semantic, maximum, component, fmt in [('COLOR_0', 255, 5121, '<4B'), ('WEIGHTS_0', 65535, 5123, '<4H')]:
                if semantic not in primitive['attributes']:
                    continue
                index = primitive['attributes'][semantic]
                if (semantic, index) in converted:
                    primitive['attributes'][semantic] = converted[(semantic, index)]
                    continue
                accessor = glb.doc['accessors'][index]
                if accessor['componentType'] != 5126 or accessor['type'] not in ('VEC3', 'VEC4'):
                    raise ValueError('Expected float VEC4 for ' + semantic)
                width = int(accessor['type'][-1])
                fmt = '<4' + ('B' if component == 5121 else 'H')
                output = []
                for data in records(original, index):
                    values = struct.unpack('<' + str(width) + 'f', data)
                    if not all(math.isfinite(v) and 0 <= v <= 1 for v in values):
                        raise ValueError('Cannot represent value in normalized accessor')
                    if semantic == 'WEIGHTS_0':
                        total = sum(values)
                        if abs(total - 1.0) > 1e-5:
                            raise ValueError('Source weights must be normalized')
                        exact = [v / total * maximum for v in values]
                        packed = [math.floor(v) for v in exact]
                        remainder = maximum - sum(packed)
                        order = sorted(range(4), key=lambda i: -(exact[i] - packed[i]))
                        for i in order[:remainder]:
                            packed[i] += 1
                        assert sum(packed) == maximum
                    else:
                        packed = [round(v * maximum) for v in values]
                    max_error[semantic] = max(max_error[semantic], max(abs(a-b/maximum) for a, b in zip(values, packed)))
                    if semantic == 'COLOR_0' and width == 3:
                        packed.append(255)  # implicit RGB alpha, four-byte vertex alignment
                    output.append(struct.pack(fmt, *packed))
                new = copy.deepcopy(accessor)
                new.pop('byteOffset', None)
                new.pop('min', None)
                new.pop('max', None)
                new['bufferView'] = glb.append_view(b''.join(output))
                new['componentType'] = component
                new['type'] = 'VEC4'
                new['normalized'] = True
                new_index = len(glb.doc['accessors'])
                glb.doc['accessors'].append(new)
                converted[(semantic, index)] = new_index
                primitive['attributes'][semantic] = new_index
                counts[semantic] += accessor['count']
    with tempfile.TemporaryDirectory() as directory:
        intermediate = Path(directory) / 'packed.glb'
        glb.path = intermediate
        glb.write()
        report = compact(intermediate, destination)
    packed = Glb(destination)
    for old_mesh, new_mesh in zip(original.doc['meshes'], packed.doc['meshes']):
        for old, new in zip(old_mesh['primitives'], new_mesh['primitives']):
            def indices(g, p):
                a = g.doc['accessors'][p['indices']]
                fmt = {5121: '<B', 5123: '<H', 5125: '<I'}[a['componentType']]
                return [struct.unpack(fmt, b)[0] for b in records(g, p['indices'])]
            old_indices, new_indices = indices(original, old), indices(packed, new)
            for semantic in old['attributes']:
                if semantic in max_error:
                    continue
                old_data = records(original, old['attributes'][semantic])
                new_data = records(packed, new['attributes'][semantic])
                assert all(old_data[a] == new_data[b] for a, b in zip(old_indices, new_indices))
    report.update(beforeBytes=source.stat().st_size, afterBytes=destination.stat().st_size,
                  maxAbsoluteError=max_error, convertedVertices=counts,
                  weightIntegerSum=65535, nonQuantizedTriangleAttributesExact=True)
    report.pop('expandedAttributesExact')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    print(json.dumps(pack(args.source, args.destination), indent=2))

#!/usr/bin/env python3
"""Deduplicate exact complete vertex records and remove unreferenced GLB bytes.

No quantization, tolerance, decimation or animation resampling is performed.
All attributes (including joint weights and morph targets) participate in the
vertex key. Triangle-expanded attribute bytes are verified before writing.
"""
import argparse
import copy
import json
import struct
from pathlib import Path
from build_character_gaits import Glb

SIZES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
WIDTHS = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def records(glb, index):
    a = glb.doc['accessors'][index]
    if 'sparse' in a or a['type'] not in WIDTHS:
        raise ValueError('Sparse or padded matrix accessor is not supported')
    v = glb.doc['bufferViews'][a['bufferView']]
    if v.get('extensions') or a.get('extensions'):
        raise ValueError('Accessor/view extensions require a dedicated codec')
    width = SIZES[a['componentType']] * WIDTHS[a['type']]
    stride = v.get('byteStride', width)
    start = v.get('byteOffset', 0) + a.get('byteOffset', 0)
    return [bytes(glb.binary[start+i*stride:start+i*stride+width]) for i in range(a['count'])]


def compact(source, destination):
    original = Glb(source)
    result = Glb(source)
    result.doc = copy.deepcopy(original.doc)
    result.binary = bytearray()
    result.doc['accessors'] = []
    result.doc['bufferViews'] = []
    imported = {}
    written = {}

    def append(template, data):
        accessor = copy.deepcopy(template)
        accessor.pop('byteOffset', None)
        accessor.pop('bufferView', None)
        accessor['count'] = len(data)
        payload = b''.join(data)
        key = (json.dumps(accessor, sort_keys=True), payload)
        if key in written:
            return written[key]
        accessor['bufferView'] = result.append_view(payload)
        index = len(result.doc['accessors'])
        result.doc['accessors'].append(accessor)
        written[key] = index
        return index

    def preserve(index):
        if index not in imported:
            imported[index] = append(original.doc['accessors'][index], records(original, index))
        return imported[index]

    vertices_before = vertices_after = 0
    for mesh in result.doc.get('meshes', []):
        for primitive in mesh['primitives']:
            if primitive.get('extensions'):
                raise ValueError('Compressed primitive is not supported')
            attribute_maps = [primitive['attributes']] + primitive.get('targets', [])
            attributes = [(mapping, name, index, records(original, index))
                          for mapping in attribute_maps for name, index in mapping.items()]
            count = len(attributes[0][3])
            assert all(len(a[3]) == count for a in attributes)
            unique, remap, chosen = {}, [], []
            for i in range(count):
                key = tuple(a[3][i] for a in attributes)
                index = unique.get(key)
                if index is None:
                    index = len(chosen)
                    unique[key] = index
                    chosen.append(i)
                remap.append(index)
            if 'indices' in primitive:
                old_index = primitive['indices']
                kind = original.doc['accessors'][old_index]['componentType']
                fmt = {5121: '<B', 5123: '<H', 5125: '<I'}[kind]
                old_indices = [struct.unpack(fmt, b)[0] for b in records(original, old_index)]
            else:
                old_indices = list(range(count))
            new_indices = [remap[i] for i in old_indices]
            for mapping, name, old, data in attributes:
                reduced = [data[i] for i in chosen]
                # Exact per-index equality preserves topology, normals, UVs,
                # colours, joints, weights and morphs, including seams.
                assert all(data[a] == reduced[b] for a, b in zip(old_indices, new_indices))
                mapping[name] = append(original.doc['accessors'][old], reduced)
            kind, fmt = (5123, '<H') if len(chosen) <= 65536 else (5125, '<I')
            primitive['indices'] = append({'componentType': kind, 'type': 'SCALAR', 'min': [min(new_indices)], 'max': [max(new_indices)]}, [struct.pack(fmt, i) for i in new_indices])
            vertices_before += count
            vertices_after += len(chosen)
    for animation in result.doc.get('animations', []):
        for sampler in animation['samplers']:
            for key in ('input', 'output'):
                sampler[key] = preserve(sampler[key])
    for skin in result.doc.get('skins', []):
        if 'inverseBindMatrices' in skin:
            skin['inverseBindMatrices'] = preserve(skin['inverseBindMatrices'])
    for image in result.doc.get('images', []):
        if 'bufferView' in image:
            view = original.doc['bufferViews'][image['bufferView']]
            start = view.get('byteOffset', 0)
            image['bufferView'] = result.append_view(bytes(original.binary[start:start+view['byteLength']]))
    # Confirm animation accessors and every channel/other property unchanged.
    for old, new in zip(original.doc.get('animations', []), result.doc.get('animations', [])):
        checked = copy.deepcopy(new)
        for a, b in zip(old['samplers'], checked['samplers']):
            for key in ('input', 'output'):
                assert records(original, a[key]) == records(result, b[key])
                b[key] = a[key]
        assert old == checked
    result.path = destination
    result.write()
    return {'beforeBytes': source.stat().st_size, 'afterBytes': destination.stat().st_size,
            'beforeVertices': vertices_before, 'afterVertices': vertices_after,
            'expandedAttributesExact': True, 'animationExact': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    print(json.dumps(compact(args.source, args.destination), indent=2))

#!/usr/bin/env python3
"""Append facial morphs without rewriting the authored neutral geometry or rig.

Uses only the standard library. Unified mobile bodies are classified by welded
connected components in character coordinates, never by individual spatial
vertices (which could pick skin or clothes). Catchlights share their eye pivot;
the tongue shares the mouth pivot. All body/gait attributes remain untouched.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from build_character_gaits import ASSET_ROOT, Glb, point, world_matrices
from restore_child_skinning import inverse_matrix, read_attribute

KEY = "characterReactions"
NAMES = ["REACTION_CALM", "REACTION_WAITING", "REACTION_JOY", "REACTION_MOVING", "REACTION_WIND"]
# eye height, eye smile arch, mouth width, mouth height
POSES = [(0.90, 0.0, 0.85, 0.50), (0.28, -0.28, 0.95, 0.10),
         (0.22, 0.65, 1.28, 1.50), (1.22, 0.12, 1.22, 1.28),
         (0.62, 0.0, 1.40, 1.60)]


def bounds(points):
    return ([min(p[k] for p in points) for k in range(3)],
            [max(p[k] for p in points) for k in range(3)])


def components(glb, primitive, positions):
    parents = list(range(len(positions)))
    def find(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i
    welded = {}
    for i, p in enumerate(positions):
        key = tuple(round(x, 5) for x in p)
        if key in welded:
            parents[find(i)] = find(welded[key])
        else:
            welded[key] = i
    indices = [v[0] for v in read_attribute(glb, primitive["indices"])]
    for i in range(0, len(indices), 3):
        a, b, c = map(find, indices[i:i + 3])
        parents[b] = a
        parents[c] = a
    result = {}
    for i in range(len(positions)):
        result.setdefault(find(i), []).append(i)
    return list(result.values())


def classify_component(points):
    lo, hi = bounds(points)
    # The neutral adult/parent face components are authored in this region.
    # Bounds must contain the WHOLE connected component: skull/skin cannot match.
    if lo[2] > 0.41 and lo[1] > 1.49 and hi[1] < 1.74:
        if lo[0] > 0.14 and hi[0] < 0.33:
            return "right"
        if lo[0] > -0.33 and hi[0] < -0.14:
            return "left"
    if lo[2] > 0.44 and lo[1] > 1.30 and hi[1] < 1.46 and lo[0] > -0.11 and hi[0] < 0.11:
        return "mouth"
    return None


def normalized(v):
    length = math.sqrt(sum(x*x for x in v))
    return tuple(x / length for x in v) if length else (0.0, 0.0, 1.0)


def transpose_vector(m, v):
    return tuple(sum(m[k*4+j] * v[j] for j in range(3)) for k in range(3))


def sparse_delta(glb, count, ids, values):
    index = glb.append_sparse_vec3(count, ids, values)
    accessor = glb.doc["accessors"][index]
    accessor["min"] = [min([0.0] + values[k::3]) for k in range(3)]
    accessor["max"] = [max([0.0] + values[k::3]) for k in range(3)]
    return index


def build(path):
    glb = Glb(path)
    doc = glb.doc
    extras = doc.setdefault("asset", {}).setdefault("extras", {})
    if KEY in extras:
        return {"asset": str(path.relative_to(ASSET_ROOT)), "status": "unchanged"}
    worlds = world_matrices(doc)
    entries = []
    grouped = {"left": [], "right": [], "mouth": []}
    for ni, node in enumerate(doc["nodes"]):
        name = node.get("name", "")
        if "mesh" not in node:
            continue
        explicit = name.startswith("FACE_default|")
        unified = name in ("BODY|adult", "BODY|parent")
        if not (explicit or unified):
            continue
        mesh = doc["meshes"][node["mesh"]]
        if unified:
            # The atlas bakes neutral eye contours onto skin. They remain visible
            # after eye morphs; use authored vertex normals on this body material.
            # Clothing/accessory materials and base color textures stay intact.
            for primitive in mesh["primitives"]:
                doc["materials"][primitive["material"]].pop("normalTexture", None)
        for primitive in mesh["primitives"]:
            positions = glb.read_float_vec3(primitive["attributes"]["POSITION"])
            points = [point(worlds[ni], p) for p in positions]
            selected = {}
            catchlights = set()
            if explicit:
                part = name.split("|", 1)[1]
                if part.startswith(("eye", "glint")):
                    group = "left" if sum(p[0] for p in points) < 0 else "right"
                elif part.startswith(("open_mouth", "smile_inset", "tongue")):
                    group = "mouth"
                else:
                    continue
                selected = dict.fromkeys(range(len(points)), group)
                if part.startswith(("glint", "eye_catchlight")):
                    catchlights.update(selected)
            else:
                for component in components(glb, primitive, positions):
                    group = classify_component([points[i] for i in component])
                    if group:
                        selected.update(dict.fromkeys(component, group))
                        lo, hi = bounds([points[i] for i in component])
                        if group != "mouth" and hi[0]-lo[0] < 0.07 and hi[1]-lo[1] < 0.08:
                            catchlights.update(component)
            if selected:
                for i, group in selected.items():
                    grouped[group].append(points[i])
                entries.append((ni, mesh, primitive, positions, points, selected, catchlights))
    if not entries or any(not p for p in grouped.values()):
        raise ValueError(f"Missing semantic face groups: {path}: { {k: len(v) for k,v in grouped.items()} }")
    pivots = {}
    for group, pts in grouped.items():
        lo, hi = bounds(pts)
        pivots[group] = ([(a+b)/2 for a,b in zip(lo,hi)], [(b-a)/2 for a,b in zip(lo,hi)])
    affected = 0
    for ni, mesh, prim, positions, points, selected, catchlights in entries:
        if prim.get("targets"):
            raise ValueError(f"Face already contains unrelated morph targets: {path}")
        inv = inverse_matrix(worlds[ni])
        normals = glb.read_float_vec3(prim["attributes"]["NORMAL"])
        targets = []
        ids = sorted(selected)
        for pose_index, (eye_height, arch, mouth_width, mouth_height) in enumerate(POSES):
            delta_positions, delta_normals = [], []
            for i in ids:
                group = selected[i]
                center, half = pivots[group]
                sx, sy = (mouth_width, mouth_height) if group == "mouth" else (1.0, eye_height)
                bend = (0.60 if pose_index == 1 else 0.0) if group == "mouth" else arch
                parent_eye = doc["nodes"][ni].get("name") == "BODY|parent" and group != "mouth"
                if parent_eye and pose_index in (1, 2):
                    # Keep the native thin stroke visible instead of flattening
                    # it with the closed-oval mapping used by child/adult eyes.
                    sy, bend = (0.80, -0.65) if pose_index == 1 else (1.15, 0.35)
                x, y, z = points[i]
                if parent_eye and pose_index in (1, 2):
                    z += 0.01
                u = (x-center[0]) / half[0]
                q = (center[0] + sx*(x-center[0]),
                     center[1] + sy*(y-center[1]) + bend*half[1]*(1-u*u), z)
                # Planar Jacobian; its inverse transpose keeps shading consistent.
                a, b, c, d = sx, 0.0, -2*bend*half[1]*u/half[0], sy
                if pose_index == 4 and group != "mouth":
                    direction = 1.0 if group == "left" else -1.0
                    if doc["nodes"][ni].get("name") == "BODY|parent":
                        # Rotate the authored curved stroke, rather than folding a
                        # shallow arc as though it were a filled oval. Both sides
                        # retain their thickness and positive orientation.
                        a, b, c, d = 0.0, direction*1.5, -direction*0.85, 0.0
                        q = (center[0] + b*(y-center[1]),
                             center[1] + c*(x-center[0]), z + 0.01)
                    else:
                        # Rounded, wider chevrons with enough stroke thickness.
                        # Shared pivots keep the eye and its catchlight together.
                        sx = 0.32
                        v = (y-center[1]) / half[1]
                        rounded = math.sqrt(v*v + 0.025)
                        fold = direction * half[0] * 1.6
                        q = (center[0] + sx*(x-center[0]) + fold*(0.5-rounded),
                             center[1] + sy*(y-center[1]), z + 0.01)
                        a, b, c, d = sx, -fold*v/(rounded*half[1]), 0.0, sy
                if i in catchlights and pose_index in (1, 2, 4):
                    # Closed eyes have no glossy pupil highlight. Move the
                    # separate highlight behind the face only in these poses.
                    q = (q[0], q[1], q[2] - 0.08)
                local = point(inv, q)
                delta_positions.extend(local[k]-positions[i][k] for k in range(3))
                nx, ny, nz = normalized(transpose_vector(inv, normals[i]))
                det = a*d - b*c
                new_world = ((d*nx-c*ny)/det, (-b*nx+a*ny)/det, nz)
                new_local = normalized(transpose_vector(worlds[ni], new_world))
                delta_normals.extend(new_local[k]-normals[i][k] for k in range(3))
            targets.append({"POSITION": sparse_delta(glb, len(positions), ids, delta_positions),
                            "NORMAL": sparse_delta(glb, len(positions), ids, delta_normals)})
        prim["targets"] = targets
        mesh["weights"] = [0.0]*len(NAMES)
        mesh.setdefault("extras", {})["targetNames"] = NAMES
        affected += len(ids)
    extras[KEY] = {"version": 1, "targetNames": NAMES,
                   "method": "neutral-face-only sparse position and normal deltas; shared eye/mouth pivots",
                   "affectedVertices": affected}
    glb.write()
    return {"asset": str(path.relative_to(ASSET_ROOT)), "vertices": affected,
            "groups": {k:len(v) for k,v in grouped.items()}, "addedBytes": path.stat().st_size-len(glb.raw)}


if __name__ == "__main__":
    paths = sorted(ASSET_ROOT.glob("catalog/*/outfits/*.glb"))
    # Legacy composite is still supported by the renderer, although not selected
    # by the current catalog. It uses separately named neutral-face meshes.
    paths += sorted(ASSET_ROOT.glob("catalog/adult/adult_composite.glb"))
    for path in paths:
        print(json.dumps(build(path), ensure_ascii=False))

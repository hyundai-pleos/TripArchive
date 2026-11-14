#!/usr/bin/env python3
"""Append in-place Walk/Run animation data to the active character GLBs.

Run from any directory with:
    python3 triparchive-24/tools/build_character_gaits.py

The builder is stdlib-only and idempotent. It appends sparse morph target
position/normal deltas and animation accessors to the GLB BIN chunk, leaving
the source POSITION/NORMAL accessors and their bounds untouched. Parent
outfits already have a skinned Walk clip, so those files receive a faster,
retimed Run clip using the same gait pose.
"""

from __future__ import annotations

import copy
import json
import math
import struct
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets/characters"
BUILD_KEY = "codexCharacterGaits"
TARGET_NAMES = ["arm_left", "arm_right", "leg_left", "leg_right"]
SAMPLE_COUNT = 9
WALK_DURATION = 1.08
RUN_DURATION = 0.66


class Glb:
    def __init__(self, path: Path):
        self.path = path
        self.raw = path.read_bytes()
        magic, version, declared_length = struct.unpack_from("<III", self.raw, 0)
        if magic != 0x46546C67 or version != 2 or declared_length != len(self.raw):
            raise ValueError(f"Not a valid GLB 2.0 file: {path}")
        self.chunks: list[tuple[int, bytes]] = []
        offset = 12
        while offset < len(self.raw):
            length, kind = struct.unpack_from("<II", self.raw, offset)
            payload = self.raw[offset + 8 : offset + 8 + length]
            self.chunks.append((kind, payload))
            offset += 8 + length
        self.doc = json.loads(next(data for kind, data in self.chunks if kind == 0x4E4F534A))
        self.bin_index = next(i for i, (kind, _) in enumerate(self.chunks) if kind == 0x004E4942)
        bin_payload = self.chunks[self.bin_index][1]
        declared_bin_length = self.doc["buffers"][0]["byteLength"]
        self.binary = bytearray(bin_payload[:declared_bin_length])
        self.doc.setdefault("buffers", [{}])[0]["byteLength"] = len(self.binary)

    def append_bytes(self, data: bytes) -> int:
        while len(self.binary) % 4:
            self.binary.append(0)
        offset = len(self.binary)
        self.binary.extend(data)
        return offset

    def append_view(self, data: bytes) -> int:
        offset = self.append_bytes(data)
        view: dict[str, Any] = {"buffer": 0, "byteOffset": offset, "byteLength": len(data)}
        self.doc.setdefault("bufferViews", []).append(view)
        return len(self.doc["bufferViews"]) - 1

    def append_accessor(
        self,
        values: list[float],
        kind: str,
        components: int,
        *,
        mins: list[float] | None = None,
        maxs: list[float] | None = None,
    ) -> int:
        payload = struct.pack("<" + "f" * len(values), *values)
        view = self.append_view(payload)
        accessor: dict[str, Any] = {
            "bufferView": view,
            "componentType": 5126,
            "count": len(values) // components,
            "type": kind,
        }
        if mins is not None:
            accessor["min"] = mins
        if maxs is not None:
            accessor["max"] = maxs
        self.doc.setdefault("accessors", []).append(accessor)
        return len(self.doc["accessors"]) - 1

    def append_sparse_vec3(self, base_count: int, indices: list[int], values: list[float]) -> int:
        if not indices:
            raise ValueError("Sparse accessor needs at least one changed vertex")
        if base_count > 65535:
            index_type, packed_indices = 5125, struct.pack("<" + "I" * len(indices), *indices)
        else:
            index_type, packed_indices = 5123, struct.pack("<" + "H" * len(indices), *indices)
        index_view = self.append_view(packed_indices)
        value_view = self.append_view(struct.pack("<" + "f" * len(values), *values))
        self.doc.setdefault("accessors", []).append(
            {
                "componentType": 5126,
                "count": base_count,
                "type": "VEC3",
                "sparse": {
                    "count": len(indices),
                    "indices": {"bufferView": index_view, "componentType": index_type},
                    "values": {"bufferView": value_view},
                },
            }
        )
        return len(self.doc["accessors"]) - 1

    def read_float_vec3(self, accessor_index: int) -> list[tuple[float, float, float]]:
        accessor = self.doc["accessors"][accessor_index]
        if accessor["componentType"] != 5126 or accessor["type"] != "VEC3":
            raise ValueError(f"Expected FLOAT VEC3 accessor {accessor_index}")
        count = accessor["count"]
        out = [(0.0, 0.0, 0.0)] * count
        if "bufferView" in accessor:
            view = self.doc["bufferViews"][accessor["bufferView"]]
            stride = view.get("byteStride", 12)
            base = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
            out = [struct.unpack_from("<3f", self.binary, base + stride * i) for i in range(count)]
        sparse = accessor.get("sparse")
        if sparse:
            iv = self.doc["bufferViews"][sparse["indices"]["bufferView"]]
            vv = self.doc["bufferViews"][sparse["values"]["bufferView"]]
            ibytes = iv.get("byteOffset", 0) + sparse["indices"].get("byteOffset", 0)
            vbytes = vv.get("byteOffset", 0) + sparse["values"].get("byteOffset", 0)
            index_type = sparse["indices"]["componentType"]
            code, width = {5121: ("B", 1), 5123: ("H", 2), 5125: ("I", 4)}[index_type]
            for i in range(sparse["count"]):
                index = struct.unpack_from("<" + code, self.binary, ibytes + i * width)[0]
                out[index] = struct.unpack_from("<3f", self.binary, vbytes + i * 12)
        return out

    def read_float_scalar(self, accessor_index: int) -> list[float]:
        accessor = self.doc["accessors"][accessor_index]
        if accessor["componentType"] != 5126 or accessor["type"] != "SCALAR":
            raise ValueError(f"Expected FLOAT SCALAR accessor {accessor_index}")
        view = self.doc["bufferViews"][accessor["bufferView"]]
        stride = view.get("byteStride", 4)
        base = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        return [struct.unpack_from("<f", self.binary, base + stride * i)[0] for i in range(accessor["count"])]

    def write(self) -> None:
        self.doc["buffers"][0]["byteLength"] = len(self.binary)
        json_chunk = json.dumps(self.doc, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        json_chunk += b" " * ((-len(json_chunk)) % 4)
        binary_chunk = bytes(self.binary) + b"\0" * ((-len(self.binary)) % 4)
        chunks = list(self.chunks)
        chunks[0] = (0x4E4F534A, json_chunk)
        chunks[self.bin_index] = (0x004E4942, binary_chunk)
        encoded = bytearray(struct.pack("<III", 0x46546C67, 2, 0))
        for kind, payload in chunks:
            encoded.extend(struct.pack("<II", len(payload), kind))
            encoded.extend(payload)
        struct.pack_into("<I", encoded, 8, len(encoded))
        self.path.write_bytes(encoded)


def normalize(v: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(sum(x * x for x in v))
    return (0.0, 1.0, 0.0) if length < 1e-10 else tuple(x / length for x in v)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def identity() -> list[float]:
    return [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]


def mat_mul(a: list[float], b: list[float]) -> list[float]:
    return [sum(a[k * 4 + row] * b[col * 4 + k] for k in range(4)) for col in range(4) for row in range(4)]


def node_matrix(node: dict[str, Any]) -> list[float]:
    if "matrix" in node:
        return list(node["matrix"])
    x, y, z, w = node.get("rotation", [0.0, 0.0, 0.0, 1.0])
    sx, sy, sz = node.get("scale", [1.0, 1.0, 1.0])
    tx, ty, tz = node.get("translation", [0.0, 0.0, 0.0])
    return [
        (1 - 2 * (y * y + z * z)) * sx,
        (2 * (x * y + z * w)) * sx,
        (2 * (x * z - y * w)) * sx,
        0.0,
        (2 * (x * y - z * w)) * sy,
        (1 - 2 * (x * x + z * z)) * sy,
        (2 * (y * z + x * w)) * sy,
        0.0,
        (2 * (x * z + y * w)) * sz,
        (2 * (y * z - x * w)) * sz,
        (1 - 2 * (x * x + y * y)) * sz,
        0.0,
        tx,
        ty,
        tz,
        1.0,
    ]


def point(m: list[float], p: tuple[float, float, float]) -> tuple[float, float, float]:
    x, y, z = p
    return (
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14],
    )


def inverse3(m: list[float]) -> list[float]:
    a, b, c = m[0], m[4], m[8]
    d, e, f = m[1], m[5], m[9]
    g, h, i = m[2], m[6], m[10]
    det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if abs(det) < 1e-12:
        raise ValueError("Cannot animate a mesh with a singular node transform")
    inv = 1.0 / det
    return [
        (e * i - f * h) * inv,
        (f * g - d * i) * inv,
        (d * h - e * g) * inv,
        (c * h - b * i) * inv,
        (a * i - c * g) * inv,
        (b * g - a * h) * inv,
        (b * f - c * e) * inv,
        (c * d - a * f) * inv,
        (a * e - b * d) * inv,
    ]


def transform3(m: list[float], v: tuple[float, float, float]) -> tuple[float, float, float]:
    x, y, z = v
    return (m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z)


def transpose_transform3(m: list[float], v: tuple[float, float, float]) -> tuple[float, float, float]:
    x, y, z = v
    return (m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z)


def world_matrices(doc: dict[str, Any]) -> list[list[float]]:
    parents: dict[int, int] = {}
    for parent, node in enumerate(doc["nodes"]):
        for child in node.get("children", []):
            parents[child] = parent
    result: list[list[float] | None] = [None] * len(doc["nodes"])

    def resolve(index: int) -> list[float]:
        if result[index] is not None:
            return result[index]  # type: ignore[return-value]
        local = node_matrix(doc["nodes"][index])
        full = mat_mul(resolve(parents[index]), local) if index in parents else local
        result[index] = full
        return full

    return [resolve(i) for i in range(len(doc["nodes"]))]


def axis_rotate_x(p: tuple[float, float, float], pivot: tuple[float, float, float], angle: float) -> tuple[float, float, float]:
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    c, s = math.cos(angle), math.sin(angle)
    return (pivot[0] + x, pivot[1] + c * y - s * z, pivot[2] + s * y + c * z)


def gait_effect(
    p: tuple[float, float, float],
    target: str,
    role: str,
) -> tuple[tuple[float, float, float], float]:
    adult = role == "adult"
    if target.startswith("arm_"):
        side = -1.0 if target.endswith("left") else 1.0
        shoulder_x = 0.18 if adult else 0.16
        shoulder_y = 0.92 if adult else 0.64
        threshold = shoulder_x + max(0.0, shoulder_y - p[1]) * 0.53
        mask = smoothstep(threshold - 0.045, threshold + 0.035, side * p[0])
        low, high = ((0.30, 1.12) if adult else (0.20, 0.79))
        mask *= smoothstep(low, low + 0.08, p[1]) * (1.0 - smoothstep(high - 0.09, high, p[1]))
        pivot = (side * shoulder_x, shoulder_y, 0.0)
        angle = (0.28 if adult else 0.30) * side
    else:
        side = -1.0 if target.endswith("left") else 1.0
        hip_x = 0.10 if adult else 0.095
        hip_y = 0.39 if adult else 0.19
        threshold = hip_x + max(0.0, hip_y - p[1]) * 0.27
        mask = smoothstep(threshold - 0.025, threshold + 0.025, side * p[0])
        fade_start, fade_end = ((0.47, 0.63) if adult else (0.28, 0.43))
        mask *= 1.0 - smoothstep(fade_start, fade_end, p[1])
        pivot = (side * hip_x, hip_y, 0.0)
        angle = -(0.27 if adult else 0.30) * side
    if mask < 1e-5:
        return (0.0, 0.0, 0.0), 0.0
    applied_angle = angle * mask
    moved = axis_rotate_x(p, pivot, applied_angle)
    return (moved[0] - p[0], moved[1] - p[1], moved[2] - p[2]), applied_angle


def quaternion_for_sway(phase: float, strength: float) -> list[float]:
    # Tiny shoulder roll/pitch only; no translation, preserving the authored center.
    pitch = 0.010 * strength * math.cos(phase)
    roll = 0.014 * strength * math.sin(phase)
    cx, sx = math.cos(pitch / 2), math.sin(pitch / 2)
    cz, sz = math.cos(roll / 2), math.sin(roll / 2)
    return [sx * cz, -sx * sz, cx * sz, cx * cz]


def add_root_gait(
    glb: Glb,
    root_node: int,
    name: str,
    duration: float,
    weight_amplitude: float,
    morph_nodes: list[int],
    *,
    root_strength: float = 1.0,
) -> None:
    times = [duration * i / (SAMPLE_COUNT - 1) for i in range(SAMPLE_COUNT)]
    time_acc = glb.append_accessor(times, "SCALAR", 1, mins=[0.0], maxs=[duration])
    phase = [2.0 * math.pi * t / duration for t in times]
    rotations = [component for angle in phase for component in quaternion_for_sway(angle, root_strength)]
    rotation_acc = glb.append_accessor(rotations, "VEC4", 4)
    samplers: list[dict[str, Any]] = [{"input": time_acc, "output": rotation_acc, "interpolation": "LINEAR"}]
    channels: list[dict[str, Any]] = [
        {"sampler": 0, "target": {"node": root_node, "path": "rotation"}}
    ]
    if morph_nodes:
        weights: list[float] = []
        for angle in phase:
            wave = math.sin(angle) * weight_amplitude
            weights.extend([wave, -wave, -wave, wave])
        weight_acc = glb.append_accessor(weights, "SCALAR", 1)
        sampler_index = len(samplers)
        samplers.append({"input": time_acc, "output": weight_acc, "interpolation": "LINEAR"})
        channels.extend(
            {"sampler": sampler_index, "target": {"node": node, "path": "weights"}}
            for node in morph_nodes
        )
    glb.doc.setdefault("animations", []).append({"name": name, "samplers": samplers, "channels": channels})


def add_morph_gaits(glb: Glb, role: str, root_node: int) -> None:
    matrices = world_matrices(glb.doc)
    touched_nodes: list[int] = []
    mesh_originals: dict[str, Any] = {}
    for node_index, node in enumerate(glb.doc["nodes"]):
        mesh_index = node.get("mesh")
        if mesh_index is None:
            continue
        node_name = node.get("name", "")
        if node_name == "Child" or node_name.startswith("BODY|") or node_name.startswith("FACE_"):
            continue
        if role == "adult" and not node_name.startswith("OUTFIT_"):
            continue
        mesh = glb.doc["meshes"][mesh_index]
        original_weights = copy.deepcopy(mesh.get("weights"))
        old_target_counts = [len(p.get("targets", [])) for p in mesh.get("primitives", [])]
        mesh_originals[str(mesh_index)] = {"weights": original_weights, "targetCounts": old_target_counts}
        matrix = matrices[node_index]
        inv = inverse3(matrix)
        inv_transpose = [inv[0], inv[3], inv[6], inv[1], inv[4], inv[7], inv[2], inv[5], inv[8]]
        linear_transpose = [matrix[0], matrix[1], matrix[2], matrix[4], matrix[5], matrix[6], matrix[8], matrix[9], matrix[10]]
        mesh_has_deltas = False
        node_has_deltas = False
        for primitive in mesh["primitives"]:
            attrs = primitive.get("attributes", {})
            position_index = attrs.get("POSITION")
            if position_index is None:
                continue
            positions = glb.read_float_vec3(position_index)
            normals = glb.read_float_vec3(attrs["NORMAL"]) if "NORMAL" in attrs else None
            world_positions = [point(matrix, p) for p in positions]
            primitive_changes: list[tuple[list[int], list[float], list[float], list[float]]] = []
            primitive_has_delta = False
            for target_name in TARGET_NAMES:
                sparse_indices: list[int] = []
                position_deltas: list[float] = []
                normal_deltas: list[float] = []
                for vertex_index, world_position in enumerate(world_positions):
                    world_delta, applied_angle = gait_effect(world_position, target_name, role)
                    if sum(x * x for x in world_delta) < 1e-14:
                        continue
                    local_delta = transform3(inv, world_delta)
                    if sum(x * x for x in local_delta) < 1e-14:
                        continue
                    sparse_indices.append(vertex_index)
                    position_deltas.extend(local_delta)
                    if normals is not None:
                        world_normal = normalize(transpose_transform3(inv_transpose, normals[vertex_index]))
                        turned = normalize(axis_rotate_x(world_normal, (0.0, 0.0, 0.0), applied_angle))
                        local_normal = normalize(transpose_transform3(linear_transpose, turned))
                        normal_deltas.extend((local_normal[i] - normals[vertex_index][i] for i in range(3)))
                primitive_changes.append((sparse_indices, position_deltas, normal_deltas, [len(positions), len(normals or [])]))
                primitive_has_delta |= bool(sparse_indices)
            if primitive_has_delta:
                primitive_targets: list[dict[str, int]] = []
                for indices, position_deltas, normal_deltas, counts in primitive_changes:
                    if indices:
                        target = {"POSITION": glb.append_sparse_vec3(counts[0], indices, position_deltas)}
                        if counts[1]:
                            target["NORMAL"] = glb.append_sparse_vec3(counts[1], indices, normal_deltas)
                    else:
                        zeros = [0.0] * (counts[0] * 3)
                        target = {"POSITION": glb.append_accessor(zeros, "VEC3", 3)}
                        if counts[1]:
                            target["NORMAL"] = glb.append_accessor(zeros, "VEC3", 3)
                    primitive_targets.append(target)
                primitive["targets"] = primitive_targets
            if primitive_has_delta:
                node_has_deltas = True
                mesh_has_deltas = True
        if mesh_has_deltas:
            touched_nodes.append(node_index)
            mesh["weights"] = [0.0] * len(TARGET_NAMES)
        else:
            # Remove targets and appended zero accessors for meshes outside the
            # limb masks. They have no effect and should not inflate the GLB.
            for primitive in mesh["primitives"]:
                primitive.pop("targets", None)
            mesh_originals.pop(str(mesh_index), None)
    add_root_gait(glb, root_node, "Walk", WALK_DURATION, 0.72, touched_nodes, root_strength=0.8)
    add_root_gait(glb, root_node, "Run", RUN_DURATION, 1.0, touched_nodes, root_strength=1.0)
    glb.doc.setdefault("asset", {}).setdefault("extras", {})[BUILD_KEY] = {
        "kind": "morph-gait-v1",
        "originalBufferLength": len(glb.binary) - sum(
            glb.doc["bufferViews"][v].get("byteLength", 0) for v in []
        ),
        "meshes": mesh_originals,
        "animations": ["Walk", "Run"],
        "notes": "Rest geometry unchanged; sparse vertex deltas drive signed weights.",
    }


def append_root_rotation_clip(
    glb: Glb,
    root_node: int,
    name: str,
    duration: float,
    strength: float,
) -> None:
    times = [duration * i / (SAMPLE_COUNT - 1) for i in range(SAMPLE_COUNT)]
    phase = [2 * math.pi * i / (SAMPLE_COUNT - 1) for i in range(SAMPLE_COUNT)]
    time_acc = glb.append_accessor(times, "SCALAR", 1, mins=[0.0], maxs=[duration])
    rotations = [v for p in phase for v in quaternion_for_sway(p, strength)]
    rotation_acc = glb.append_accessor(rotations, "VEC4", 4)
    glb.doc.setdefault("animations", []).append(
        {
            "name": name,
            "samplers": [{"input": time_acc, "output": rotation_acc, "interpolation": "LINEAR"}],
            "channels": [{"sampler": 0, "target": {"node": root_node, "path": "rotation"}}],
        }
    )


def clone_idle_to_composite(idle_source: Glb, composite: Glb, composite_root: int) -> None:
    idle = next(animation for animation in idle_source.doc.get("animations", []) if animation.get("name") == "Idle")
    rotation_channel = next(
        channel
        for channel in idle["channels"]
        if channel["target"].get("path") == "rotation"
    )
    sampler = idle["samplers"][rotation_channel["sampler"]]
    source_times = idle_source.read_float_scalar(sampler["input"])
    times = composite.append_accessor(source_times, "SCALAR", 1, mins=[min(source_times)], maxs=[max(source_times)])
    source_output = idle_source.doc["accessors"][sampler["output"]]
    out_view = idle_source.doc["bufferViews"][source_output["bufferView"]]
    stride = out_view.get("byteStride", 16)
    base = out_view.get("byteOffset", 0) + source_output.get("byteOffset", 0)
    vals = [
        c
        for i in range(source_output["count"])
        for c in struct.unpack_from("<4f", idle_source.binary, base + i * stride)
    ]
    output = composite.append_accessor(vals, "VEC4", 4)
    composite.doc.setdefault("animations", []).append(
        {
            "name": "Idle",
            "samplers": [{"input": times, "output": output, "interpolation": sampler.get("interpolation", "LINEAR")}],
            "channels": [{"sampler": 0, "target": {"node": composite_root, "path": "rotation"}}],
        }
    )


def rollback_previous_build(glb: Glb) -> None:
    asset_extras = glb.doc.get("asset", {}).get("extras", {})
    metadata = asset_extras.get(BUILD_KEY)
    if not metadata:
        return
    for name in metadata.get("animations", []):
        glb.doc["animations"] = [a for a in glb.doc.get("animations", []) if a.get("name") != name]
    for mesh_key, original in (metadata.get("meshes") or {}).items():
        mesh = glb.doc["meshes"][int(mesh_key)]
        for primitive, count in zip(mesh.get("primitives", []), original.get("targetCounts", [])):
            if count:
                primitive["targets"] = primitive.get("targets", [])[:count]
            else:
                primitive.pop("targets", None)
        if original.get("weights") is None:
            mesh.pop("weights", None)
        else:
            mesh["weights"] = original["weights"]
    # Added accessors and views are append-only. Store their starting indices
    # and byte count in the build metadata so repeated calls can rebuild safely.
    for index in reversed(metadata.get("addedAccessors", [])):
        if index == len(glb.doc["accessors"]) - 1:
            glb.doc["accessors"].pop()
    for index in reversed(metadata.get("addedBufferViews", [])):
        if index == len(glb.doc["bufferViews"]) - 1:
            glb.doc["bufferViews"].pop()
    glb.binary = glb.binary[: metadata["originalBufferLength"]]
    asset_extras.pop(BUILD_KEY, None)


def remember_build_start(glb: Glb) -> dict[str, int]:
    return {
        "originalBufferLength": len(glb.binary),
        "firstAccessor": len(glb.doc.setdefault("accessors", [])),
        "firstBufferView": len(glb.doc.setdefault("bufferViews", [])),
    }


def finalize_build_metadata(glb: Glb, start: dict[str, int], metadata: dict[str, Any]) -> None:
    metadata["originalBufferLength"] = start["originalBufferLength"]
    metadata["addedAccessors"] = list(range(start["firstAccessor"], len(glb.doc.get("accessors", []))))
    metadata["addedBufferViews"] = list(range(start["firstBufferView"], len(glb.doc.get("bufferViews", []))))
    glb.doc.setdefault("asset", {}).setdefault("extras", {})[BUILD_KEY] = metadata


def find_root(doc: dict[str, Any]) -> int:
    for index, node in enumerate(doc["nodes"]):
        if node.get("name") in ("Child", "root"):
            return index
    raise ValueError("Could not find the character root node")


def ensure_animation_root(doc: dict[str, Any], name: str = "CharacterMotionRoot") -> int:
    try:
        return find_root(doc)
    except ValueError:
        # The adult composite is authored as many independent scene roots.
        # Group them under an identity node so one small sway track can move
        # the visible head and outfit together without changing their rest TRS.
        parents: set[int] = set()
        for node in doc["nodes"]:
            parents.update(node.get("children", []))
        roots = [i for i in range(len(doc["nodes"])) if i not in parents]
        root_index = len(doc["nodes"])
        doc["nodes"].append({"name": name, "children": roots})
        active_scene = doc.get("scene", 0)
        doc["scenes"][active_scene]["nodes"] = [root_index]
        return root_index


def build_morph_asset(path: Path, role: str) -> None:
    glb = Glb(path)
    rollback_previous_build(glb)
    start = remember_build_start(glb)
    root_node = ensure_animation_root(glb.doc)
    if role == "adult":
        clone_idle_to_composite(Glb(ASSET_ROOT / "adult_wardrobe.glb"), glb, root_node)
    add_morph_gaits(glb, role, root_node)
    # Replace preliminary metadata with exact append ranges for repeatability.
    metadata = glb.doc["asset"]["extras"][BUILD_KEY]
    metadata["kind"] = "morph-gait-v1"
    metadata["animations"] = ["Idle", "Walk", "Run"] if role == "adult" else ["Walk", "Run"]
    finalize_build_metadata(glb, start, metadata)
    glb.write()


def build_adult_base(path: Path) -> None:
    glb = Glb(path)
    rollback_previous_build(glb)
    start = remember_build_start(glb)
    root_node = find_root(glb.doc)
    append_root_rotation_clip(glb, root_node, "Walk", WALK_DURATION, 0.8)
    append_root_rotation_clip(glb, root_node, "Run", RUN_DURATION, 1.0)
    finalize_build_metadata(
        glb,
        start,
        {"kind": "adult-base-gait-v1", "animations": ["Walk", "Run"], "meshes": {}},
    )
    glb.write()


def add_parent_run(path: Path) -> None:
    glb = Glb(path)
    rollback_previous_build(glb)
    start = remember_build_start(glb)
    walk = next(a for a in glb.doc.get("animations", []) if a.get("name") == "Walk")
    walk_times = [glb.read_float_scalar(s["input"]) for s in walk["samplers"]]
    first = min(values[0] for values in walk_times)
    last = max(values[-1] for values in walk_times)
    samplers: list[dict[str, Any]] = []
    channels = copy.deepcopy(walk["channels"])
    input_remap: dict[int, int] = {}
    for sampler in walk["samplers"]:
        clone = copy.deepcopy(sampler)
        old_input = sampler["input"]
        if old_input not in input_remap:
            times = glb.read_float_scalar(old_input)
            mapped = [(value - first) / max(last - first, 1e-6) * RUN_DURATION for value in times]
            input_remap[old_input] = glb.append_accessor(mapped, "SCALAR", 1, mins=[0.0], maxs=[RUN_DURATION])
        clone["input"] = input_remap[old_input]
        samplers.append(clone)
    glb.doc.setdefault("animations", []).append({"name": "Run", "samplers": samplers, "channels": channels})
    finalize_build_metadata(
        glb,
        start,
        {"kind": "retimed-skinned-run-v1", "animations": ["Run"], "meshes": {}},
    )
    glb.write()


def main() -> int:
    child_paths = sorted((ASSET_ROOT / "catalog/child/outfits").glob("family_*.glb"))
    parent_paths = sorted((ASSET_ROOT / "catalog/parent/outfits").glob("parent_[124].glb"))
    adult_composite = ASSET_ROOT / "catalog/adult/adult_composite.glb"
    adult_base = ASSET_ROOT / "adult_wardrobe.glb"
    if len(child_paths) != 3 or len(parent_paths) != 3:
        raise SystemExit("Expected the three active child and parent outfit GLBs")
    for path in child_paths:
        build_morph_asset(path, "child")
    build_morph_asset(adult_composite, "adult")
    build_adult_base(adult_base)
    for path in parent_paths:
        add_parent_run(path)
    print(f"Updated {len(child_paths) + len(parent_paths) + 2} active character GLBs.")
    print(f"Walk {WALK_DURATION:.2f}s / Run {RUN_DURATION:.2f}s; rest positions and source bounds retained.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

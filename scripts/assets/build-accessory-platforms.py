#!/usr/bin/env python3
"""Derive LOD1 and collision meshes from Accessory Pack 1 OBJs; record FBX LOD0."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

import pymeshlab

SOURCES = {
    "circular": "Circular_Platform.obj",
    "curved_milky_way": "CurvedBase_MilkyWay.obj",
    "curved_pegasus": "CurvedBase_Pegasus.obj",
    "grace_of_god": "Grace_of_God_Platform.obj",
}
FBX_LOD0 = {
    "circular": ("Circular_Platform.fbx", 14423),
    "curved_milky_way": ("CurvedBase_MilkyWay.fbx", 244058),
    "curved_pegasus": ("CurvedBase_Pegasus.fbx", 242670),
    "grace_of_god": ("Grace_of_God_Platform.fbx", 47570),
}


def export(mesh, path):
    with path.open("w", encoding="ascii") as out:
        out.write("# Accessory Pack derivative; triangulated, Y-up metres\n")
        for x, y, z in mesh.vertex_matrix():
            out.write(f"v {x:.7f} {y:.7f} {z:.7f}\n")
        for a, b, c in mesh.face_matrix():
            out.write(f"f {a + 1} {b + 1} {c + 1}\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for key, filename in SOURCES.items():
        source = args.source / filename
        fbx_filename, fbx_triangles = FBX_LOD0[key]
        fbx_source = args.source / fbx_filename
        if not fbx_source.is_file():
            raise FileNotFoundError(fbx_source)
        meshes = pymeshlab.MeshSet()
        meshes.load_new_mesh(str(source))
        count = meshes.current_mesh().face_number()
        export(meshes.current_mesh(), args.output / f"{key}_lod0.obj")
        lod1 = max(3000, int(count * .32))
        meshes.meshing_decimation_quadric_edge_collapse(targetfacenum=lod1, preservetopology=False)
        render_count = meshes.current_mesh().face_number()
        export(meshes.current_mesh(), args.output / f"{key}_lod1.obj")
        if key == "circular":
            # Its small central deck is altered by coarse simplification.
            collision_count = count
            shutil.copyfile(args.output / f"{key}_lod0.obj", args.output / f"{key}_collision.obj")
        else:
            collision = pymeshlab.MeshSet()
            collision.load_new_mesh(str(source))
            collision.meshing_decimation_quadric_edge_collapse(
                targetfacenum=min(6000, max(1800, int(count * .035))), preservetopology=False)
            collision_count = collision.current_mesh().face_number()
            export(collision.current_mesh(), args.output / f"{key}_collision.obj")
        manifest[key] = {
            "source": filename, "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "fbx_source": fbx_filename, "fbx_sha256": hashlib.sha256(fbx_source.read_bytes()).hexdigest(),
            "fbx_import_triangles": fbx_triangles,
            "bundle_lod0_source": filename if key == "circular" else fbx_filename,
            "bundle_lod0_triangles": count if key == "circular" else fbx_triangles,
            "lod0_triangles": count, "lod1_triangles": render_count,
            "collision_triangles": collision_count,
        }
        print(f"{key}: {count} / {render_count} / {collision_count}", flush=True)
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()

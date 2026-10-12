using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEngine;

// Exports the four unique animated source meshes, including FBX material boundaries.
// The binary output is an intermediate build artifact; the FBX is never distributed.
public static class StargateFbxExporter {
    public static void Export() {
        const string source = "Assets/StargateSource/SG_MilkyWay.fbx";
        var root = AssetDatabase.LoadAssetAtPath<GameObject>(source);
        if (root == null) throw new InvalidOperationException("Missing " + source);
        var output = Environment.GetCommandLineArgs().SkipWhile(x => x != "-exportPath").Skip(1).FirstOrDefault();
        if (String.IsNullOrWhiteSpace(output)) throw new ArgumentException("-exportPath is required");
        Directory.CreateDirectory(output);
        var all = root.GetComponentsInChildren<MeshFilter>(true);
        Save(all, "body", x => x.name == "SG_MW_Main", output);
        Save(all, "ring", x => x.name == "SG_MW_InnerRing_Earth_Giza", output);
        Save(all, "chevron-v", x => x.name == "SG_MW_Chevron:Chevron_A", output);
        Save(all, "chevron-block", x => x.name == "SG_MW_ChevronBlock_(1)", output);
    }

    private static void Save(MeshFilter[] all, string name, Func<MeshFilter, bool> match, string folder) {
        var matches = all.Where(match).ToArray();
        if (matches.Length != 1) throw new InvalidOperationException(name + ": expected exactly one source mesh, found " + matches.Length);
        var filter = matches[0];
        var mesh = filter.sharedMesh;
        var renderer = filter.GetComponent<MeshRenderer>();
        if (mesh == null || renderer == null || mesh.subMeshCount != renderer.sharedMaterials.Length)
            throw new InvalidOperationException("Invalid FBX mesh/materials: " + name);
        var path = Path.Combine(folder, name + ".sgmesh");
        using (var writer = new BinaryWriter(File.Create(path))) {
            writer.Write(0x53474D31); // SGM1, little endian
            writer.Write(mesh.vertexCount);
            writer.Write(mesh.subMeshCount);
            var vertices = mesh.vertices;
            var uv = mesh.uv;
            for (var i = 0; i < vertices.Length; i++) {
                writer.Write(vertices[i].x); writer.Write(vertices[i].y); writer.Write(vertices[i].z);
                writer.Write(uv.Length == vertices.Length ? uv[i].x : 0f);
                writer.Write(uv.Length == vertices.Length ? uv[i].y : 0f);
            }
            for (var i = 0; i < mesh.subMeshCount; i++) {
                writer.Write(renderer.sharedMaterials[i].name);
                var indices = mesh.GetIndices(i);
                writer.Write(indices.Length);
                foreach (var index in indices) writer.Write(index);
            }
        }
        Debug.Log($"SG_FBX_EXPORTED {name} vertices={mesh.vertexCount} submeshes={mesh.subMeshCount} path={path}");
    }
}

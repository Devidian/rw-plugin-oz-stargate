using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

// Unreduced render sources are outside the bundle folder; generated mesh assets are inside it.
public static class StargatePlatformPrefabBuilder {
    private const string Folder = "Assets/AssetBundles/StargatePlatforms";
    private const string SourceFolder = "Assets/StargateSource/Platforms";
    private const string TexturePath = Folder + "/PlatformStone.png";
    private static readonly string[] Types = {
        "circular", "curved_milky_way", "curved_pegasus", "grace_of_god"
    };
    private static readonly string[] FbxFiles = {
        "Circular_Platform.fbx", "CurvedBase_MilkyWay.fbx",
        "CurvedBase_Pegasus.fbx", "Grace_of_God_Platform.fbx"
    };
    private static readonly int[] Lod0Triangles = { 14435, 244058, 242670, 47570 };

    public static void Build() {
        if (Application.unityVersion != "6000.3.22f1")
            throw new InvalidOperationException("Platform bundle requires Unity 6000.3.22f1");
        var shader = Shader.Find("HDRP/Lit");
        if (shader == null) throw new InvalidOperationException("HDRP/Lit unavailable");
        var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(TexturePath);
        if (texture == null) throw new InvalidOperationException("Missing platform stone texture " + TexturePath);
        var material = AssetDatabase.LoadAssetAtPath<Material>(Folder + "/PlatformStone.mat");
        if (material == null) {
            material = new Material(shader);
            AssetDatabase.CreateAsset(material, Folder + "/PlatformStone.mat");
        }
        material.shader = shader;
        material.SetColor("_BaseColor", new Color(.93f, .95f, .98f));
        material.SetTexture("_BaseColorMap", texture);
        for (var i = 0; i < Types.Length; i++) BuildOne(Types[i], FbxFiles[i], Lod0Triangles[i], material);
        AssetDatabase.SaveAssets();
        Debug.Log("SG_PLATFORM_PREFABS_READY variants=" + Types.Length);
    }

    private static void BuildOne(string type, string fbxFile, int lod0Triangles, Material material) {
        var root = new GameObject("StargatePlatform" + type.Replace("_", ""));
        try {
            var lods = new LOD[2];
            for (var i = 0; i < 2; i++) {
                // Unity's circular FBX import loses one 12-triangle stone. Its
                // complete source OBJ retains every face without decimation.
                var source = i == 0 ? ImportedMesh(type == "circular"
                        ? SourceFolder + "/Derived/circular_lod0.obj" : SourceFolder + "/" + fbxFile)
                    : ImportedMesh(SourceFolder + "/Derived/" + type + "_lod1.obj");
                var mesh = TexturedMesh(source, type + "_lod" + i);
                int triangles = mesh.triangles.Length / 3;
                if (i == 0 && triangles != lod0Triangles)
                    throw new InvalidOperationException("Changed LOD0 geometry: " + type + " " + triangles);
                var holder = Child(root, "LOD" + i);
                var body = Child(holder, "Body");
                var renderers = new List<Renderer>();
                for (var submesh = 0; submesh < mesh.subMeshCount; submesh++) {
                    var part = Child(body, "Surface_" + submesh);
                    part.AddComponent<MeshFilter>().sharedMesh = SubmeshAsset(mesh, type + "_lod" + i, submesh);
                    var renderer = part.AddComponent<MeshRenderer>();
                    renderer.sharedMaterial = material;
                    renderers.Add(renderer);
                }
                lods[i] = new LOD(i == 0 ? .2f : .005f, renderers.ToArray());
                Debug.Log("SG_PLATFORM_LOD " + type + " lod=" + i + " triangles=" + triangles
                    + " material_slots=" + renderers.Count);
                UnityEngine.Object.DestroyImmediate(mesh);
            }
            var group = root.AddComponent<LODGroup>();
            group.SetLODs(lods);
            group.fadeMode = LODFadeMode.None;
            group.RecalculateBounds();
            if (type == "grace_of_god") AddGraceLights(root);
            var collision = Child(root, "Collision");
            var collider = Child(collision, "Platform").AddComponent<MeshCollider>();
            collider.sharedMesh = ImportedMesh(Folder + "/" + type + "_collision.obj");
            collider.convex = false;
            var path = Folder + "/StargatePlatform" + type.Replace("_", "") + ".prefab";
            if (PrefabUtility.SaveAsPrefabAsset(root, path) == null)
                throw new InvalidOperationException("Cannot save " + path);
            Debug.Log("SG_PLATFORM_PREFAB_READY " + type + " triangles=" + lod0Triangles);
        } finally { UnityEngine.Object.DestroyImmediate(root); }
    }

    private static void AddGraceLights(GameObject root) {
        // Centers of the four Bollard_Light rings in the original OBJ mesh.
        var holder = Child(root, "Lights");
        foreach (var x in new[] { -2.5f, 2.5f })
            foreach (var z in new[] { -1.6f, 1.6f }) {
                var node = Child(holder, "Bollard_" + (x < 0 ? "L" : "R") + (z < 0 ? "B" : "F"));
                node.transform.localPosition = new Vector3(x, 1.14f, z);
                var light = node.AddComponent<Light>();
                light.type = LightType.Point;
                light.color = new Color(.70f, .58f, .32f);
                light.intensity = .05f;
                light.range = 1.1f;
                light.shadows = LightShadows.None;
            }
    }

    private static Mesh ImportedMesh(string path) {
        if (!File.Exists(path)) throw new InvalidOperationException("Missing platform input " + path);
        var imported = AssetDatabase.LoadAssetAtPath<GameObject>(path);
        var filter = imported == null ? null : imported.GetComponentInChildren<MeshFilter>();
        if (filter == null || filter.sharedMesh == null)
            throw new InvalidOperationException("Cannot import platform mesh " + path);
        return filter.sharedMesh;
    }

    private static Mesh TexturedMesh(Mesh source, string name) {
        AssetDatabase.DeleteAsset(Folder + "/" + name + "_textured.asset");
        var mesh = new Mesh { name = name + "_textured", indexFormat = source.indexFormat };
        var vertices = source.vertices;
        var normals = source.normals;
        mesh.vertices = vertices;
        if (normals.Length == vertices.Length) mesh.normals = normals;
        var uv = new Vector2[vertices.Length];
        for (var i = 0; i < vertices.Length; i++) {
            var v = vertices[i];
            var n = normals.Length == vertices.Length ? normals[i] : Vector3.up;
            var ax = Mathf.Abs(n.x); var ay = Mathf.Abs(n.y); var az = Mathf.Abs(n.z);
            // One stone tile spans four source metres. This also supplies UVs to
            // the two curved FBX meshes, which contain no authored UV channel.
            uv[i] = ay >= ax && ay >= az ? new Vector2(v.x, v.z) * .25f
                : ax >= az ? new Vector2(v.z, v.y) * .25f
                : new Vector2(v.x, v.y) * .25f;
        }
        mesh.uv = uv;
        mesh.subMeshCount = source.subMeshCount;
        for (var i = 0; i < source.subMeshCount; i++)
            mesh.SetTriangles(source.GetTriangles(i), i);
        if (normals.Length != vertices.Length) mesh.RecalculateNormals();
        mesh.RecalculateBounds();
        if (mesh.triangles.Length != source.triangles.Length)
            throw new InvalidOperationException("LOD geometry changed: " + name);
        return mesh;
    }

    private static Mesh SubmeshAsset(Mesh source, string name, int submesh) {
        var path = Folder + "/" + name + "_surface_" + submesh + ".asset";
        AssetDatabase.DeleteAsset(path);
        var sourceVertices = source.vertices;
        var sourceNormals = source.normals;
        var sourceUv = source.uv;
        var vertices = new List<Vector3>();
        var normals = new List<Vector3>();
        var uv = new List<Vector2>();
        var remap = new Dictionary<int, int>();
        var triangles = source.GetTriangles(submesh);
        for (var i = 0; i < triangles.Length; i++) {
            int original = triangles[i];
            if (!remap.TryGetValue(original, out int mapped)) {
                mapped = vertices.Count;
                remap.Add(original, mapped);
                vertices.Add(sourceVertices[original]);
                normals.Add(sourceNormals[original]);
                uv.Add(sourceUv[original]);
            }
            triangles[i] = mapped;
        }
        var mesh = new Mesh { name = name + "_surface_" + submesh,
            indexFormat = vertices.Count > 65535 ? UnityEngine.Rendering.IndexFormat.UInt32
                : UnityEngine.Rendering.IndexFormat.UInt16 };
        mesh.SetVertices(vertices);
        mesh.SetNormals(normals);
        mesh.SetUVs(0, uv);
        mesh.SetTriangles(triangles, 0);
        mesh.RecalculateBounds();
        AssetDatabase.CreateAsset(mesh, path);
        return mesh;
    }

    private static GameObject Child(GameObject parent, string name) {
        var child = new GameObject(name);
        child.transform.SetParent(parent.transform, false);
        return child;
    }
}

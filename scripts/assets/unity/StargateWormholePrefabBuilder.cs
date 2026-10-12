using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

// Offline conversion of the accepted 12 fps atlas animation into bundle meshes.
public static class StargateWormholePrefabBuilder {
    private const string Folder = "Assets/AssetBundles/StargateWormhole";
    private const int Frames = 140, Columns = 7, Rows = 10, Tile = 384;
    private static readonly Dictionary<string, Material> Materials = new Dictionary<string, Material>();

    public static void Build() { BuildWithSurge("articulated-wormhole-surge.obj"); }

    // Development A/B candidate from build-lightwave-kawoosh.py.
    public static void BuildLightwaveComparison() {
        BuildWithSurge("wormhole-surge-lightwave.obj");
    }

    private static void BuildWithSurge(string surgeFile) {
        if (Application.unityVersion != "6000.3.22f1")
            throw new InvalidOperationException("Wormhole bundle requires Unity 6000.3.22f1");
        foreach (var name in new[] { "puddle-loop-0.png", "puddle-loop-1.png",
                "puddle-surge.png", "puddle-dissolve.png", surgeFile,
                "articulated-wormhole-rear-vortex.obj" })
            if (!File.Exists(Folder + "/" + name))
                throw new InvalidOperationException("Missing wormhole input " + name);
        var root = new GameObject("StargateWormhole");
        try {
            CreateMaterials();
            var loop = Child(root, "Loop");
            var front = Child(loop, "Front");
            var rear = Child(loop, "Rear");
            for (var i = 0; i < Frames; i++) {
                var mesh = SaveMesh(FrameMesh(i, false), "Loop_" + i.ToString("000"));
                var material = Materials["Loop" + (i / 70)];
                MeshChild(front, "Frame_" + i.ToString("000"), mesh, material)
                    .SetActive(false);
                var other = MeshChild(rear, "Frame_" + i.ToString("000"), mesh, material);
                other.transform.localRotation = new Quaternion(0f, 1f, 0f, 0f);
                other.SetActive(false);
            }
            var dissolve = Child(root, "Dissolve");
            for (var i = 0; i < 12; i++) {
                var mesh = SaveMesh(FrameMesh(i, true), "Dissolve_" + i.ToString("00"));
                MeshChild(dissolve, "Frame_" + i.ToString("00"), mesh,
                    Materials["Dissolve"]).SetActive(false);
            }
            ImportedChild(root, "Surge", surgeFile,
                Materials["Surge"], -.03f).SetActive(false);
            ImportedChild(root, "RearVortex", "articulated-wormhole-rear-vortex.obj",
                Materials["Surge"], .03f).SetActive(false);
            var lights = Child(root, "Lights");
            Light(lights, "Aperture", new Vector3(0f, 0f, .35f),
                LightType.Point, 40f);
            var broad = Light(lights, "Broad", new Vector3(0f, 0f, -2.3f),
                LightType.Spot, 1000f);
            broad.spotAngle = 135f;
            broad.innerSpotAngle = 120f;
            broad.transform.localRotation = Quaternion.LookRotation(Vector3.forward);
            var prefab = PrefabUtility.SaveAsPrefabAsset(root, Folder + "/StargateWormhole.prefab");
            if (prefab == null) throw new InvalidOperationException("Could not save wormhole prefab");
            AssetDatabase.SaveAssets();
            Debug.Log("SG_WORMHOLE_PREFAB_READY loop=140 dissolve=12 surge=2 lights=2 source=" + surgeFile);
        } finally { UnityEngine.Object.DestroyImmediate(root); }
    }

    private static void CreateMaterials() {
        var shader = Shader.Find("HDRP/Lit");
        if (shader == null) throw new InvalidOperationException("HDRP/Lit unavailable");
        for (var i = 0; i < 2; i++)
            MaterialFor("Loop" + i, "puddle-loop-" + i + ".png", shader);
        MaterialFor("Surge", "puddle-surge.png", shader);
        MaterialFor("Dissolve", "puddle-dissolve.png", shader);
    }

    private static void MaterialFor(string name, string textureName, Shader shader) {
        var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(Folder + "/" + textureName);
        if (texture == null) throw new InvalidOperationException("Missing texture " + textureName);
        var path = Folder + "/Mat" + name + ".mat";
        var material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (material == null) {
            material = new Material(shader);
            AssetDatabase.CreateAsset(material, path);
        }
        material.shader = shader;
        material.SetTexture("_BaseColorMap", texture);
        material.SetColor("_BaseColor", Color.white);
        Materials[name] = material;
    }

    private static Mesh FrameMesh(int frame, bool closing) {
        var vertices = new Vector3[97];
        var normals = new Vector3[97];
        var uvs = new Vector2[97];
        var indices = new int[288];
        for (var i = 0; i < vertices.Length; i++) {
            var angle = (i - 1) * Mathf.PI * 2f / 96f;
            var x = i == 0 ? 0f : Mathf.Cos(angle);
            var y = i == 0 ? 0f : Mathf.Sin(angle);
            vertices[i] = new Vector3(x * 2.31f, y * 2.31f, 0f);
            normals[i] = Vector3.back;
            if (closing) {
                uvs[i] = new Vector2(((frame % 4) * 384f + .5f +
                    (x + 1f) * .5f * 383f) / 1536f,
                    1f - ((frame / 4) * 384f + .5f + (1f - y) * .5f * 383f) / 1152f);
            } else {
                var row = (frame % 70) / Columns;
                uvs[i] = new Vector2(((frame % Columns) * Tile + .5f +
                    (x + 1f) * .5f * (Tile - 1)) / (Columns * Tile),
                    1f - (row * Tile + .5f + (1f - y) * .5f * (Tile - 1)) / (Rows * Tile));
            }
        }
        for (var i = 0; i < 96; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = 1 + (i + 1) % 96;
            indices[i * 3 + 2] = 1 + i;
        }
        var mesh = new Mesh { vertices = vertices, normals = normals, uv = uvs,
            triangles = indices };
        mesh.RecalculateBounds();
        return mesh;
    }

    private static Mesh SaveMesh(Mesh mesh, string name) {
        var path = Folder + "/" + name + ".asset";
        var existing = AssetDatabase.LoadAssetAtPath<Mesh>(path);
        if (existing == null) {
            AssetDatabase.CreateAsset(mesh, path);
            return mesh;
        } else {
            EditorUtility.CopySerialized(mesh, existing);
            UnityEngine.Object.DestroyImmediate(mesh);
            return existing;
        }
    }

    private static GameObject ImportedChild(GameObject parent, string name,
            string file, Material material, float z) {
        var source = AssetDatabase.LoadAssetAtPath<GameObject>(Folder + "/" + file);
        var filter = source == null ? null : source.GetComponentInChildren<MeshFilter>();
        if (filter == null || filter.sharedMesh == null)
            throw new InvalidOperationException("Missing OBJ mesh " + file);
        if (file == "wormhole-surge-lightwave.obj" &&
                filter.sharedMesh.triangles.Length / 3 != 4032)
            throw new InvalidOperationException("LightWave Kawoosh triangle count changed");
        var child = MeshChild(parent, name, filter.sharedMesh, material);
        child.transform.localPosition = new Vector3(0f, 0f, z);
        return child;
    }

    private static GameObject MeshChild(GameObject parent, string name,
            Mesh mesh, Material material) {
        var child = Child(parent, name);
        child.AddComponent<MeshFilter>().sharedMesh = mesh;
        child.AddComponent<MeshRenderer>().sharedMaterial = material;
        return child;
    }

    private static Light Light(GameObject parent, string name, Vector3 position,
            LightType type, float intensity) {
        var child = Child(parent, name);
        child.transform.localPosition = position;
        var light = child.AddComponent<Light>();
        light.type = type;
        light.intensity = intensity;
        light.color = new Color(.72f, .85f, 1f);
        light.range = 12f;
        light.shadows = LightShadows.None;
        return light;
    }

    private static GameObject Child(GameObject parent, string name) {
        var child = new GameObject(name);
        child.transform.SetParent(parent.transform, false);
        return child;
    }
}

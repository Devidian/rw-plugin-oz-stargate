"""Triangulate author OBJ polygons before MeshLab; preserve concave ring openings."""
from array import array
import numpy as np
import mapbox_earcut

def read_obj(stream):
    vertices=array('d');triangles=array('i');quads=array('i');polygons=0
    for line in stream:
        if line.startswith(b'v '): vertices.extend(map(float,line.split()[1:4]))
        elif line.startswith(b'f '):
            ids=[int(t.split(b'/')[0]) for t in line.split()[1:]]
            ids=[i-1 if i>0 else len(vertices)//3+i for i in ids]
            if len(ids)<3: continue
            polygons+=1
            if len(ids)==3: triangles.extend(ids)
            elif len(ids)==4: quads.extend(ids)
            else:
                points=np.array([vertices[3*i:3*i+3] for i in ids])
                shifted=points-points[0]
                normal=np.cross(shifted,np.roll(shifted,-1,axis=0)).sum(axis=0)
                if np.linalg.norm(normal)<1e-14: continue
                projected=np.delete(points,int(np.argmax(np.abs(normal))),axis=1)
                local=mapbox_earcut.triangulate_float64(projected,np.array([len(ids)],dtype=np.uint32)).reshape(-1,3)
                t=np.asarray(ids)[local];xyz=points[local]
                reversed_faces=np.einsum('ij,j->i',np.cross(xyz[:,1]-xyz[:,0],xyz[:,2]-xyz[:,0]),normal)<0
                t[reversed_faces]=t[reversed_faces][:,[0,2,1]]
                triangles.extend(t.ravel().tolist())
    points=np.asarray(vertices).reshape(-1,3)
    faces=np.asarray(triangles).reshape(-1,3)
    q=np.asarray(quads).reshape(-1,4)
    if len(q):
        v=points[q]
        a=np.cross(v[:,1]-v[:,0],v[:,2]-v[:,0]);b=np.cross(v[:,2]-v[:,0],v[:,3]-v[:,0])
        alternate=np.einsum('ij,ij->i',a,b)<0
        t1=q[:,[0,1,2]].copy();t2=q[:,[0,2,3]].copy()
        t1[alternate]=q[alternate][:,[1,2,3]];t2[alternate]=q[alternate][:,[1,3,0]]
        faces=np.concatenate([faces,t1,t2])
    xyz=points[faces]
    faces=faces[np.linalg.norm(np.cross(xyz[:,1]-xyz[:,0],xyz[:,2]-xyz[:,0]),axis=1)>1e-12]
    return points,faces,polygons

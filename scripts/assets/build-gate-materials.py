#!/usr/bin/env python3
"""Add box-projected UVs without changing accepted geometry; extract original JPG unchanged."""
import argparse,hashlib,json,math
from pathlib import Path
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parents[2]/'src/assets/models/milkyway'
PARTS={'milkyway-preview.obj':3.075,'articulated-body.obj':3.075,
       'articulated-ring.obj':0,'articulated-chevron-fixed-frame.obj':0,
       'articulated-chevron-v-frame.obj':0}
parser=argparse.ArgumentParser()
parser.add_argument('reference',type=Path)
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
report={'projection':'dominant face axis, 3.6 source metres per 4m authored reference tile',
        'archiveSha256':hashlib.sha256(args.reference.read_bytes()).hexdigest(),'files':{}}
def save(name,data):
 p=ROOT/name
 if args.check:assert p.read_bytes()==data,name
 else:p.write_bytes(data)
 report['files'][name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
with ZipFile(args.reference) as archive:
 save('gate-original-color.jpg',archive.read('SG_MW_Texture_Reference/SG_Main_4mx4m_Color_and_Metal.jpg'))
for name,offset in PARTS.items():
 source=(ROOT/name).read_text();verts=[];lines=[];faces=[];uvs=[];uvmap={}
 for line in source.splitlines():
  if line.startswith('v '):verts.append(tuple(map(float,line.split()[1:4])));lines.append(line)
  elif line.startswith('f '):faces.append([int(n.split('/')[0]) for n in line.split()[1:]])
  elif not line.startswith(('vt ','vn ','usemtl ','mtllib ')):lines.append(line)
 outputfaces=[]
 for ids in faces:
  assert len(ids)==3
  a,b,c=[verts[i-1] for i in ids];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
  normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
  axis=max(range(3),key=lambda i:abs(normal[i]));sign=1 if normal[axis]>=0 else -1
  indices=[]
  for index in ids:
   key=(index,axis,sign)
   if key not in uvmap:
    x,y,z=verts[index-1];y-=offset
    pair=((z*sign,y) if axis==0 else (x,z*sign) if axis==1 else (x*sign,y))
    uv=tuple(.5+n/3.6 for n in pair)
    assert all(math.isfinite(n) for n in uv)
    uvs.append(uv);uvmap[key]=len(uvs)
   indices.append(f'{index}/{uvmap[key]}')
  outputfaces.append('f '+' '.join(indices))
 data=('\n'.join(lines+['vt %.7f %.7f'%p for p in uvs]+outputfaces)+'\n').encode()
 out='textured-'+name
 save(out,data)
 # Geometry/polygon order must be identical; only UV indices have been added.
 generated=data.decode().splitlines()
 assert [l for l in generated if l.startswith('v ')]==[l for l in source.splitlines() if l.startswith('v ')]
 assert [[int(n.split('/')[0]) for n in l.split()[1:]] for l in generated if l.startswith('f ')]==faces
 report['files'][out].update(vertices=len(verts),triangles=len(faces),uvs=len(uvs),sourceSha256=hashlib.sha256(source.encode()).hexdigest())
text=json.dumps(report,indent=2)+'\n';p=ROOT/'gate-material-report.json'
if args.check:assert p.read_text()==text
else:p.write_text(text)
print(text)

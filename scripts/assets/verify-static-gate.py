#!/usr/bin/env python3
"""Check generated preview independently of its mesh tooling (stdlib only)."""
import hashlib
import json
from pathlib import Path
import math
import sys

root=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[2]/'src/assets/models/milkyway'
report=json.loads((root/'build-report.json').read_text())
vertices=[];faces=0;objects=0
triangles=[]
path=root/'milkyway-preview.obj'
with path.open('rb') as data:
    assert hashlib.file_digest(data,'sha256').hexdigest()==report['obj_sha256']
for line in path.read_text().splitlines():
    if line.startswith('v '):
        p=tuple(map(float,line.split()[1:]));assert len(p)==3 and all(math.isfinite(x) for x in p);vertices.append(p)
    elif line.startswith('o '): objects+=1
    elif line.startswith('f '):
        indices=[int(x)-1 for x in line.split()[1:]]
        assert len(indices)==3 and len(set(indices))==3 and min(indices)>=0 and max(indices)<len(vertices)
        a,b,c=[vertices[i] for i in indices]
        u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        cross=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        assert sum(x*x for x in cross)>1e-24,'Degenerate face after OBJ rounding'
        triangles.append((a,b,c))
        faces+=1
assert objects==20 # body + ring + nine lower and nine upper chevrons
assert faces==report['triangles'] and 0<faces<=250000
assert path.stat().st_size==report['bytes'] and path.stat().st_size<=10*1024*1024
lo=[min(p[i] for p in vertices) for i in range(3)];hi=[max(p[i] for p in vertices) for i in range(3)]
assert abs(lo[1])<1e-5 and 6<hi[1]<6.3 and 6<hi[0]-lo[0]<6.3 and hi[2]-lo[2]<0.6
# The large author polygons must not be triangulated across the open passage.
centre=(hi[1]+lo[1])/2
samples=[(0,centre)]+[(1.8*math.cos(i*math.pi/4),centre+1.8*math.sin(i*math.pi/4)) for i in range(8)]
for a,b,c in triangles:
    area=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    if abs(area)<1e-12: continue
    for x,y in samples:
        if not (min(a[0],b[0],c[0])<=x<=max(a[0],b[0],c[0]) and min(a[1],b[1],c[1])<=y<=max(a[1],b[1],c[1])): continue
        sides=[(v[0]-u[0])*(y-u[1])-(v[1]-u[1])*(x-u[0]) for u,v in [(a,b),(b,c),(c,a)]]
        assert not (min(sides)>1e-10 or max(sides)<-1e-10), 'Triangle spans the gate opening'
assert 'David Gian-Cursio' in (root/'SOURCE-NOTICE.txt').read_text(errors='replace')
assert 'CC0' in (root/'SOURCE-NOTICE.txt').read_text(errors='replace')
print('Static gate verified:',faces,'triangles,',len(vertices),'vertices,',path.stat().st_size,'bytes; feet origin and bounds valid')

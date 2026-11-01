"""Convert the user's chair cuboids to an exact Forge OBJ; never execute archive scripts."""
from pathlib import Path
import json,math,shutil
ROOT=Path(__file__).resolve().parents[1]
parts=json.loads((ROOT/'tools/model-assets/chair_geometry_source.json').read_text(encoding='utf-8'))['objects']
colors=['case','case_light','edge','dark','black','metal','cyan','cyan_dark','white','amber','rubber','screw','screen_dark','blue','teal','mint']
assets=ROOT/'src/main/resources/assets/browserlink'
obj=['mtllib computer_chair.mtl','o GamingChair','usemtl atlas']
for i in range(16):obj.append(f'vt {((i%4)*32+16)/128:.6f} {((i//4)*32+16)/128:.6f}')
faces=[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]
corners=[(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]
collisions=[];bounds=[]
for n,p in enumerate(parts):
 angle=math.radians(p['rotation_z_degrees']);cs=math.cos(angle);sn=math.sin(angle);cx,cy,cz=p['center_grid'];sx,sy,sz=p['size_grid'];vs=[]
 obj.append(f'g part_{n:03d}')
 for a,b,c in corners:
  x=a*sx/2;y=b*sy/2;z=cz+c*sz/2
  v=(.5+(cx+x*cs-y*sn)/32,z/32,.5-(cy+x*sn+y*cs)/32);vs.append(v);bounds.append(v)
  obj.append('v '+' '.join(f'{t:.8f}' for t in v))
 uv=colors.index(p['palette'])+1
 for face in faces:obj.append('f '+' '.join(f'{n*8+k+1}/{uv}' for k in face))
 if p['name'].startswith(('B00 ','L01 ','S02 ','S03 ','S04 ','R01 ','R08 ','R10 ','R11 ')) or '05 padded top' in p['name'] or '02 upright' in p['name']:
  collisions.append([min(v[i] for v in vs)*16 for i in range(3)]+[max(v[i] for v in vs)*16 for i in range(3)])
(assets/'models/block/computer_chair.obj').write_text('\n'.join(obj)+'\n')
(assets/'models/block/computer_chair.mtl').write_text('newmtl atlas\nKd 1 1 1\nmap_Kd browserlink:block/chair_atlas\n')
model={'loader':'forge:obj','model':'browserlink:models/block/computer_chair.obj','automatic_culling':False,'shade_quads':True,'flip_v':False,'emissive_ambient':False,'ambientocclusion':False,'textures':{'particle':'browserlink:block/chair_atlas'}}
(assets/'models/block/computer_chair.json').write_text(json.dumps(model,indent=2))
(assets/'models/item/computer_chair.json').write_text(json.dumps({'parent':'browserlink:block/computer_chair','display':{'gui':{'rotation':[20,210,0],'translation':[0,-1,0],'scale':[.55,.55,.55]},'ground':{'translation':[0,1,0],'scale':[.4,.4,.4]},'fixed':{'scale':[.6,.6,.6]}}},indent=2))
shutil.copy2(ROOT/'tools/model-assets/chair_atlas.png',assets/'textures/block/chair_atlas.png')
source=(ROOT/'src/main/java/dev/browserlink/ComputerGeometry.java').read_text().replace('ComputerGeometry','ChairGeometry').replace('tools/convert_computer.py','tools/convert_chair.py')
start=source.index('    public static final float[] SCREEN');end=source.index('    private static final VoxelShape[] SHAPES')
boxlines=',\n        '.join('{'+', '.join(f'{v:.8f}' for v in b)+'}' for b in collisions)
source=source[:start]+'    public static final double SEAT_HEIGHT=0.521875;\n    private static final double[][] BOXES={\n        '+boxlines+'\n    };\n'+source[end:]
(ROOT/'src/main/java/dev/browserlink/ChairGeometry.java').write_text(source)
assert len(parts)==169
print('Chair:',len(parts),'parts;',len(parts)*6,'quads; height',max(v[1] for v in bounds),'seat',.521875)

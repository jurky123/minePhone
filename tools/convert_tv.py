"""Read user geometry only; do not execute scripts supplied in the archive."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1];assets=ROOT/'src/main/resources/assets/browserlink'
parts=json.loads((ROOT/'tools/model-assets/tv_geometry_source.json').read_text())['objects']
colors=['case','case_light','edge','dark','black','metal','cyan','cyan_dark','white','amber','rubber','screw','screen_dark','blue','teal','mint']
scale=3/128.24
obj=['mtllib television.mtl','o Television','usemtl atlas']
for i in range(16):obj.append(f'vt {((i%4)*32+16)/128:.6f} {((i//4)*32+16)/128:.6f}')
faces=[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]
corners=[(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]
for n,p in enumerate(parts):
 cx,cy,cz=p['center_grid'];sx,sy,sz=p['size_grid'];obj.append(f'g part_{n:03d}')
 for a,b,c in corners:obj.append('v '+' '.join(f'{v:.9f}' for v in (.5+(cx+a*sx/2)*scale,(cz+c*sz/2)*scale,.5-(cy+b*sy/2)*scale)))
 uv=colors.index(p['palette'])+1
 for face in faces:obj.append('f '+' '.join(f'{n*8+k+1}/{uv}' for k in face))
screen=[.5-56*scale,14*scale,.5+56*scale,77*scale,.5+3.38*scale]
obj+=['g screen','usemtl screen']
for v in [(screen[0],screen[1],screen[4]),(screen[2],screen[1],screen[4]),(screen[2],screen[3],screen[4]),(screen[0],screen[3],screen[4])]:obj.append('v '+' '.join(map(str,v)))
obj+=['vt 0 1','vt 1 1','vt 1 0','vt 0 0','f '+' '.join(f'{len(parts)*8+i+1}/{17+i}' for i in range(4))]
(assets/'models/block/television.obj').write_text('\n'.join(obj)+'\n')
(assets/'models/block/television.mtl').write_text('newmtl atlas\nKd 1 1 1\nmap_Kd browserlink:block/tv_body_atlas\nnewmtl screen\nKd 1 1 1\nmap_Kd browserlink:block/tv_screen\n')
model={'loader':'forge:obj','model':'browserlink:models/block/television.obj','automatic_culling':False,'shade_quads':True,'flip_v':False,'ambientocclusion':False,'textures':{'particle':'browserlink:block/tv_body_atlas'}}
def write(path,data): (assets/path).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
write('models/block/television.json',model);write('models/block/television_part.json',{'textures':{'particle':'browserlink:block/tv_body_atlas'},'elements':[]})
write('models/item/television.json',{'parent':'browserlink:block/television','display':{'gui':{'rotation':[20,210,0],'translation':[0,-2,0],'scale':[.3,.3,.3]},'ground':{'translation':[0,2,0],'scale':[.25,.25,.25]},'fixed':{'scale':[.3,.3,.3]},'firstperson_righthand':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.18,.18,.18]},'firstperson_lefthand':{'rotation':[0,180,0],'scale':[.18,.18,.18]},'thirdperson_righthand':{'rotation':[0,180,0],'scale':[.2,.2,.2]},'thirdperson_lefthand':{'rotation':[0,180,0],'scale':[.2,.2,.2]}}})
write('blockstates/television.json',{'variants':{f'facing={d},part={p}':{'model':'browserlink:block/'+('television' if p==1 else 'television_part'),'y':rot} for d,rot in [('south',0),('west',90),('north',180),('east',270)] for p in range(6)}})
for name in ['tv_body_atlas','tv_screen']:shutil.copy2(ROOT/f'tools/model-assets/{name}.png',assets/f'textures/block/{name}.png')
nums=','.join(f'{v:.9f}f' for v in screen)
source='''package dev.browserlink;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
public final class TvGeometry {
    public static final float[] SCREEN={NUMS};
    private static final VoxelShape[][] SHAPES=new VoxelShape[4][6];
    static {
        for(Direction d:Direction.Plane.HORIZONTAL)for(int p=0;p<6;p++){
            // A thin 3 x 2 furniture footprint. Every occupied cell is clickable.
            VoxelShape full=(d.getAxis()==Direction.Axis.Z)?Shapes.box(-1,0,.17,2,1.942,.83):Shapes.box(.17,0,-1,.83,1.942,2);
            var side=d.getCounterClockWise();int offset=p%3-1;
            SHAPES[d.get2DDataValue()][p]=Shapes.join(full.move(-side.getStepX()*offset,-p/3,-side.getStepZ()*offset),Shapes.block(),BooleanOp.AND);
        }
    }
    public static VoxelShape shape(Direction d,int part){return SHAPES[d.get2DDataValue()][part];}
}
'''.replace('NUMS',nums)
(ROOT/'src/main/java/dev/browserlink/TvGeometry.java').write_text(source)
assert len(parts)==152
assert abs((screen[2]-screen[0])/(screen[3]-screen[1])-16/9)<1e-8
print('TV:',len(parts),'body parts + screen; width=3 blocks; height=',83*scale,'screen=',screen)

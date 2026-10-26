"""Build an optional TACZ-format tool demonstration pack from vanilla sprite silhouettes.
Usage: python tools/generate_tool_demo.py <Minecraft client jar>
Textures are referenced from minecraft:, not copied or reconfigured.
"""
import io, json, re, sys, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'examples' / 'tacz_vanilla_tools'
ASSETS = ROOT / 'assets' / 'tacz_tools'
def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def motion(length, rotations, positions=None):
    bone = {'rotation': rotations}
    if positions is not None: bone['position'] = positions
    return {'animation_length': length, 'bones': {'root': bone}}

animations = {
    'static_idle': {'loop': True, 'bones': {}},
    'draw': motion(.3, {'0.0': [-45,0,-25], '0.3': [0,0,0]}, {'0.0':[0,-12,0], '0.3':[0,0,0]}),
    'put_away': motion(.25, {'0.0':[0,0,0], '0.25':[-45,0,-25]}, {'0.0':[0,0,0], '0.25':[0,-12,0]}),
    'swing': motion(.4, {'0.0':[0,0,0], '0.1':[15,-20,-35], '0.22':[-60,25,45], '0.4':[0,0,0]}),
    'dig': motion(.3, {'0.0':[0,0,0], '0.08':[30,0,-12], '0.17':[-65,0,15], '0.3':[0,0,0]}),
    'use': motion(.35, {'0.0':[0,0,0], '0.13':[-50,-15,15], '0.35':[0,0,0]}),
    'inspect': motion(1.2, {'0.0':[0,0,0], '0.3':[0,50,-15], '0.8':[0,-40,15], '1.2':[0,0,0]})
}
write(ROOT/'pack.mcmeta', {'pack':{'description':'TACZ client: vanilla tool animation examples', 'pack_format':88,'min_format':88,'max_format':999}})
write(ASSETS/'animations/tools.animation.json', {'format_version':'1.8.0','animations':animations})
items = {}
with zipfile.ZipFile(sys.argv[1]) as jar:
    for name in sorted(jar.namelist()):
        match = re.fullmatch(r'assets/minecraft/textures/item/((?:wooden|stone|copper|iron|golden|diamond|netherite)_(?:sword|axe|pickaxe|shovel|hoe))\.png', name)
        if not match: continue
        item = match.group(1)
        sprite = Image.open(io.BytesIO(jar.read(name))).convert('RGBA')
        width, height = sprite.size
        cubes = []
        # Extruded pixels preserve each tool's familiar outline. Faces retain vanilla UVs.
        for y in range(height):
            for x in range(width):
                if sprite.getpixel((x,y))[3] < 128: continue
                cubes.append({'origin':[-6+(7-x)*.7, -3+(7-y)*.7, -10.35], 'size':[.7,.7,.7],
                              'uv':{face:{'uv':[x,y],'uv_size':[1,1]} for face in ['north','south','east','west','up','down']}})
        geo = {'format_version':'1.12.0', 'minecraft:geometry':[{
            'description':{'identifier':'geometry.'+item,'texture_width':width,'texture_height':height,
                           'visible_bounds_width':4,'visible_bounds_height':4,'visible_bounds_offset':[0,0,0]},
            'bones':[{'name':'root','pivot':[-3,-7,-10], 'cubes':cubes},
                     {'name':'idle_view','pivot':[0,0,0]}]}]}
        write(ASSETS/f'geo_models/tools/{item}.json', geo)
        write(ASSETS/f'display/guns/{item}_display.json', {'model':f'tacz_tools:tools/{item}',
              'texture':f'minecraft:item/{item}','animation':'tacz_tools:tools'})
        items['minecraft:'+item] = {'enabled':False, 'display':f'tacz_tools:{item}_display', 'actions':{
              'idle':'static_idle','draw':'draw','put_away':'put_away','attack':'swing','mine':'dig',
              'use':'use','inspect':'inspect','reload':''}}
write(ASSETS/'config/client_item_overrides.json', {'items':items})
(ROOT/'README.txt').write_text('默认禁用：在映射中将需要的 enabled 改为 true，再安装并启用此资源包。\n示例包：35 种原版剑、斧、镐、铲、锄。仅演示同一套 TACZ 资产格式和物品动作映射。\n'
    '模型由原版物品像素轮廓挤出，保留 minecraft 贴图引用，示例不含手臂定位组。\n'
    '左键挥动/攻击、挖掘、有效右键使用、I 检视、切换时掏出/收起；R 不启用。\n'
    '动画师可以用自己的模型和动画替换这些示例文件，无须改动格式。\n',encoding='utf-8')
print(f'Generated {len(items)} mapped tools: {ROOT}')

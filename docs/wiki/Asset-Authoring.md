# 制作模型与动画

新物品资源包的最小目录如下；TACZ 已有资源保持原目录、字段和动画名称。

```text
my-pack/
  pack.mcmeta
  assets/my_pack/
    config/client_item_overrides.json
    display/guns/sword_display.json
    geo_models/tools/sword.json
    animations/sword.animation.json
    textures/tools/sword.png
```

## Display

`assets/my_pack/display/guns/sword_display.json`：

```json
{
  "model": "my_pack:tools/sword",
  "texture": "my_pack:tools/sword",
  "animation": "my_pack:sword"
}
```

`model` 解析为 `geo_models/<path>.json`；`texture` 解析为 `textures/<path>.png`；`animation` 解析为 `animations/<path>.animation.json`。
**不要在这些字段中重复写目录前缀或扩展名。** 命名空间、路径使用小写。
`display/guns` 的目录名保持兼容，它也可以承载剑、斧、镐等非枪械显示定义。

## 模型

使用 Blockbench 的 Bedrock 模型结构（`minecraft:geometry`），当前读取其中第一项。
模型保持像素单位，16 像素为一个方块；渲染器负责 TACZ 的坐标转换和 Z-Y-X 旋转顺序。
采用每个 cube 的 **per-face UV**，例如：

```json
{"origin":[0,0,0],"size":[2,8,1],"uv":{"north":{"uv":[0,0],"uv_size":[2,8]},"south":{"uv":[2,0],"uv_size":[2,8]}}}
```

未声明的面不会绘制。当前不支持旧版 geometry 格式、box UV 数组、网格或完整镜像语义；不要据此宣称全部 TACZ 模型兼容。

### 定位组

- `idle_view`：非瞄准第一人称的视点位置与朝向。渲染器把它的静态路径变换取逆，使它对齐玩家眼睛。缺失时尝试 `camera`，仍缺失则使用模型原点。
- `lefthand_pos` / `righthand_pos`：以 TACZ 的规则绑定玩家皮肤手臂，占位方块不会用武器贴图绘制。
- 使用普通父子骨骼结构；cube 和 bone pivot 均按 TACZ 的坐标约定处理。
- 默认无配件外观会隐藏扩容弹匣、配件转接口、瞄具支架等对应分支。

## 动画

保留 Bedrock/TACZ 的 `.animation.json`：

```json
{
  "format_version":"1.8.0",
  "animations":{
    "static_idle":{"loop":true,"bones":{"root":{"rotation":[0,0,0]}}},
    "slash":{
      "animation_length":0.4,
      "bones":{"root":{"rotation":{"0.0":[0,0,0],"0.15":[-60,0,30],"0.4":[0,0,0]}}}
    }
  }
}
```

`animation_length` 单位为秒，控制本地动作时长。模型和动画中的骨骼名必须一致。
静态 rotation / position / scale 及数值时间关键帧可用；目前关键帧线性插值，`pre/post` 对象优先取 post。
目前不实现完整 Catmull-Rom、Molang 表达式、声音事件、IK/约束、Lua 或任意动画状态机。
idle 当前保持第 0 秒基础姿势，动作播放一次后回到 idle；`loop` 不提供完整 TACZ 循环语义。

# 动作与可选工具示例

`actions` 的值是原动画文件里的 clip 名称，动画师不必重命名自己的动画。

| 本地事件 | 默认 clip | 触发 |
| --- | --- | --- |
| idle | static_idle | 基础持握姿势 |
| draw | draw | 切入映射物品 |
| put_away | put_away | 切到空手或其他物品 |
| run_start | run_start | 开始疾跑 |
| run | run | 持续疾跑，循环播放 |
| run_hold | run_hold | 疾跑跳跃/腾空时稳定持枪 |
| run_end | run_end | 停止疾跑 |
| attack | shoot | 主手攻击/挥动 |
| mine | 与 attack 相同 | 主手挖掘挥动 |
| use | 无 | 原版持续使用开始，或右键工具产生主手挥动 |
| inspect | inspect | I，可在按键设置中修改 |
| reload | reload_tactical | R，可在按键设置中修改 |

可选动作写空字符串禁用，例如工具 `"reload":""`。缺失 draw/put_away 时仍有简短位移动画兜底；其他缺失动作不播放。
右键空挥不强制触发使用。动作不会增加真实弹药、伤害或改变挖掘判定。

## 基础姿势与后座

原 TACZ 的 `static_idle` 会保留。draw、inspect、reload、工具动作只覆盖自己声明的通道，其余旋转、位移、缩放继承基础姿势。
attack/mine 指向 `shoot` 时按后座叠加处理：旋转和位移相加，缩放相乘。自定义工具动作如 `slash` 按通道覆盖。

切走后旧模型留在本地画面中，播完 put_away，再绘制当前选择的新物品。真实槽位立即变化，动画不会阻止原版交互。
快速切槽只掏出最终选择；收起期间新的检视/攻击输入不会中断旧模型的收起。

## 疾跑与缺省动画（0.1.1 起）

使用玩家实际疾跑状态（例如前进 + Ctrl），不是单纯按住前进键。run_start 播完进入 run 循环，腾空时优先 run_hold，停止时播放 run_end。
run 必须存在才能启用这组状态；起跑、腾空、停跑片段可以省略。`"actions":{"run":""}` 可禁用整个疾跑动画组，也可把四个事件映射到自己的动画名称。
疾跑动画按 TACZ 的移动叠加轨道处理，保留 static_idle 手臂姿势；状态之间使用短过渡。掏出/收起、攻击、挖掘、使用、检视、换弹优先，完成后若仍在疾跑则恢复。
当前按动画时长循环，尚未复刻 TACZ 按步行距离同步的步频，也不执行其移动 Lua 状态机或普通行走/战术冲刺分支。

0.1.2 起，切换物品时会保留当前的移动叠加轨道，让 `put_away` 主轨道与 `run`/`run_hold` 同时过渡；主轨道的切入使用 `put_away` 时长的 75% 和 TACZ 同款 ease-out cubic 曲线。这样疾跑中切换物品时，跑步姿势会连续收下，而不是直接跳到收枪第一帧。

沿用原 TACZ display 的两个字段，无需修改资产结构：

- `"default_animation":"my_pack:common"`：从 `assets/my_pack/animations/common.animation.json` 补齐缺失 clip。
- `"use_default_animation":"rifle"` / `"pistol"`：分别读取 `assets/tacz/animations/rifle_default.animation.json` / `pistol_default.animation.json`。前面的 default_animation 优先。

主动画里已有的同名 clip 优先。空字符串动作不会被缺省动画重新启用。
**JAR 不提供这些文件。** 原版 AK-47 的跑步动画依赖 TACZ 本体内的 rifle_default，并不在独立枪包的 ak47.animation.json 中；需自行制作，或从有权使用的 TACZ 素材中取得该文件并按上述路径放进资源包。无需修改 AK 的模型、动画或 display 文件。缺少文件会跳过相关动作并在日志提示，补齐后 F3+T 重载。

## 默认禁用的工具示例

仓库 `examples/tacz_vanilla_tools/` 提供木、石、铜、铁、金、钻石、下界合金的剑、斧、镐、铲、锄，共 35 个简单示例。
它们只保留在源码中，不打包进 JAR、不自动安装、不自动启用，并且每条映射显式设置 `enabled: false`。

需要时：

1. 从源码下载此目录，复制到实例的 `resourcepacks/tacz_vanilla_tools/`。
2. 编辑 `assets/tacz_tools/config/client_item_overrides.json`。
3. 仅把需要测试的物品（例如 diamond_sword）的 `enabled` 改为 `true`。
4. 在资源包界面启用该包，或 F3+T 重载。
5. 用左键挥动、挖掘、有效右键使用、I 检视测试。示例不启用 R。

示例模型是原版贴图轮廓挤出的简单形状，不含手臂定位组，不是最终美术资产；贴图引用来自游戏自身。
动画师可按同样格式替换其模型、贴图和动画。
可用 Python 3 + Pillow 运行 `tools/generate_tool_demo.py <Minecraft客户端JAR>` 重新生成，输出仍默认禁用。

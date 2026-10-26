# 动作与可选工具示例

`actions` 的值是原动画文件里的 clip 名称，动画师不必重命名自己的动画。

| 本地事件 | 默认 clip | 触发 |
| --- | --- | --- |
| idle | static_idle | 基础持握姿势 |
| draw | draw | 切入映射物品 |
| put_away | put_away | 切到空手或其他物品 |
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

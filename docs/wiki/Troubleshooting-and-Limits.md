# 排错与兼容边界

## 安装后没有变化

这是没有启用映射时的预期行为。检查：资源包是否启用；目录是否多套一层；映射是否在 `assets/<namespace>/config/`；物品 ID 是否正确；`enabled` 是否为 true。
示例工具包全部默认 false。修改后 F3+T 重载。

## 紫黑贴图

确认 display 的 `texture` 如 `my_pack:tools/sword` 对应真实文件 `assets/my_pack/textures/tools/sword.png`。
不要写成 `my_pack:textures/tools/sword.png`，渲染器会重复补目录和后缀。
检查大小写、PNG 和资源包内实际路径，不要只检查本地编辑器的引用。

## 模型偏移或手臂异常

检查 `idle_view`、父子关系、pivot、每面 UV，以及基础持握姿势是否在映射选中的 idle clip 中。
没有手臂定位组的示例不会显示玩家手臂。当前仅支持主手第一人称，左主手镜像、特殊副手组合和第三人称不应被视为已验证兼容。

## 动画不播放

检查动作名与 clip 名是否一致、animation 文件路径、骨骼名及正数 animation_length。
默认工具不会使用枪械 reload；use 依赖原版使用或挥动反馈。切换物品的 draw/put_away 阶段会保护切换过程，阻止其他动作打断。

## 当前已验证与未实现

已验证：26.2 Fabric 的第一人称主手替换、AK-47 的基本持握/射击/检视/收起、工具示例，嵌套骨骼变换、完整实体顶点及基础姿势叠加。
自动测试验证逻辑和网格，不代替每个资产包在游戏中的画面验收。

没有声称完整 TACZ 资源包功能兼容：

- 不执行 TACZ Lua 状态机、Molang、IK/完整约束与完整插值曲线。
- 不实现枪械真实机制、联网同步、音效事件、枪口火焰、弹壳、配件系统或瞄准系统。
- 不保证特殊 geometry、box UV、镜像、发光/透明/PBR 材质、Shaderpack 或其他渲染模组兼容。
- 普通资源包的 data/ 索引不会被客户端资源管理器读取；非标准 display 名称请在映射中直接指定。

## 提交问题

在 Issues 附上：Minecraft/Fabric/API/模组版本、复现步骤、物品映射、截图和 latest.log 或崩溃报告。
分享日志前移除访问令牌、服务器地址等不希望公开的信息。涉及私有素材可提供最小复现包，不必上传整套资产。

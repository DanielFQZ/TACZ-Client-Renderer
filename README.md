# TACZ Client Renderer

Minecraft **26.2 / Fabric / Java 25** 的纯客户端第一人称物品渲染器。
使用 TACZ 格式的模型和动画替换本机看到的原版武器或工具，不注册物品/实体，不额外发送网络包；服务器和其他玩家仍看到原版物品。

**默认无资产、无映射、无替换。** JAR 仅包含代码与界面语言资源。用户自行制作配置和资源包，或使用获得授权的素材。
本项目独立维护，非官方 TACZ 分支；来源见 [NOTICE.md](NOTICE.md)。

## 下载与配置

- [Releases](https://github.com/DanielFQZ/TACZ-Client-Renderer/releases)
- [配置 Wiki](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki)
- [Wiki 文档源码](docs/wiki/Home.md)

需要 Fabric Loader >= 0.20.1 和适用于 26.2 的 Fabric API。
安装主 JAR 到实例 mods/ 后，添加并启用自己的资源包。
在 `assets/<namespace>/config/client_item_overrides.json` 中映射原版物品：

```json
{
  "items": {
    "minecraft:crossbow": {"display": "tacz:ak47_display"},
    "minecraft:diamond_sword": {
      "display": "my_pack:sword_display",
      "actions": {"attack": "slash", "mine": "dig", "reload": ""}
    }
  }
}
```

这些例子要求你自己提供对应 display、模型、贴图和动画。资产的 TACZ 目录和配置格式保持原样。
切换时播放 draw/put_away，I 检视、R 换弹（仅视觉）；工具可单独映射攻击、挖掘和使用动作。

## 可选工具示例

[examples/tacz_vanilla_tools](examples/tacz_vanilla_tools) 提供 35 个粗略的原版工具模型与动作示例。
**不打包、不自动安装、不自动加载，每条映射默认 enabled:false。**
仅在需要时手动安装示例资源包，并开启所需物品的映射。
示例引用原版纹理，不附带游戏纹理图片，也不含手臂定位组。

## 兼容范围

这是精简的主手第一人称渲染器，不能视为完整 TACZ 客户端或全部枪包功能的兼容实现。
目前不执行 Lua/Molang，不实现完整约束/曲线、瞄准、配件或枪械游戏机制。
副手/第三人称保持原版。请阅读 [兼容边界](docs/wiki/Troubleshooting-and-Limits.md)。

## 构建

安装 JDK 25 后：

```powershell
./gradlew.bat build --no-daemon
```

Linux/macOS 使用 `./gradlew build`。产物位于 build/libs/。
构建包含：物品切换时间线、动画叠加、几何与 UV 回归、35 种工具示例的网格检查，以及 JAR 不包含资产/映射的检查。
无需下载默认 TACZ 枪包即可构建。如你本地持有相应资源，可额外验证 AK-47：

```powershell
./gradlew.bat build '-PtestPack=绝对路径/你的TACZ测试资源包'
```

## 许可证

代码采用 [GNU GPL v3](LICENSE)。本仓库不分发 TACZ 原版枪包、动画、声音、脚本和贴图。
第三方素材须遵守其作者授权；上游 TACZ 的资产使用独立许可证。

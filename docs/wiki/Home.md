# TACZ Client Renderer 配置 Wiki

这是一个面向 **Minecraft 26.2 + Fabric、Java 25** 的纯客户端第一人称渲染模组。
把原版物品映射到 TACZ 格式的模型和动画，本机可以看到替换，服务器和其他玩家仍看到原版物品。

**JAR 默认不包含枪械或工具的动画、模型、贴图，也不包含启用的物品映射。** 单独安装时不会替换任何物品。请自行制作资源包，或使用获得授权的现有素材并添加自己的映射。

- [安装与首次配置](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki/Installation)
- [物品映射配置](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki/Configuration)
- [制作模型与动画](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki/Asset-Authoring)
- [动作与可选工具示例](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki/Actions-and-Examples)
- [排错与兼容边界](https://github.com/DanielFQZ/TACZ-Client-Renderer/wiki/Troubleshooting-and-Limits)

本项目是独立的非官方项目。代码来源与许可证见仓库 NOTICE.md 和 LICENSE。
资产目录与字段沿用 TACZ，但当前渲染器并非完整 TACZ 客户端，尤其不执行 Lua 状态机。

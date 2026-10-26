# 安装与首次配置

## 环境

- Minecraft **26.2**，Fabric Loader **0.20.1 或更新版本**。
- Fabric API：开发验证版本为 **0.161.0+26.2**。
- Java **25**。
- 只在客户端安装。它不会给原版武器增加真实枪械伤害、弹药或服务器逻辑。

## 安装

1. 从本仓库 Releases 下载主 JAR（不要安装 `-sources.jar`）。
2. 放入所用实例的 `mods/`；删除该模组的重复旧版本。
3. 准备自己的客户端资源包，放入同一实例的 `resourcepacks/`。
4. 在游戏资源包界面启用该包，进入世界测试。

没有资源包和物品映射时，安装成功也不会改变画面，这是默认行为。

## 使用已有 TACZ 素材

在获得素材作者授权的前提下，保留 `assets/<namespace>/display/guns`、`geo_models`、`animations`、`textures` 等原目录。
资源包根目录必须有 `pack.mcmeta`，不要再套一层文件夹。
针对 26.2 的示例：

```json
{"pack":{"description":"My client item animations","min_format":88,"max_format":88}}
```

然后增加 `assets/tacz/config/client_item_overrides.json`：

```json
{"items":{"minecraft:crossbow":{"enabled":true,"display":"tacz:ak47_display"}}}
```

这里假设你自己的包实际包含 AK-47 的 display、模型、贴图和动画。**模组不会为这个例子自动下载或提供素材。**
若显示文件并非 `ak47_display.json`，请填写实际文件名（不含 `.json`）。

调整资源包后按 `F3 + T` 重载；替换 JAR 后必须完全重启客户端。

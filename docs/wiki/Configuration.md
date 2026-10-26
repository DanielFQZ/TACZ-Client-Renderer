# 原版物品映射

推荐入口：`assets/<namespace>/config/client_item_overrides.json`。
这是客户端资源文件，不能只放在 `data/` 下或服务器数据包中。

```json
{
  "items": {
    "minecraft:crossbow": {
      "enabled": true,
      "gun": "tacz:ak47"
    },
    "minecraft:diamond_sword": {
      "enabled": true,
      "display": "my_pack:sword_display",
      "actions": {
        "idle": "static_idle",
        "draw": "draw",
        "put_away": "put_away",
        "attack": "slash",
        "mine": "dig",
        "use": "",
        "inspect": "inspect",
        "reload": ""
      }
    }
  }
}
```

也可以拆分成 `assets/<namespace>/config/client_items/<文件名>.json`：

```json
{"item":"minecraft:diamond_pickaxe","enabled":true,"display":"my_pack:pickaxe_display","actions":{"attack":"swing","mine":"dig","reload":""}}
```

拆分文件建议明确写 `item`，文件名不用于猜测物品材质或类别。请使用标准 JSON；示例中的 `my_pack` 要换成自己的资源命名空间。

| 字段 | 含义 |
| --- | --- |
| items 的键 / item | 完整原版物品 ID，如 `minecraft:diamond_sword` |
| enabled | `false` 禁用此映射；省略时为 `true`，因此示例包显式写 `false` |
| display | display 文件 ID，省略 `display/guns/` 和 `.json`，优先于 gun |
| gun | 可选的 TACZ 枪械 ID；display 与 gun 至少有一个 |
| animation | 可选，覆盖 display 中的动画文件 ID |
| actions | 本地事件到已有动画名称的对应表，详见动作页 |
| state_machine | 保留字段；当前不执行 Lua 状态机 |

### gun 的解析规则

客户端资源管理器只读取 `assets/`。只有暴露为客户端资源的 `index/guns/*.json` 才能解析其中的 `display` 字段，普通资源包的 `data/` 不会被读取。
如果没有客户端索引，`tacz:ak47` 回退查找 `tacz:ak47_display`。
如果你原来的索引指向其他名称，**直接在新增映射里指定 display**，不必改名或迁移原 TACZ 资产。

### 范围与冲突

当前按物品 ID 匹配主手中的所有该类物品，不按名称、附魔、耐久或组件区分，也不支持 `#tag`/通配符映射。
副手和第三人称保持原版。耐久和数量变化不会重新触发掏出。
同一路径由资源包优先级覆盖；不同文件不要重复定义同一物品，以免依赖加载顺序。

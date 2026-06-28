# AuthOfflineBridge

AuthOfflineBridge 是一个 Fabric 服务端模组，用于在 Minecraft 身份验证服务不可用、服务器临时切换到离线模式时，尽量保留正版玩家原有的数据。

目标版本：

- Minecraft `26.1.2`
- Fabric Loader `0.19.3+`
- Fabric API `0.153.0+26.1.2`
- Java `25+`

## 功能

- 从 `usercache.json` 自动读取玩家名到正版 UUID 的映射。
- 支持通过 `config/authofflinebridge-uuids.json` 手动补充或覆盖 UUID 映射。
- 当本地缓存未命中时，可自动向 Mojang/Minecraft Services 官方 API 查询玩家正版 UUID。
- API 查询成功后会写回 `config/authofflinebridge-uuids.json`，后续重启不需要重复查询。
- 离线模式登录时，将玩家离线 UUID 映射到正版 UUID。
- 如果已有离线 UUID 玩家数据，会自动复制到正版 UUID 文件名，避免玩家背包、位置等数据丢失。
- 同步迁移玩家主数据、进度和统计数据。
- 服务端专用，客户端不需要安装。

## 使用方式

1. 将构建出的 `authofflinebridge-1.0.0.jar` 放入服务器 `mods` 文件夹。
2. 在 Microsoft/Mojang 身份验证服务正常时，以 `online-mode=true` 启动服务器，让服务器生成或刷新 `usercache.json`。
3. 玩家至少正常进入一次服务器，使正版 UUID 写入缓存。
4. 当身份验证服务不可用时，将 `server.properties` 改为：

   ```properties
   online-mode=false
   ```

5. 重启服务器。
6. 玩家用原玩家名进入服务器，模组会在离线 UUID 生成阶段桥接到缓存中的正版 UUID。

## 长期开启离线验证的服务器

如果你的服务器需要长期保持：

```properties
online-mode=false
```

但服务器仍然可以访问 Mojang/Minecraft Services 官方 API，本模组会在本地映射缺失时自动查询：

```text
https://api.minecraftservices.com/minecraft/profile/lookup/name/<玩家名>
```

如果该端点不可用，会回退到旧的 Mojang API：

```text
https://api.mojang.com/users/profiles/minecraft/<玩家名>
```

查询成功后，模组会把结果写入：

```text
server_directory/config/authofflinebridge-uuids.json
```

日志示例：

```text
Fetched online UUID for PlayerName from Mojang API: ...
Cached fetched online UUID for PlayerName in config/authofflinebridge-uuids.json
Replaced offline UUID with online UUID for PlayerName: ...
```

这样即使服务器从不切回 `online-mode=true`，也可以逐步为进服玩家建立正版 UUID 映射。

## 数据迁移行为

离线模式下，Minecraft 默认会按玩家名生成离线 UUID，例如：

```text
OfflinePlayer:PlayerName -> 离线 UUID
```

本模组命中映射后，会把离线 UUID 对应的数据复制到正版 UUID 对应的文件名：

```text
world/playerdata/<离线UUID>.dat -> world/playerdata/<正版UUID>.dat
world/advancements/<离线UUID>.json -> world/advancements/<正版UUID>.json
world/stats/<离线UUID>.json -> world/stats/<正版UUID>.json
```

如果正版 UUID 文件已经存在，模组不会覆盖它。这样可以避免玩家改名、手动别名或多个昵称映射到同一 UUID 时，后登录的昵称把同一个正版 UUID 的真实数据覆盖掉。

如果你确认要用离线 UUID 文件替换正版 UUID 文件，请先停止服务器，手动备份或删除目标正版 UUID 文件，再重新进服触发迁移。

复制完成后会写入一次性标记：

```text
<正版UUID>.dat.authofflinebridge.migrated
```

只要标记存在，后续登录不会反复用旧离线数据覆盖新的正版 UUID 数据。

如果确实需要重新迁移，停止服务器后删除对应的 `.authofflinebridge.migrated` 文件，并确认目标正版 UUID 文件不存在或已经手动备份，再重新进服。

## 改名和同 UUID 多昵称

正版账号的 UUID 不会随着玩家改名而改变。玩家改名后，只要使用当前正版昵称进服，官方 API 会返回同一个正版 UUID。

如果配置文件或缓存中出现多个昵称指向同一个正版 UUID，模组会打印警告，但不会崩溃：

```text
Names 'OldName' and 'NewName' both map to online UUID ...
```

这种情况通常表示玩家改过名，或你手动配置了别名。玩家数据最终按正版 UUID 归档，而不是按昵称归档。

需要注意：在 `online-mode=false` 下，任何人都可以输入这些昵称之一连接服务器。这个模组只负责 UUID 映射和数据迁移，不负责证明连接者拥有该正版账号。

## 手动配置

配置文件位置：

```text
server_directory/config/authofflinebridge-uuids.json
```

格式：

```json
{
  "PlayerName": "5f45724c-2a19-4cf3-883d-458d28910a2c"
}
```

规则：

- key 是玩家名。
- value 是玩家的正版 UUID。
- 以下划线开头的字段会被当作注释或示例跳过。
- 手动配置优先级高于 `usercache.json`。
- 可以配置多个昵称指向同一个正版 UUID，用于兼容改名前后的名字；模组会记录警告，但数据只会归到同一个正版 UUID。

## 日志判断

启动时应该看到映射表：

```text
Loaded 1 online UUID mappings from usercache.json
===== Player UUID Mapping Table =====
Name: PlayerName | Online UUID: ... | Offline UUID: ...
```

离线模式登录并迁移成功时，应该看到类似日志：

```text
Copied offline player data for PlayerName from ... to ...
Replaced offline UUID with online UUID for PlayerName: ...
PlayerName has ONLINE UUID: ... (bridge successful)
```

如果看到：

```text
PlayerName still has OFFLINE UUID: ...
```

说明桥接没有生效，需要检查玩家名是否匹配、`usercache.json` 是否包含正版 UUID，或手动配置是否正确。

## 构建

在仓库根目录执行：

```powershell
.\gradlew.bat build
```

构建产物：

```text
build/libs/authofflinebridge-1.0.0.jar
```

## 注意事项

- 该模组只解决临时离线模式下的数据延续问题，不会提供身份验证能力。
- `online-mode=false` 本身存在安全风险，任何人都可以冒用玩家名。自动查询官方 API 只能确认“这个名字对应哪个正版 UUID”，不能证明当前连接者拥有该账号。
- 首次迁移前建议备份整个 `world` 目录。
- 如果同时安装登录、认证或数据库相关模组，相关报错需要按对应模组处理；例如 H2 JDBC driver 缺失不属于本模组问题。

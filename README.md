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

如果正版 UUID 文件已经存在，模组会先创建备份：

```text
<正版UUID>.dat.authofflinebridge.bak
```

复制完成后会写入一次性标记：

```text
<正版UUID>.dat.authofflinebridge.migrated
```

只要标记存在，后续登录不会反复用旧离线数据覆盖新的正版 UUID 数据。

如果确实需要重新迁移，停止服务器后删除对应的 `.authofflinebridge.migrated` 文件，再重新进服。

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
- `online-mode=false` 本身存在安全风险，任何人都可以冒用玩家名。身份验证服务恢复后应尽快切回 `online-mode=true`。
- 首次迁移前建议备份整个 `world` 目录。
- 如果同时安装登录、认证或数据库相关模组，相关报错需要按对应模组处理；例如 H2 JDBC driver 缺失不属于本模组问题。

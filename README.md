# AuthOfflineBridge

Fabric 服务端模组。用于在 `online-mode=false` 时，把玩家的离线 UUID 映射回正版 UUID，并迁移已有离线玩家数据，避免背包、位置、进度和统计数据丢失。

支持长期关闭正版验证的服务器：只要服务器能访问 Mojang 官方 API，模组就能按玩家名自动查询正版 UUID 并缓存到本地。

## 版本

- Minecraft `26.1.2`
- Fabric Loader `0.19.3+`
- Fabric API `0.153.0+26.1.2`
- Java `25+`

## 功能

- 从 `usercache.json` 读取玩家名和正版 UUID。
- 从 `config/authofflinebridge-uuids.json` 读取手动映射。
- 本地未命中时，自动请求 Mojang/Minecraft Services API。
- 查询成功后写回本地配置，避免重复请求。
- 离线模式登录时，把离线 UUID 替换成正版 UUID。
- 首次桥接时，把离线 UUID 玩家数据复制到正版 UUID 文件名。
- 支持玩家改名：多个昵称可以指向同一个正版 UUID。
- 服务端专用，客户端无需安装。

## 安装

1. 构建模组：

   ```powershell
   .\gradlew.bat build
   ```

2. 将 jar 放入服务器 `mods` 目录：

   ```text
   build/libs/authofflinebridge-1.0.0.jar
   ```

3. 安装 Fabric API。

## 推荐使用方式

如果你平时使用正版验证：

1. 先以 `online-mode=true` 启动服务器。
2. 让玩家正常进入一次，生成 `usercache.json`。
3. 需要临时离线时，改为：

   ```properties
   online-mode=false
   ```

4. 重启服务器，玩家用原昵称进入。

如果你长期保持 `online-mode=false`，只要服务器能访问 Mojang API，也可以直接使用。玩家第一次进入时，模组会自动查询并缓存正版 UUID。

## UUID 查询顺序

登录时按以下顺序查找正版 UUID：

1. `config/authofflinebridge-uuids.json`
2. `usercache.json`
3. `https://api.minecraftservices.com/minecraft/profile/lookup/name/<玩家名>`
4. `https://api.mojang.com/users/profiles/minecraft/<玩家名>`
5. 全部失败时使用原版离线 UUID

手动配置优先级最高。

示例：

```json
{
  "PlayerName": "5f45724c-2a19-4cf3-883d-458d28910a2c"
}
```

## 数据迁移

命中正版 UUID 后，模组会尝试复制：

```text
world/playerdata/<离线UUID>.dat      -> world/playerdata/<正版UUID>.dat
world/advancements/<离线UUID>.json  -> world/advancements/<正版UUID>.json
world/stats/<离线UUID>.json         -> world/stats/<正版UUID>.json
```

如果目标正版 UUID 文件已经存在，模组不会覆盖它。这是为了避免玩家改名或多个昵称映射到同一 UUID 时，旧离线数据覆盖已有正版数据。

复制完成后会写入标记文件：

```text
<正版UUID>.dat.authofflinebridge.migrated
```

标记存在时不会重复迁移。需要重新迁移时，先停止服务器，手动备份或删除目标正版 UUID 文件，再删除对应的 `.authofflinebridge.migrated` 标记。

## 改名处理

正版 UUID 不随玩家改名变化。玩家改名后，官方 API 会返回同一个 UUID。

如果多个昵称映射到同一个 UUID，模组会打印警告：

```text
Names 'OldName' and 'NewName' both map to online UUID ...
```

这通常是正常的改名或手动别名。玩家数据最终按 UUID 归档，不按昵称归档。

## 日志判断

正常桥接时会看到：

```text
Fetched online UUID for PlayerName from Mojang API: ...
Cached fetched online UUID for PlayerName in config/authofflinebridge-uuids.json
Replaced offline UUID with online UUID for PlayerName: ...
PlayerName has ONLINE UUID: ... (bridge successful)
```

如果看到：

```text
PlayerName still has OFFLINE UUID: ...
```

说明桥接失败。检查玩家名、配置文件、`usercache.json` 或服务器是否能访问 Mojang API。

## 风险提示

- `online-mode=false` 无法证明连接者拥有该正版账号。
- 自动查询 API 只能确认“这个昵称当前对应哪个正版 UUID”。
- 长期开离线验证时，建议配合白名单、登录插件或其他身份校验方案。
- 首次迁移前建议备份整个 `world` 目录。

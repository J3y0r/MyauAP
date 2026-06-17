# MyauAP Lua 脚本开发文档

> **版本**: 1.0 | **引擎**: LuaJ 3.0.1 | **脚本目录**: `./config/Myau/scripts/`

---

## 目录

1. [快速开始](#快速开始)
2. [脚本生命周期](#脚本生命周期)
3. [事件系统](#事件系统)
4. [API 参考](#api-参考)
5. [ClickGUI 集成](#clickgui-集成)
6. [命令参考](#命令参考)
7. [完整示例](#完整示例)
8. [常见问题](#常见问题)
9. [附录：可用模块列表](#附录可用模块列表)

---

## 快速开始

### 目录与命名

脚本文件统一放在游戏运行目录下的 `config/Myau/scripts/` 文件夹中。

```
.minecraft/
└── config/
    └── Myau/
        └── scripts/       ← 脚本放这里
            ├── auto_gg.lua
            ├── welcome.lua
            └── anti_kb.lua
```

- 文件扩展名必须是 `.lua`
- 脚本名称 = 文件名去掉 `.lua`（例如 `auto_gg.lua` → 脚本名 `auto_gg`）
- 客户端启动时**自动扫描并加载**该目录下所有 `.lua` 文件
- 推荐使用 UTF-8 编码

### Hello World

创建 `config/Myau/scripts/hello.lua`：

```lua
myau.log("Hello, MyauAP!")
```

进游戏后聊天栏会显示 `[Script] Hello, MyauAP!`。

---

## 脚本生命周期

```
启动 → 扫描目录 → 加载所有 .lua → 脚本执行 → 注册事件 → 就绪
                                                        ↓
                                              事件回调循环运行
                                                        ↓
                                     .script unload 或 客户端关闭 → 卸载
```

**重要规则：**

- 脚本顶层代码在加载时**立即执行一次**（用于注册事件处理器、初始化变量）
- 事件回调在对应事件发生时反复触发
- 脚本加载后如果已在世界中，**自动触发一次 `world_load` 事件**
- 通过 ClickGUI 或 `.script` 命令**关闭某个脚本**后，它的所有事件回调都不会再执行（脚本仍在内存中）
- `.script reload` 会先卸载再重新加载，**变量会重置**
- 脚本加载失败时，会在聊天栏显示红色错误信息，但不影响其他脚本

---

## 事件系统

所有事件通过 `event.事件名(回调函数)` 注册。

### `event.tick(callback)`

**触发频率**: 每秒约 20 次（每个游戏 tick）  
**参数**: 无  
**用途**: 持续检测、自动化操作

```lua
event.tick(function()
    if myau.player().health < 10 then
        myau.chat("/heal")  -- 低血量自动治疗
    end
end)
```

⚠️ **性能警告**: tick 事件回调不要太重。在里面放死循环、大量字符串拼接或频繁调用聊天命令会严重掉帧。

### `event.chat(callback)`

**触发**: 收到服务器聊天消息  
**参数**: `message` (string) — 原始消息文本（不含颜色代码）  
**用途**: 自动回复、击杀计数、关键词触发

```lua
event.chat(function(msg)
    -- 检测击杀
    if string.find(msg, "was killed by") then
        kills = kills + 1
        myau.log("Kill #" .. kills)
    end

    -- 自动回复
    if string.find(msg, "gl hf") then
        myau.chat("gl hf!")
    end
end)
```

⚠️ **注意**: 只接收**服务器发来的**消息（`S02PacketChat`），不接收自己发送的。

### `event.world_load(callback)`

**触发**: 进入世界/切换服务器  
**参数**: 无  
**用途**: 初始化、重置状态

```lua
event.world_load(function()
    kills = 0
    game_started = true
    myau.log("Welcome to a new world!")
end)
```

### `event.world_unload(callback)`

**触发**: 离开世界  
**参数**: 无  
**用途**: 清理状态

> ⚠️ 此事件已注册，但实际触发依赖于服务端 WORLD_UNLOAD 事件的实现。

### `event.attack(callback)`

**触发**: 攻击实体  
**参数**: 无  
**用途**: 攻击后自动操作

> ⚠️ 此事件已注册，但实际触发需要在事件系统中接入 AttackEvent。

### `event.death(callback)`

**触发**: 玩家死亡  
**参数**: 无  
**用途**: 死亡自动响应

> ⚠️ 此事件已注册，但实际触发需要在事件系统中接入死亡事件。

### `event.module_toggle(callback)`

**触发**: 任意模块被开关  
**参数**: `moduleName` (string), `enabled` (boolean)  
**用途**: 模块联动

> ⚠️ 此事件已注册，但实际触发需要在 Module.setEnabled() 中调用 ScriptManager.onModuleToggle()。

```lua
event.module_toggle(function(name, enabled)
    myau.log(name .. " is now " .. (enabled and "ON" or "OFF"))
end)
```

---

## API 参考

所有 API 通过全局 `myau` 表调用。

### `myau.chat(message)`

发送消息到公共聊天频道。

```lua
myau.chat("Hello everyone!")           -- 普通消息
myau.chat("/lobby")                    -- 命令
myau.chat("GG! " .. kills .. " kills") -- 拼接变量
```

| 参数 | 类型 | 说明 |
|------|------|------|
| message | string | 要发送的消息或命令 |

**返回值**: 无  
**注意**: 等同于玩家在聊天栏输入后回车，服务器会收到这条消息。

---

### `myau.log(message)`

在客户端聊天栏显示一条带 `[Script]` 前缀的消息。**仅自己可见，不会发送给服务器**。

```lua
myau.log("Script initialized")
myau.log("Current health: " .. myau.player().health)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| message | string | 要显示的消息（支持 & 颜色代码） |

**返回值**: 无  
**注意**: 适合调试输出，不会干扰其他玩家。

---

### `myau.toggle(moduleName [, state])`

切换或设置模块的开关状态。

```lua
-- 切换开关（开→关，关→开）
myau.toggle("KillAura")

-- 强制打开
myau.toggle("KillAura", true)

-- 强制关闭
myau.toggle("Spammer", false)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| moduleName | string | 模块名称（不区分大小写） |
| state | boolean (可选) | true=开启, false=关闭。不传则切换 |

**返回值**: 无  
**名称匹配**: 不区分大小写。`"killaura"`、`"KillAura"`、`"KiLLaUrA"` 均可。

---

### `myau.isEnabled(moduleName)`

查询模块是否已开启。

```lua
if myau.isEnabled("KillAura") then
    myau.log("KillAura is active")
else
    myau.log("KillAura is off")
end
```

| 参数 | 类型 | 说明 |
|------|------|------|
| moduleName | string | 模块名称（不区分大小写） |

**返回值**: `boolean` — `true` 表示开启中

---

### `myau.player()`

获取当前玩家状态信息。

```lua
local p = myau.player()

myau.log("Position: " .. string.format("%.1f, %.1f, %.1f", p.x, p.y, p.z))
myau.log("Health: " .. p.health)
myau.log("On ground: " .. tostring(p.onGround))
myau.log("Dimension: " .. p.dimension)  -- 0=主世界, -1=地狱, 1=末地
```

**返回值**: `table`，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 玩家名称 |
| `x` | number | X 坐标 |
| `y` | number | Y 坐标（脚底） |
| `z` | number | Z 坐标 |
| `health` | number | 当前血量（0-20） |
| `onGround` | boolean | 是否在地面 |
| `isInWater` | boolean | 是否在水中 |
| `isInLava` | boolean | 是否在岩浆中 |
| `food` | number | 饥饿值（0-20） |
| `dimension` | number | 0=主世界, -1=地狱, 1=末地 |

---

### `myau.server()`

获取当前服务器信息。

```lua
local s = myau.server()
myau.log("Server: " .. s.ip)
```

**返回值**: `table`

| 字段 | 类型 | 说明 |
|------|------|------|
| `ip` | string | 服务器 IP（单人游戏时为 `"singleplayer"`） |

---

### `myau.getSetting(moduleName, settingName)`

读取模块的某个设置项的当前值。

```lua
local range = myau.getSetting("KillAura", "maxRange")
local speed = myau.getSetting("Scaffold", "placeDelay")
myau.log("KillAura range: " .. range)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| moduleName | string | 模块名称 |
| settingName | string | 设置项字段名（区分大小写，必须与代码中一致） |

**返回值**: 设置项的当前值（number/boolean/string），找不到则返回 `nil`。

⚠️ **重要**: `settingName` 使用的是 **Java 代码中的字段名**，不是 ClickGUI 显示的名称。常用字段名：

| 模块 | 设置项 | 字段名 | 类型 |
|------|--------|--------|------|
| KillAura | 攻击距离 | `maxRange` | number |
| KillAura | FOV | `fov` | number |
| KillAura | APS | `aps` | number |
| Scaffold | 方块延迟 | `placeDelay` | number |
| Scaffold | 模式 | `mode` | string |
| Velocity | 水平倍率 | `horizontal` | number |
| Speed | 速度倍率 | `speed` | number |
| AutoClicker | 左键CPS | `leftCPS` | number |
| Reach | 攻击距离 | `range` | number |
| Sprint | 保持冲刺 | `keepSprint` | boolean |

> 完整的字段名请查看对应模块的 Java 源码中 `Property` 声明。

---

### `myau.setSetting(moduleName, settingName, value)`

修改模块的某个设置项。

```lua
-- 数值型设置
myau.setSetting("KillAura", "maxRange", 4.5)
myau.setSetting("Scaffold", "placeDelay", 0)

-- 布尔型设置
myau.setSetting("Sprint", "keepSprint", false)

-- 字符串模式设置（ModeProperty）
myau.setSetting("Scaffold", "mode", "normal")
```

| 参数 | 类型 | 说明 |
|------|------|------|
| moduleName | string | 模块名称 |
| settingName | string | 设置项字段名 |
| value | number/boolean/string | 新值。类型必须与设置项类型匹配 |

**返回值**: `boolean` — `true` 表示设置成功，`false` 表示失败（模块不存在、字段不存在或类型不匹配）

**支持的类型：**
- `FloatProperty` — 浮点数（如 `3.5`）
- `IntProperty` — 整数（如 `4`）
- `BooleanProperty` — 布尔值（`true`/`false`）
- `ModeProperty` — 模式选择（字符串名如 `"normal"`，或整数索引如 `0`）

---

## ClickGUI 集成

### 脚本在 ClickGUI 中的表现

按 **右 Shift** 打开 ClickGUI，最下方新增了 **Scripts** 分类：

```
┌─────────────┐
│   Scripts   │
├─────────────┤
│ auto_gg  ✔️ │  ← 已开启
│ welcome  ✖️ │  ← 已关闭
│ anti_kb  ✔️ │
└─────────────┘
```

- **开关**：点击切换脚本的启用/禁用。关闭后该脚本的所有事件回调停止执行。
- **按键绑定**：使用 `.bind <脚本名> <按键>` 绑定快捷键（同普通模块）
- **实时更新**：关闭再重新打开 ClickGUI 会刷新脚本列表
- **错误标记**：加载失败的脚本后缀显示 `error`

### 注意

- 每个脚本模块**默认开启**
- 脚本模块**无子设置面板**（与普通模块不同）
- 通过 ClickGUI 关闭再开启不会重置脚本变量（不同于 reload）

---

## 命令参考

| 命令 | 说明 |
|------|------|
| `.script list` | 列出所有已加载的脚本及其状态 |
| `.script load <name>` | 加载指定脚本（name 不含 .lua） |
| `.script reload <name>` | 重新加载指定脚本（变量会重置） |
| `.script unload <name>` | 卸载指定脚本 |
| `.script reloadall` | 重新加载所有脚本 |
| `.bind <name> <key>` | 为脚本模块绑定快捷键 |

**别名**: `.scripts` 和 `.lua` 等同于 `.script`

**加载新脚本**：把 `.lua` 文件放进 `scripts/` 目录后，不需要重启客户端。执行：

```
.script load 你的文件名
```

### 示例

```text
.script list
→ [Script] Loaded scripts (2):
→  - auto_gg [ON]
→  - welcome [OFF]

.script load my_script
→ [Script] Loaded my_script

.script reload auto_gg
→ [Script] Reloaded auto_gg

.script unload welcome
→ [Script] Unloaded welcome

.bind auto_gg p
→ (按 P 键切换 auto_gg 脚本的开关)
```

---

## 完整示例

### 示例 1：自动 GG + 击杀计数

```lua
-- auto_gg.lua
local kills = 0

event.chat(function(msg)
    -- 统计击杀
    if string.find(msg, "was killed by") then
        kills = kills + 1
        myau.log("Kill #" .. kills)
    end

    -- 游戏结束时自动发 GG
    if string.find(msg, "1st Killer")
    or string.find(msg, "Winner")
    or string.find(msg, "游戏结束") then
        myau.chat("GG! " .. kills .. " kills")
        kills = 0
    end
end)

event.world_load(function()
    kills = 0
end)

myau.log("AutoGG script loaded!")
```

### 示例 2：低血量自动逃跑

```lua
-- auto_escape.lua
local panicking = false

event.tick(function()
    local p = myau.player()

    if p.health <= 6 and not panicking then
        panicking = true
        myau.toggle("Speed", true)
        myau.toggle("Velocity", true)
        myau.log("&c[PANIC] Low health! Speed + Velocity ON")
    end

    if p.health > 14 and panicking then
        panicking = false
        myau.toggle("Speed", false)
        myau.toggle("Velocity", false)
        myau.log("&aRecovered. Modules restored.")
    end
end)
```

### 示例 3：自动切模块配置

```lua
-- anti_invis.lua
-- 进游戏自动关闭显眼模块，切换隐身模式

event.world_load(function()
    myau.toggle("ESP", false)
    myau.toggle("NameTags", false)
    myau.toggle("KillAura", false)
    myau.log("Stealth mode engaged!")
end)
```

### 示例 4：击杀播报 + 自动嘲讽

```lua
-- kill_announcer.lua
local kill_msgs = {
    "{player} just got demolished!",
    "RIP {player}",
    "L bozo {player}",
    "{player} needs a better gaming chair",
}

event.chat(function(msg)
    local victim = string.match(msg, "(.+) was killed by ")
    if victim then
        local msg_template = kill_msgs[math.random(#kill_msgs)]
        local final_msg = string.gsub(msg_template, "{player}", victim)
        myau.chat(final_msg)
    end
end)
```

### 示例 5：动态调整 KillAura 距离

```lua
-- adaptive_range.lua
-- 根据血量动态调整 KillAura 攻击距离

event.tick(function()
    local hp = myau.player().health
    local range

    if hp > 15 then
        range = 6.0        -- 满血：激进
    elseif hp > 8 then
        range = 4.5        -- 半血：正常
    else
        range = 3.0        -- 残血：保守
    end

    myau.setSetting("KillAura", "maxRange", range)
end)
```

---

## 常见问题

### Q: 脚本加载后什么都没发生？

1. 检查文件名是否以 `.lua` 结尾
2. 检查文件是否在 `config/Myau/scripts/` 目录下
3. 执行 `.script list` 查看是否加载成功
4. 如果显示 `(error)`，执行 `.script reload <name>` 查看错误信息

### Q: `myau.setSetting` 返回 false？

- 检查 settingName **大小写**是否与 Java 字段名完全一致
- 检查模块名是否拼写正确（不区分大小写）
- 检查 value 类型是否匹配（ModeProperty 只能设字符串）

### Q: 如何找到模块的设置项字段名？

查看对应模块的 Java 源文件（`myau/module/modules/KillAura.java` 等），找到这类声明：

```java
public FloatProperty maxRange = new FloatProperty("Max Range", 4.5, 1.0, 6.0);
```

这里 `maxRange` 就是字段名。

### Q: 脚本太多会影响性能吗？

- **tick 回调要轻量**。避免在 `event.tick()` 里做大量计算、字符串拼接或频繁调用 `myau.chat()`
- 不用的脚本可以通过 ClickGUI 关闭
- 关闭后的脚本不消耗 CPU

### Q: 脚本中可以用 Lua 标准库吗？

**大部分可以**。LuaJ（JSE 版本）提供了完整的 Lua 5.2 标准库：

- `string.*` — 字符串操作 ✅
- `math.*` — 数学函数 ✅
- `table.*` — 表操作 ✅
- `os.clock()` — 计时 ✅
- `os.time()` / `os.date()` — 时间 ✅
- `os.execute()` — ❌ **不要用**，可能卡死游戏
- `io.*` — ❌ 不推荐（使用 `myau.log()` 输出）
- `coroutine.*` — ✅ 协程可用

### Q: 如何调试脚本？

```lua
-- 打印变量
myau.log("x = " .. tostring(x))

-- 检查是否进入某个分支
myau.log("Branch A entered")

-- 检查类型
myau.log("Type: " .. type(some_var))
```

配合 `.script reload <name>` 快速迭代。

---

## 附录：可用模块列表

以下模块名可用于 `myau.toggle()`、`myau.isEnabled()`、`myau.getSetting()` 和 `myau.setSetting()`：

### Combat（战斗）
`AimAssist` `AutoClicker` `AutoProjectiles` `KillAura` `JumpReset`
`Wtap` `Velocity` `Freeze` `Reach` `TargetStrafe` `NoHitDelay`
`AntiFireball` `LagRange` `HitBox` `MoreKB` `Refill` `HitFlick`
`HitSelect` `BackTrack`

### Movement（移动）
`AntiAFK` `Fly` `Speed` `LongJump` `Sprint` `SafeWalk`
`Jesus` `Blink` `NoFall` `NoSlow` `KeepSprint` `Eagle`
`NoJumpDelay` `AntiVoid` `Stuck`

### Render（视觉）
`ESP` `Chams` `FullBright` `Tracers` `NameTags` `Xray` `TargetHUD`
`Indicators` `BedESP` `ItemESP` `ViewClip` `NoHurtCam` `HUD`
`ClickGui` `ChestESP` `Trajectories` `Radar` `OpelGLError`

### Player（玩家）
`AutoHeal` `AutoTool` `ChestAura` `ChestStealer` `InvManager`
`InvWalk` `Scaffold` `LegitScaffold` `AutoBlockIn` `AutoBedDef`
`SpeedMine` `FastPlace` `GhostHand` `MCF` `AntiDebuff`

### Misc（杂项）
`AutoL` `Spammer` `BedNuker` `BedTracker` `LightningTracker`
`NoRotate` `NickHider` `AntiObbyTrap` `AntiObfuscate` `AutoAnduril`
`InventoryClicker` `ClientInfo`

---

## 相关文件索引

| 文件 | 说明 |
|------|------|
| `myau/script/ScriptEvent.java` | 事件类型枚举 |
| `myau/script/LuaScript.java` | 单脚本运行时 |
| `myau/script/ScriptManager.java` | 脚本管理器 |
| `myau/script/ScriptModule.java` | 脚本模块包装器（ClickGUI 集成） |
| `myau/script/api/MinecraftAPI.java` | Lua API 绑定层 |
| `myau/command/commands/ScriptCommand.java` | `.script` 命令 |

---

*文档版本: 1.0 | 最后更新: 2026-06-17 | GuardTeam © 2026 保留所有权力*

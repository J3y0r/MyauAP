# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build And Run

- `./gradlew build` 构建模组产物。`assemble` 依赖 `remapJar`，因此默认会生成重映射后的最终 jar，而不只是开发态 jar。
- `./gradlew runClient` 启动 Forge 1.8.9 的 Loom 开发客户端，是验证行为改动的主要入口。
- `./gradlew clean` 清理 Gradle 构建产物。
- `./gradlew genSources` 反编译并生成 Minecraft 源码，适合在映射或依赖调整后刷新开发环境。
- `./gradlew genIntelliJRuns` 重新生成 IntelliJ 运行配置。
- `./gradlew test` 运行 Gradle 测试任务。
- `./gradlew test --tests "package.ClassName"` 运行单个测试类。
- `./gradlew test --tests "package.ClassName.methodName"` 运行单个测试方法。

## Project Shape

- 这是单模块 Gradle 工程，`settings.gradle.kts` 只定义了根项目 `myau`，没有子项目。
- 技术栈是 Java 8 + Forge `1.8.9` + Architectury Loom。核心产物通过 `jar -> shadowJar -> remapJar` 链路生成。
- 运行时入口不是传统 `@Mod` 类，而是 mixin 驱动。`myau.mixin.MixinMinecraft` 注入 `Minecraft.startGame`：开头构造 `myau.init.Initializer`，结尾构造 `myau.Myau`。
- `myau.Myau` 是组合根。这里会创建所有 manager、注册事件监听、手工注册全部模块与命令、加载配置/好友/目标数据，并初始化账号管理器。

## Event And Mixin Architecture

- `src/main/resources/mixins.myau.json` 没有静态列出 mixin，而是指定 `myau.init.FMLLoadingPlugin`。这个插件会在运行时扫描 `myau.mixin` 包并返回其中的所有 mixin 类。
- mixin 层是与 Minecraft/Forge 交互的边界。大多数注入点会把原生流程转换成 `myau.events.*` 事件，然后交给 `myau.event.EventManager.call(...)` 分发。
- `myau.event.EventManager` 按事件的精确运行时类型分发，不会沿继承链匹配父类监听器。新增监听时要订阅实际被触发的具体事件类。

## Modules, Properties, And UI Coupling

- 新模块必须先在 `myau.Myau` 中加入 `moduleManager.modules`；否则不会收到事件，也不会进入配置读写流程。
- 模块配置是反射注册的。启动时 `Myau` 会扫描每个模块的声明字段，把 `Property<?>` 字段收集到 `myau.property.PropertyManager`。不挂在模块字段上的设置不会出现在配置文件或 Click GUI 中。
- `myau.ui.clickgui.ClickGui` 还手工维护模块分类。给 `Myau` 注册新模块后，必须同步放进 `combatModules`、`movementModules`、
  `renderModules`、`playerModules`、`miscModules` 之一；否则 `ClickGui` 构造时会因“未注册到 GUI 分类”直接抛异常。
- `myau.module.ModuleManager` 负责按键切换与 HUD/聊天提示；模块自身则通过 `@EventTarget` 直接订阅事件。

## Persistence And Commands

- 主配置目录是 `./config/Myau/`。`myau.config.Config` 会把每个模块的启用状态、按键、隐藏状态和属性值序列化为该目录下的 JSON 文件。
- 好友和目标名单分别持久化到 `friends.txt` 与 `enemies.txt`，由 `myau.management.PlayerFileManager` 的子类管理。
- Click GUI 的布局状态单独保存在 `./config/Myau/clickgui.txt`。
- 点号前缀命令不是从聊天界面直接处理，而是在 `myau.command.CommandManager` 中拦截发送中的 `C01PacketChatMessage` 后执行，所以命令逻辑发生在消息发往服务器之前。
- 内嵌账号管理器独立于主配置系统。它把账号数据写到 Minecraft 数据目录下的 `openmyau.accounts.json`，不是 `./config/Myau/`。

## Current Testing State

- Gradle 暴露了 `test` 任务，但仓库当前没有 `src/test` 目录，也没有可见的测试依赖。
- 目前实际可依赖的验证方式主要是 `./gradlew build` 和 `./gradlew runClient` 下的手动行为检查。

## Git Rules

- 推送信息不要包含类似 'Co-Author: Claude' 之类的信息

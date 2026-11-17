# 星契 StarPact

> Minecraft **1.20.1** 的任务追踪 HUD 与任务面板插件。
> 把 **Chemdah** 的任务数据渲染成 **ArcartX** 的界面。

星野尘梦工作室 · 默默

---

## 它是什么

StarPact 是一个**纯服务端插件**，作用是把 Chemdah 的任务系统可视化：

- **任务追踪 HUD** —— 屏幕角落常驻显示当前追踪的那一个任务
- **任务面板** —— 按 `J` 打开，全屏浏览接取中的任务、查看详情与奖励
- **HUD 设置** —— 独立面板，实时调整 HUD 的显隐、位置、缩放、宽度
- **奖励发放** —— 任务完成时自动发物品 / 执行指令
- **对话界面** —— 用自绘的 AX 面板替换 Chemdah 的箱子对话（仿原神风格），
  箱子对玩家完全不可见。[详见 `docs/CHAT.md`](docs/CHAT.md)

客户端**不需要**安装额外模组，只需要 **ArcartX 客户端模组**（Forge 1.20.1）。

---

## HUD 长什么样

```
沙砾采集                  ← 标题：当前追踪的任务名
────────────              ← 一根细分割线
◈ 沙砾采集        0/3     ← 目标名 + 当前/目标
```

设计目标是**不遮挡画面**：

- 没有底板、没有边框，只有文字 + 一根横线
- 标题字号 23、正文 15，主次分明
- **只显示一个任务**（玩家在面板里点「追踪」选定）
- 接取新任务时 **HUD 从透明淡入**（400ms），并播放一声提示音
- **没有任务时 HUD 完全隐藏**，不留空面板

---

## 功能细节

### 任务面板

按 `J` 打开：

- 三个分类页：**时序 / 间章 / 残流**（对应 Chemdah 的 `meta.type`）
- 左栏任务列表，右栏详情（描述 + 逐条目标进度）
- **奖励预览**：真实物品图标 + 鼠标悬停看 ToolTip
  - 支持 **MythicMobs** / **NeigeItems** / 原版材质
  - 支持**指令型奖励**（经验、点券、权限），可用 NI 物品做图标
  - NI 图标支持传参：`ni:StarPactExpBottle?amount=500`

### 奖励自动发放

任务完成时 StarPact 自动发奖：

```yaml
starpact_ore:
  rewards:
    - provider: MM
      key: "StarPactTestSword"      # 名称/Lore 直接用 MM 里配的
      amount: 1
    - command: "xp add {player} 100 points"
      icon: "ni:StarPactExpBottle?amount=100"
```

- **物品**：`MM` / `NI` / `VANILLA`，**名称和 Lore 优先用物品库里配的**（不用再写 `display`）
- **指令**：以控制台身份执行，支持 `{player}` 占位符
- 背包满了会掉在脚下，不让玩家白做任务

### HUD 设置界面

游戏内可调，按玩家单独保存：

| 项 | 说明 |
|---|---|
| 显示 HUD | 开 / 关 |
| 垂直位置 | 滑块（拖动或滚轮） |
| 重置位置 | 恢复默认 |
| HUD 缩放 | ±10% |
| HUD 宽度 | ±40 |
| HUD 位置 | 靠左 / 靠右吸附到屏幕边缘 |

---

## 依赖

| 依赖 | 版本 | 必需 | 说明 |
|---|---|---|---|
| Paper | 1.20.1 | ✅ | 服务端 |
| ArcartX | 2.7.77 | ✅ | 界面渲染。服务端插件与客户端模组**版本必须一致** |
| Chemdah | 1.1.34+ | ✅ | 任务数据来源。缺失时降级到 BossBar |
| MythicMobs | 任意 | 可选 | 用 `MM` 奖励时需要 |
| NeigeItems | 任意 | 可选 | 用 `NI` 奖励 / 图标时需要 |

---

## 安装

1. 把 `StarPact-Plugin-<版本>.jar` 放进服务端 `plugins/`
2. 确保服务端装了 **ArcartX**（与客户端同版本）
3. 启动服务端，插件会：
   - 创建 `plugins/StarPact/` 数据目录
   - 释放默认 UI 配置到 `plugins/StarPact/ui/`
   - 写入 ArcartX 按键配置到 `plugins/ArcartX/`

4. 在 `plugins/Chemdah/core/quest/` 写任务
5. 在 `plugins/StarPact/quests/` 配展示信息（名称 / 分类 / 描述 / 奖励）

> ⚠️ **`plugins/Chemdah/core/quest/` 里不要放备份文件**（`.bak` / `.old`）。
> Chemdah 会扫描目录下所有文件，同名任务被注册两次会导致**进度翻倍**。

### 客户端图标

任务面板的图标（页签、设置、关闭）需要放在客户端：

```
.minecraft/resourcepacks/ArcartX/resource/starpact/gui/
├── tab_shixu.png       时序（主线）
├── tab_jianzhang.png   间章（支线）
├── tab_canliu.png      残流（每日/限时）
├── btn_setting.png
└── btn_close.png
```

这些图标也在本仓库 `src/main/resources/assets/starpact/textures/gui/` 里。

---

## 使用

| 操作 | 说明 |
|---|---|
| `J` | 打开 / 关闭任务面板 |
| 点顶部图标 | 切换「时序 / 间章 / 残流」分类 |
| 点左侧任务 | 右侧显示详情与奖励 |
| 点「追踪」 | 让 HUD 显示这个任务 |
| 点右上 `⚙` | 进入设置页 |

### 命令

| 命令 | 作用 |
|---|---|
| `/sp` | 查看帮助 |
| `/sp panel` | 打开任务面板 |
| `/sp toggle` | 显示 / 隐藏 HUD |
| `/sp status` | 查看插件状态 |
| `/sp reload` | 重载配置与 UI |
| `/sp reward <任务ID>` | 把该任务的奖励物品发给自己（测试用，**不执行指令奖励**） |

---

## 从源码构建

需要 **JDK 17**。

```bash
# 1. 准备依赖 jar（第三方商业插件，不随源码分发）
mkdir libs
# 把 Chemdah 和 ArcartX 的 jar 放进 libs/

# 2. 配置路径（gradle.properties）
# chemdah_jar=libs/Chemdah.jar
# arcartx_jar=libs/ArcartX.jar

# 3. 构建
./gradlew build

# 产物：build/libs/StarPact-Plugin-1.1.0.jar
```

---

## 架构

```
ChemdahHook    →  只判断「Chemdah 在不在」（零 Chemdah 引用，纯 Class.forName）
ChemdahReader  →  真正读 Chemdah（import Chemdah，只在确认存在后反射加载）
HudService     →  决定用 AX HUD 还是降级 BossBar
AxHook         →  反射调 ArcartX，失败只降级不崩
```

### 必须遵守的三条

**1. `ChemdahHook` 里不能出现任何 Chemdah 类型**（方法签名、字段、泛型都不行）。

Chemdah 未安装时，JVM 在类解析阶段就会抛 `NoClassDefFoundError`，
这个错误 **catch 不住**，会直接打断插件启用。

**2. 不要直接 import Chemdah 的事件类。**

它们继承 `taboolib.platform.type.BukkitProxyEvent`，那个基类不在 Chemdah 的 jar 里
（TabooLib 运行时注入），编译期 import 不到。
所有事件监听都通过**反射 + `registerEvent()`** 注册。

**3. 不要用 `@EventHandler` 监听基类 `Event`。**

Bukkit 会报 `Unable to find handler list`。必须用
`getPluginManager().registerEvent(Class, Listener, Priority, EventExecutor, Plugin)`。

---

## UI 坐标约定

**这是踩坑最多的地方**，改 UI 前务必先看。

- **`screenScale: false`**：必须为 false。否则 AX 会二次缩放整个 UI，
  服务端下发的像素坐标和实际渲染位置对不上（靠右吸附会算错）。
- **控件 `width` / `x` 单位**：与 `Screen.getWidth()` 同一套坐标系。
- **HUD 是 `defaultOpen: false`**：UI 完全由服务端 `open()` / `close()` 控制，
  没任务时不调用 `open()`，HUD 完全不存在。
- **`tasks` 的 `time` 是首次延迟，不是运行时长**。
  `time: 3000` = UI 打开 3 秒后才开始跑第一次循环。
- **Aria 不支持嵌套 `if`**，也不支持 `//` 注释。
  嵌套会被**静默丢弃** → 整个脚本失效，只在客户端日志留一行 warning。
- **`effect.stroke` 的 `color: ~R,G,B,A` 会触发解析错误**
  （`Expected RBRACKET, got COMMA`），HUD 上不要用带描边的特效。

> **UI 行为异常时，先看客户端 `logs/latest.log` 里的 `[Aria]` 报错。**

---

## 已知限制

- **HUD 位置不能动态跟随小地图**：StarTrail 是独立客户端模组，
  AX 的 Aria 访问不到它的尺寸配置。目前按屏幕宽度比例缩放来近似对齐。
- **动画在客户端做**：服务端 `runTaskLater` 最小 1 tick（50ms），做不到 60fps，
  所以淡入由客户端 `tasks` 按时间戳驱动。
- **任务分类**：依赖 Chemdah 任务的 `meta.type` 字段匹配关键字
  （`时序` / `间章` / `残流`），未匹配到的默认归入「时序」。

---

## 许可

保留所有权利。转载 / 修改前请联系作者。

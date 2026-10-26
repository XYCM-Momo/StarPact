# 星契 StarPact

> Minecraft **1.20.1** 的任务追踪 HUD 与任务面板插件。
> 把 **Chemdah** 的任务数据渲染成 **ArcartX** 的界面。

星野尘梦工作室 · 默默

---

## 它是什么

StarPact 是一个**纯服务端插件**，作用是把 Chemdah 的任务系统可视化：

- **任务追踪 HUD** —— 屏幕角落常驻显示当前追踪的任务目标与进度
- **任务面板** —— 按 `J` 打开，全屏浏览接取中的任务、查看详情与奖励
- **HUD 设置** —— 独立面板，实时调整 HUD 的显隐、位置、缩放、宽度

客户端**不需要**安装额外模组，只需要 **ArcartX 客户端模组**（Forge 1.20.1）。

---

## 依赖

| 依赖 | 版本 | 必需 | 说明 |
|---|---|---|---|
| Paper | 1.20.1 | ✅ | 服务端 |
| ArcartX | 2.7.77 | ✅ | 界面渲染。服务端插件与客户端模组**版本必须一致** |
| Chemdah | 1.1.33-FREE-MySQL | 可选 | 任务数据来源。缺失时降级到 BossBar/计分板 |
| PlaceholderAPI | 2.x | 可选 | 变量支持 |
| Adyeshach | 2.x | 可选 | — |

---

## 安装

1. 把 `StarPact-Plugin-<版本>.jar` 放进服务端 `plugins/`
2. 确保服务端装了 **ArcartX**（同版本）
3. 启动服务端，插件会：
   - 创建 `plugins/StarPact/` 数据目录
   - 释放默认 UI 配置到 `plugins/StarPact/ui/`
   - 写入 ArcartX 按键配置到 `plugins/ArcartX/key_bind/simple_key/` 与 `plugins/ArcartX/scripts/`

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
| `J` | 打开/关闭任务面板 |
| 点顶部图标 | 切换「时序 / 间章 / 残流」分类 |
| 点左侧任务 | 右侧显示详情与奖励 |
| 点右上 `⚙` | 进入设置页 |
| 点「打开 HUD 设置」 | 关闭面板，打开 HUD 调整面板 |

### 命令

| 命令 | 作用 |
|---|---|
| `/sp` | 查看帮助 |
| `/sp panel` | 打开任务面板 |
| `/sp toggle` | 显示/隐藏 HUD |
| `/sp move <dx> <dy>` | 移动 HUD |
| `/sp scale <0.5~2>` | HUD 缩放 |
| `/sp reset` | 重置 HUD 位置 |
| `/sp reload` | 重载配置与 UI |

---

## 从源码构建

需要 **JDK 17**。

```bash
# 1. 准备可选依赖（Chemdah / ArcartX 的 jar）
mkdir libs
# 把 [C][任务]Chemdah-*.jar 和 [V][图形]ArcartX-*.jar 放进 libs/

# 2. 构建
./gradlew build

# 产物：build/libs/StarPact-Plugin-1.1.0.jar
```

> `libs/` 里的 jar 是第三方商业插件，**不随源码分发**，需要自行获取。

---

## 架构

```
ChemdahHook    →  只判断「Chemdah 在不在」（零 Chemdah 引用，纯 Class.forName）
ChemdahReader  →  真正读 Chemdah（import Chemdah，只在确认存在后反射加载）
HudService     →  决定用 AX HUD 还是降级 BossBar
AxHook         →  反射调 ArcartX，失败只降级不崩
```

### 必须遵守的两条

**1. `ChemdahHook` 里不能出现任何 Chemdah 类型**（方法签名、字段、泛型都不行）。

Chemdah 未安装时，JVM 在类解析阶段就会抛 `NoClassDefFoundError`，这个错误 **catch 不住**，会直接打断插件启用。

**2. 不要用 Chemdah 的事件类**。

它们继承 `taboolib.platform.type.BukkitProxyEvent`，那个基类不在 Chemdah 的 jar 里（TabooLib 运行时注入），编译期 import 不到。所以刷新用**轮询**（默认 2 秒）。

---

## UI 坐标约定

ArcartX 的 UI 属性值都是脚本表达式，StarPact 的界面全部用 `Screen` 比例布局：

| 方向 | 表达式 |
|---|---|
| 水平坐标 / 宽度 / 字号 | `Screen.getWidth() * 比例` |
| 垂直坐标 / 高度 | `Screen.getHeight() * 比例` |

**混用这两个基准会导致界面在不同屏幕比例（16:9 / 16:10）下变形或重叠。**

---

## 已知限制

- **奖励预览**：Chemdah 的奖励写在 agent 的 Kether 脚本里，没有结构化数据可读。
  面板只能显示 `addon.reward` 配置的奖励，否则显示「该任务没有配置奖励预览」。
- **任务分类**：依赖 Chemdah 任务的 `meta.type` 字段匹配关键字。
  未匹配到的任务默认归入「时序」。

---

## 许可

保留所有权利。转载/修改前请联系作者。
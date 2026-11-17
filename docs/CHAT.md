# 对话系统（Chemdah → AX 自绘界面）

StarPact 把 **Chemdah 的箱子对话**替换成自己的 AX 界面：玩家永远看不到原版箱子，
看到的是仿原神风格的「底部台词条 + 右侧选项」。

---

## 为什么不用「盖箱子」的做法

最直接的想法是让 AX 的 `ui.match` 匹配箱子标题把它盖住 —— 视觉上确实做到了，
**但点击会失效**，原因如下：

1. Chemdah 的箱子对话用的是**虚拟库存**（`RemoteInventory`），
   点击入口是 `handleClick(Packet)` —— **只认客户端发来的原始 NMS 包**。
2. AX 面板盖上去之后，玩家的点击被 AX 吃掉，客户端根本不会把包发给箱子。
3. 服务端再怎么合成 `InventoryClickEvent`、甚至 Chemdah 自己的
   `VirtualInventoryInteractEvent`，**都没有监听者**（实测点了完全没反应）。

> `VirtualInventoryInteractEvent` 只是数据载体，不是派发点。别在这上面浪费时间。

**所以现在完全绕开箱子**：数据从内存读，点击直接改会话状态。

---

## 整体流程

```
Chemdah 对话开始（箱子被打开）
        │
        │  轮询（每 10 tick）
        ▼
ChemdahChat.snapshot()            读 Session：
        │                         · getTitle()                  NPC 名
        │                         · getNpcSide()                台词
        │                         · getPlayerReplyForDisplay()  选项
        │
        │  packet: starpact:chat_panel / sync
        ▼
AX 面板（ui/chat-panel.yml）    ← 箱子被 match 完全遮盖
        │
        │  玩家点击 → packet: starpact:chat_pick:<序号>
        ▼
ChatBridge.onPick()
        │  1. 播放点击音效
        │  2. ChemdahChat.pick()
        │       ├─ 解析 action 里的 "goto xxx"
        │       ├─ Session.goto(conversation, vars)   跳转
        │       └─ 回填新节点的台词与选项
        ▼
轮询检测到内容变化 → 刷新面板
```

---

## 关键实现（都踩过坑）

### 1. 读数：`Session` 的 getter 都是 public

```java
session.getTitle()                     // NPC 名
session.getNpcSide()                   // 台词 List<String>
session.getPlayerReplyForDisplay()     // 选项 List<PlayerReply>
```

全部走反射，**不 import 任何 Chemdah 类**（Chemdah 缺席时不能拖垮插件）。

### 2. 跳转：`goto` 只改指针，不填数据

```java
Session.goto(Conversation next, Map vars)
```

反编译可以看出这个方法**只改会话指向 + 写调试日志就 return 了**。
跳转之后 `npcSide` / `playerReplyForDisplay` 仍然是旧值，必须自己回填：

```java
session.setNpcSide(conversation.getNpcSide());
session.setPlayerReplyForDisplay(conversation.getPlayerSide().getReply());
```

### 3. **不要**调 `Theme.onDisplay()`

它是「打开界面」的入口，内部会调 `createDisplay` 重建界面。
参数传错时 Chemdah 直接把会话判为结束 —— **实测点了选项整个面板消失、会话变空**。

### 4. **不要**用 `PlayerSide.checked()`

跳转后它一直返回空列表，而同一时刻 `PlayerSide.getReply()` 里明明有数据。
直接取 `getReply()`（配置里写死的选项，不经条件过滤）。

> ⚠️ 如果对话配了 `condition` 条件选项，`getReply()` 会把不满足条件的也显示出来。
> 目前还没处理，需要时再补。

### 5. 选项序号不是箱子槽位

UI 发的是 `starpact:chat_pick:0` ~ `:5`（**选项序号**），
早期版本发的是箱子槽位（`12`~`21`），换成方案 A 后已统一成序号。

---

## UI 结构（`chat-panel.yml`）

```yaml
ui:
  match:
    - "StarPactChat"        # 遮盖箱子（配合 Chemdah 的 theme-chest.ui.title）
  hide:                     # 对话时隐藏原版 HUD
    - hotbar
    - chat_panel
    - player_state
    - crosshair
    - scoreboard
  background: false         # 不要整屏压暗
  through: "true"
  escClose: "true"
```

控件：

| 控件 | 作用 |
|---|---|
| `shade` | 底部渐变遮罩（上透明 → 下深色） |
| `name` / `line` / `text` | NPC 名、金色短线、台词 |
| `opt_bg0..5` | 选项按钮背景（圆角胶囊 + hover + 点击） |
| `opt_tx0..5` | 选项文字（**尺寸与按钮完全一致**） |

### 选项两层的对齐（踩过的坑）

`opt_tx` 必须和 `opt_bg` **尺寸、位置完全一致**：

```aria
val.opt_tx0.width  = var.OW
val.opt_tx0.height = var.OH        # 不是 var.FS！
val.opt_tx0.x      = var.OX
val.opt_tx0.y      = var.BASE      # 不要再加 (OH - FS) * 0.5
```

早期文字层比按钮矮（字号高度 16~18 vs 按钮 26），
**中间形成一条死区**：鼠标在边缘能 hover，移到文字上就没反应。

### 前提：Chemdah 的箱子标题要固定

`plugins/Chemdah/core/conversation.yml`：

```yaml
theme-chest:
  ui:
    title: 'StarPactChat'    # 原来是 '正在与 {title} 交流...'
```

AX 的 `ui.match` 是**精确匹配**（除非显式用 `[regex]` 前缀），
动态标题（含 NPC 名）匹配不上。箱子反正被盖住了，玩家看不到这个标题。

---

## 任务结束时停导航

`fail-commands` 同时挂两个事件：

| 场景 | Chemdah 事件 |
|---|---|
| `/chq stop` 取消任务 | **`QuestEvents$Unregistered`** |
| 任务自然失败 / 超时 | `QuestEvents$Fail$Post` |

**「取消」走的是 `Unregistered` 而不是 `Fail`** —— 这点很容易搞错。

### ⚠️ 必须按类名严格过滤

Bukkit 的 `registerEvent` 只按注册的 Class 决定是否调用，
但实测**同一个监听器会收到几乎全部 Chemdah 事件**
（`Accept` / `DataSet` / `PlayerEvents` / 甚至 `PluginReloadEvent`）。

不校验类型的话，「接任务」会被当成「任务失败」执行停导航指令：

```java
private static final Set<String> HANDLED = Set.of(
        "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post",
        "ink.ptms.chemdah.api.event.collect.QuestEvents$Unregistered");

private void handle(Event event) {
    String type = event.getClass().getName();
    if (!HANDLED.contains(type)) {
        return;
    }
    ...
}
```

`QuestRewardListener`（完成事件）同理。

---

## 配置示例

`quests/主线-序章.yml`：

```yaml
main_prologue_01_platform:
  name: '&b醒来'
  nav-pos: [65.5, 54, 213.5]
  track-commands:
    - 'startrail nav {player} {x} {y} {z} 江小满'
  untrack-commands:
    - 'startrail nav stop {player}'
  complete-commands:
    - 'startrail nav stop {player}'
  fail-commands:
    - 'startrail nav stop {player}'
```

`complete-commands` / `fail-commands` 不写时，自动回落到 `untrack-commands`。

---

## 启动补载

Chemdah 的对话是**延后加载**的：服务端刚启动时 `ConversationManager` 里还是空的，
玩家进服右键 NPC 完全没反应，必须手动 `/ch reload` 一次。

StarPact 启动 10 秒后自动补一次（**只在没有玩家在线时执行**）：

```java
if (!Bukkit.getOnlinePlayers().isEmpty()) {
    return;    // 有玩家在，交给 Chemdah 自己处理
}
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chemdah reload");
```

---

## 调试

`config.yml` 打开 `debug: true` 后会输出：

```
[对话] 点击选项 #0 (玩家名) 结果=true
[对话] 执行 action: goto sp_conv_wake_who
[对话] 已跳转到: sp_conv_wake_who
[对话] 会话已刷新，选项数=1
```

排查顺序：**先确认 `[对话] 已跳转到`**，再看 `选项数` ——
数据没回填就是 `getReply()` 那一步的问题。

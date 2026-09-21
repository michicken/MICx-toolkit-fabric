# 面板文案改写对照表

生成自 `ModulePanelRegistry` 与各 `*ConfigScreen` 里的 `UiText` 双语义文案。

游戏内面板右上角的 **文案 新版 / 旧版** 按钮切换的就是这两列：
默认显示「改写后」，按一下显示「改写前」。

- 改写条数：**471**
- 原样保留条数：45
- 合计：516

## 一、改写过的文案

| 文件 | 改写前 | 改写后 |
| --- | --- | --- |
| `AimLeadConfigScreen.java` | AimLead | 瞄准提前量 |
| `AimLeadConfigScreen.java` | 瞄准提前量 · server movement samples | AimLead · 服务端移动采样 |
| `AimLeadConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `AimLeadConfigScreen.java` | Enable | 启用模块 |
| `AimLeadConfigScreen.java` | 只使用服务端 movement packet 轨迹，不伪造 serverPos。 | 只用服务端 movement packet 记下来的轨迹做预测。 |
| `AimLeadConfigScreen.java` | PING (LIVE) / 延迟 | 实时延迟 |
| `AimLeadConfigScreen.java` | Ping | 当前延迟 |
| `AimLeadConfigScreen.java` | Game RTT | 游戏 RTT |
| `AimLeadConfigScreen.java` | Auto Ping | 自动测量延迟 |
| `AimLeadConfigScreen.java` | Game RTT probe | 用游戏 RTT 探测 |
| `AimLeadConfigScreen.java` | Zombies Only | 仅 Zombies 生效 |
| `AimLeadConfigScreen.java` | DIAG / 预测诊断 | 预测诊断 |
| `AimLeadConfigScreen.java` | Turn Rate | 转向速度 |
| `AimLeadConfigScreen.java` | Lead Error | 提前量误差 |
| `AimLeadConfigScreen.java` | DISPLAY / 显示 | 显示 |
| `AimLeadConfigScreen.java` | Ghost Box | 幽灵框 |
| `AimLeadConfigScreen.java` | Fire Dot | 开火点 |
| `AimLeadConfigScreen.java` | Link Line | 连线 |
| `AimLeadConfigScreen.java` | Server Shadow | 服务端影子 |
| `AimLeadConfigScreen.java` | VALUES / 数值 | 数值 |
| `AimLeadConfigScreen.java` | Min Dist | 最小距离 |
| `AimLeadConfigScreen.java` | Max Ghosts | 幽灵框上限 |
| `AimLeadConfigScreen.java` | Extra Lead | 额外提前量 |
| `AimLeadConfigScreen.java` | Manual Ping | 手动延迟 |
| `AimLeadConfigScreen.java` | Forge 语义：12 个样本、中位数速度、8 m/s 尖峰过滤、15 m/s 隐藏；预测时间 50–1200 ms，幽灵提前不足 0.3 格时不显示。 | 算法沿用 Forge 版：取 12 个样本的中位数速度，滤掉 8 m/s 的异常尖峰，超过 15 m/s 就不画幽灵框。预测时间可填 50–1200 ms；提前量不到 0.3 格时也不画。 |
| `AimLeadConfigScreen.java` | 碰撞夹取只限制预测位移；实体消失、世界切换或禁用时清空轨迹。 | 预测出来的位移会被方块碰撞夹一下。目标消失、换世界或关掉模块时会清空轨迹。 |
| `AimbotConfigScreen.java` | Aimbot | 自动瞄准 |
| `AimbotConfigScreen.java` | 目标筛选 · AimLead 攻击点 · 三态瞄准 · 鼠标策略 | Aimbot · 目标筛选 / 攻击点 / 鼠标策略 |
| `AimbotConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `AimbotConfigScreen.java` | 工作边界：模块只写客户端视角，不直接构造攻击包；目标仍必须通过 AimLead 轨迹、视野/穿透和实体过滤。 | 这个模块只改客户端视角，不会自己构造攻击包；目标仍然要过 AimLead 轨迹、视野/穿透和实体过滤这几道检查。 |
| `AimbotConfigScreen.java` | AIM STYLE / 瞄准模式 | 瞄准模式 |
| `AimbotConfigScreen.java` | TRIGGER / 触发 | 触发条件 |
| `AimbotConfigScreen.java` | TARGET / 目标筛选 | 目标筛选 |
| `AimbotConfigScreen.java` | AIM / 瞄准参数 | 瞄准参数 |
| `AimbotConfigScreen.java` | HITBOX / 命中口径 | 命中口径 |
| `AimbotConfigScreen.java` | PITCH POLICY / 垂直视角规则 | 垂直视角规则 |
| `AimbotConfigScreen.java` | HUMANIZE / 拟人参数 | 拟人化参数 |
| `AimbotConfigScreen.java` | BRUTE SWEEP / 暴力扫射 | 暴力扫射 |
| `AimbotConfigScreen.java` | JOYSTICK / 手动推偏 | 手动推偏 |
| `AimbotConfigScreen.java` | HUD / 显示与调试 | HUD 显示与调试 |
| `AimbotConfigScreen.java` | Aimbot HUD 是独立显示模块，不改变瞄准逻辑；需要检查目标点时可临时打开 Debug Line，确认后建议关闭。 | Aimbot HUD 只负责显示，不影响瞄准逻辑。想确认瞄的点对不对时可以临时打开 Debug Line，看完记得关掉。 |
| `AsrConfigScreen.java` | ASR | 语音输入 |
| `AsrConfigScreen.java` | 语音输入 · StepFun Realtime | ASR · StepFun Realtime |
| `AsrConfigScreen.java` | API key | API 密钥 |
| `AsrConfigScreen.java` | CREDENTIALS / 凭据 | 登录凭据 |
| `AsrConfigScreen.java` | API key | API 密钥 |
| `AsrConfigScreen.java` | 输入框默认掩码显示；保存时不会把完整 key 写入日志、聊天或错误提示。 | 输入框里的密钥默认打码显示，保存时也不会把完整密钥写进日志、聊天或报错提示里。 |
| `AsrConfigScreen.java` | PUSH TO TALK / 按键说话 | 按键说话 |
| `AsrConfigScreen.java` | PTT key | 按键说话键 |
| `AsrConfigScreen.java` | 默认按键为 V。模型和 WebSocket endpoint 当前由模块固定，不在面板中伪装为可编辑设置。 | 默认按键是 V。模型和 WebSocket 地址目前固定在模块里，面板上不会摆一个只能看不能改的假输入框。 |
| `AsrConfigScreen.java` | BOUNDARY / 边界 | 使用限制 |
| `AsrConfigScreen.java` | 识别结果按 Enter 发送，Escape 取消；连接、录音和最终确认超时沿用当前模块实现。 | 识别结果按 Enter 发送，按 Escape 取消。连接、录音和确认的超时时间沿用模块里的固定值。 |
| `AutoTextConfigScreen.java` | AutoText | 快捷文本 |
| `AutoTextConfigScreen.java` | 快捷文本 · 绑定列表 | AutoText · 绑定列表 |
| `AutoTextConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `AutoTextConfigScreen.java` | Enable | 启用模块 |
| `AutoTextConfigScreen.java` | BINDINGS / 快捷文本 | 快捷文本绑定 |
| `AutoTextConfigScreen.java` | 文本按原配置逐项保存到 AutoText；空文本或未绑定行不会发送。 | 每行文本各自保存；空文本或者没绑键的行不会被发出去。 |
| `ChamsConfigScreen.java` | Chams | 模型透视 |
| `ChamsConfigScreen.java` | 原贴图模型透墙 · 仅遮挡目标 | Chams · 只画被遮挡的目标 |
| `ChamsConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ChamsConfigScreen.java` | Enable | 启用模块 |
| `ChamsConfigScreen.java` | RANGE / 范围 | 生效范围 |
| `ChamsConfigScreen.java` | Range | 生效距离 |
| `ChamsConfigScreen.java` | 范围 8–128 格。可见实体保持原版渲染；只有玩家眼睛到实体中心的方块射线命中时才启用模型 Chams。 | 生效距离 8–128 格。看得见的怪仍然按原版渲染；只有从你的眼睛到怪物中心被方块挡住时，才换成模型透视。 |
| `ChamsConfigScreen.java` | BOUNDARY / 边界 | 使用限制 |
| `ChamsConfigScreen.java` | 不复用 ESP 的线框管线，不绘制纯色方框；模块关闭、断开或切世界时会清空遮挡缓存。 | 它画的是模型而不是方框，和 ESP 的线框各管各的。关闭模块、断线或换世界时会清掉已经记录的遮挡。 |
| `ChatCleanerConfigScreen.java` | ChatCleaner | 聊天清理 |
| `ChatCleanerConfigScreen.java` | 聊天折叠 · 当前规则 | ChatCleaner · 重复消息折叠 |
| `ChatCleanerConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ChatCleanerConfigScreen.java` | Enable | 启用模块 |
| `ChatCleanerConfigScreen.java` | SUPPORTED RULE / 已支持规则 | 已支持的规则 |
| `ChatTranslateConfigScreen.java` | ChatTranslate | 聊天翻译 |
| `ChatTranslateConfigScreen.java` | 聊天翻译 · DeepSeek | ChatTranslate · DeepSeek |
| `ChatTranslateConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ChatTranslateConfigScreen.java` | Enable | 启用模块 |
| `ChatTranslateConfigScreen.java` | Status:  | 状态： |
| `ChatTranslateConfigScreen.java` | PROVIDER / 翻译服务 | 翻译服务 |
| `ChatTranslateConfigScreen.java` | API KEY / 密钥 | API 密钥 |
| `ChatTranslateConfigScreen.java` | API key | API 密钥 |
| `ChatTranslateConfigScreen.java` | 在此填写 DeepSeek API key（仅保存在本地配置文件）。留空时回退环境变量 MICX_DEEPSEEK_API_KEY。 | 在这里填 DeepSeek 的 API key，只存在本地配置文件里。留空时会去读环境变量 MICX_DEEPSEEK_API_KEY。 |
| `ChatTranslateConfigScreen.java` | TARGET LANGUAGE / 出站目标语言 | 出站翻译目标语言 |
| `ChatTranslateConfigScreen.java` | Outgoing target | 出站目标语言 |
| `ChatTranslateConfigScreen.java` | 点击按钮循环切换出站语言（1.8.9 同款列表）；出站只拦截中文普通消息。 | 点按钮循环切换要翻成的目标语言（和 1.8.9 同一份列表）。只有中文的普通聊天会被拦下来翻译。 |
| `ChatTranslateConfigScreen.java` | DELIVERY / 发送边界 | 发送限制 |
| `ChatTranslateConfigScreen.java` | Timeout (ms) | 超时时间 ms |
| `ChatTranslateConfigScreen.java` | 范围沿用原 1.8.9：5000–60000 ms，默认 25000 ms。 | 超时范围和原 1.8.9 一致：5000–60000 毫秒，默认 25000。 |
| `ChatTranslateConfigScreen.java` | 只处理中文普通消息；/ 命令、纯英文和超长文本不拦截。入站翻译：点击聊天行尾 [T] 本地翻译成简体中文显示。 | 只处理中文普通消息；/ 开头的命令、纯英文和超长文本都不会被拦。收到的外语消息：点聊天行尾的 [T]，在本地翻成简体中文显示。 |
| `DpsCounterConfigScreen.java` | DPSCounter | DPS 计数 |
| `DpsCounterConfigScreen.java` | DPS 计数 · 一秒滚动窗口 | DPSCounter · 一秒滚动窗口 |
| `DpsCounterConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `DpsCounterConfigScreen.java` | Enable | 启用模块 |
| `DpsCounterConfigScreen.java` | 跟踪实体生命值和吸收值的下降量。 | 按血量（含吸收盾）掉多少来算伤害数字。 |
| `DpsCounterConfigScreen.java` | Overlay | 显示方式 |
| `DpsCounterConfigScreen.java` | 右上角显示最近一秒的伤害总量。 | 在右上角显示最近一秒你打出的伤害总量。 |
| `DpsCounterConfigScreen.java` | BOUNDARY / 边界 | 使用限制 |
| `DpsCounterConfigScreen.java` | 世界切换会清空实体快照和滚动窗口；实体消失后不保留旧 ID。数值完全在客户端计算。 | 换世界会清空记录重新开始，实体消失后不会留着旧数据。伤害完全在本地计算，不发包。 |
| `DpsCounterConfigScreen.java` | 当前 HUD 位置沿用 Forge 的右上角布局；HUD Layout 编辑器尚未迁移。 | HUD 目前固定在右上角，和 Forge 版布局一致。 |
| `EcoRatePanelScreen.java` | EcoRate | 经济增速 |
| `EcoRatePanelScreen.java` | 经济速率闪烁 · 每 2 分钟纯增长 | EcoRate · 每 2 分钟净增长 |
| `EcoRatePanelScreen.java` | MODULE / 模块 | 模块开关 |
| `EcoRatePanelScreen.java` | Enable | 启用模块 |
| `EcoRatePanelScreen.java` | 右侧经济表周期性把金币切为每2分钟纯增长速率（绿字）。 | 右侧经济表会定期把金币换成「每 2 分钟净增多少」，绿色显示。 |
| `EcoRatePanelScreen.java` | FLASH / 闪烁节奏 | 闪烁节奏 |
| `EcoRatePanelScreen.java` | Flash Interval (s) | 闪烁间隔（秒） |
| `EcoRatePanelScreen.java` | Flash Duration (s) | 闪烁时长（秒） |
| `EcoRatePanelScreen.java` | 速率 = 过去2分钟纯增长（买装备不影响）：自己按聊天 +Gold 事件，队友按记分板正增量；数值每10秒刷新一次。模块关闭时后台采集继续，中途开启立即有数据。 | 增速 = 过去 2 分钟的净增长（买装备不计入）：你自己的按聊天里的 +Gold 事件算，队友的按记分板正增量算；数值每 10 秒刷新一次。模块关着时后台照样在采集，中途打开立刻就有数据。 |
| `EspConfigScreen.java` | ESP | 线框透视 |
| `EspConfigScreen.java` | 线框透视 · 26.2 submit pipeline | ESP · 提交线框绘制 |
| `EspConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `EspConfigScreen.java` | Enable | 启用模块 |
| `EspConfigScreen.java` | 提交非玩家实体线框；不会修改全局 OpenGL 状态。 | 打开后就提交非玩家实体的线框。 |
| `EspConfigScreen.java` | Active | 实际生效 |
| `EspConfigScreen.java` | 关闭时保留模块配置但不提交线框。 | 关掉只是停止提交线框，模块配置保留，随时可以再开回来。 |
| `EspConfigScreen.java` | RANGE / 范围 | 生效范围 |
| `EspConfigScreen.java` | Range | 生效距离 |
| `EspConfigScreen.java` | Opacity % | 不透明度 % |
| `EspConfigScreen.java` | 范围 8–256 格；透明度 5–100%。普通实体使用默认红色，Golem/Wither 使用独立颜色。 | 生效距离 8–256 格，不透明度 5–100%。普通实体用默认红色，Golem 和 Wither 各有自己的颜色。 |
| `EspConfigScreen.java` | AUTO GATE / 自动门控 | 自动门控 |
| `EspConfigScreen.java` | Auto Gate | 自动门控 |
| `EspConfigScreen.java` | Gate Round | 起效回合 |
| `EspConfigScreen.java` | Show Under | 剩余怪少于 |
| `EspConfigScreen.java` | Gate Round 10–110；Show Under 1–100。TOO/Giant 优先目标和完整 TeamSync white target 仍待后续接入。 | 起效回合可填 10–110，剩余怪阈值可填 1–100。TOO / Giant 优先目标和完整的 TeamSync 白名单目标还没接进来。 |
| `KeyboardClickerConfigScreen.java` | KeyboardClicker | 自动切枪 |
| `KeyboardClickerConfigScreen.java` | 键盘连点 · 原生按键队列 | KeyboardClicker · 原生按键队列 |
| `KeyboardClickerConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `KeyboardClickerConfigScreen.java` | Enable | 启用模块 |
| `KeyboardClickerConfigScreen.java` | Right-click gate | 右键门控 |
| `KeyboardClickerConfigScreen.java` | MODES / 模式 | 模式 |
| `KeyboardClickerConfigScreen.java` | Current | 当前状态 |
| `KeyboardClickerConfigScreen.java` | Interval (ms) | 间隔 ms |
| `KeyboardClickerConfigScreen.java` | Mode  | 模式  |
| `KeyboardClickerConfigScreen.java` | 可同时勾选多个组合；V 只在已勾选模式间循环，` 切换连点开关。至少保留 1 项，范围 40–100 ms。 | 可以同时勾选多个组合；V 键只在勾选到的模式之间循环，` 键切换连点开关。至少要留 1 项，间隔范围 40–100 毫秒。 |
| `KeyboardClickerConfigScreen.java` | JAM PROTECT / 防卡弹 | 防卡弹 |
| `KeyboardClickerConfigScreen.java` | KEYS / 快捷键 | 快捷键 |
| `KeyboardClickerConfigScreen.java` | Toggle | 开关快捷键 |
| `KeyboardClickerConfigScreen.java` | Mode | 模式 |
| `KeyboardClickerConfigScreen.java` | 按 1 可暂停，按 2/3/4 可恢复原版模式；所有操作受世界与 Screen 状态保护。 | 按 1 暂停，按 2 / 3 / 4 恢复成原版模式。这些按键只在游戏内、且没开界面时才会响应。 |
| `MicxPanelScreen.java` | 此子分区仅保留原 1.8.9 入口，Fabric 运行逻辑尚未迁移。 | 这个分区只保留了原 1.8.9 的入口，Fabric 上的运行逻辑还没迁移过来。 |
| `MicxPanelScreen.java` | Enable | 启用模块 |
| `MicxPanelScreen.java` | 切换 ZombiesAssist 父模块；Display / Alerts / Auto 不是独立模块。 | 这里切的是 ZombiesAssist 父模块；Display / Alerts / Auto 是分区，不是独立模块。 |
| `MicxPanelScreen.java` | Enable | 启用模块 |
| `MicxPanelScreen.java` | Configure > | 打开配置 › |
| `MicxPanelScreen.java` | No extra settings | 没有额外设置项 |
| `ModulePanelRegistry.java` | Zombies 核心 | 僵尸模式核心 |
| `ModulePanelRegistry.java` | 战斗 · 视觉 | 视觉 · 透视与标记 |
| `ModulePanelRegistry.java` | 战斗 · 操作 | 操作 · 瞄准与点击 |
| `ModulePanelRegistry.java` | 信息面板 | 信息面板 · HUD |
| `ModulePanelRegistry.java` | 视觉 · 输入 | 杂项 · 视角与输入 |
| `ModulePanelRegistry.java` | 调试 | 调试 · 排错 |
| `ModulePanelRegistry.java` | 剑格挡 | 剑格挡动画 |
| `ModulePanelRegistry.java` | 客户端视觉格挡动画，不提供服务端伤害减免。 | 客户端本地播放的剑格挡动画，只是看起来在格挡；它不会让服务端少算你受到的伤害。 |
| `ModulePanelRegistry.java` | 当前回合用时 R{n} mm:ss，与 Wave Table 同区块同偏移。 | 显示当前回合已经打了多久（R{回合号} mm:ss）。这段文字和 Wave Table 放在同一区块，共用同一个位置偏移。 |
| `ModulePanelRegistry.java` | 经济速率 | 经济增速 |
| `ModulePanelRegistry.java` | 右侧经济表周期性把金币切为每2分钟纯增长速率（绿字闪烁），2分钟窗口每10秒刷新。 | 在右侧经济表上定期把金币数字换成「每 2 分钟净增多少」，绿色闪一下再切回原样。统计窗口是最近 2 分钟，每 10 秒刷新一次；显示时长与间隔可在面板里调。 |
| `ModulePanelRegistry.java` | LR 指示 | LR 释放清单 |
| `ModulePanelRegistry.java` | AA 物品栏上方的 LR 释放清单：绿=已放 18s 内，红=未放，轮换位置 /micx lr 2/3/4。 | 在 AA 的物品栏上方列一张 LR 释放状态清单：绿色表示 18 秒内已经放过，红色表示还没放。清单位置可以用 /micx lr 2 / 3 / 4 轮换，也可以在面板里改偏移。 |
| `ModulePanelRegistry.java` | LR Indicator | LR 释放清单 |
| `ModulePanelRegistry.java` | LR 释放清单 · 蜂鸣与偏移 | LR Indicator · 蜂鸣与偏移 |
| `ModulePanelRegistry.java` | Beep 蜂鸣 | 提示音 |
| `ModulePanelRegistry.java` | Offset X | 清单水平偏移 |
| `ModulePanelRegistry.java` | Offset Y | 清单垂直偏移 |
| `ModulePanelRegistry.java` | 自动隐藏 | 结算自动隐藏 |
| `ModulePanelRegistry.java` | 对局结算 1 分钟藏 ESP/Chams/Outline/AimLead，R1/离图恢复。 | 对局结算后的 1 分钟内，自动把 ESP、Chams、玩家轮廓和 AimLead 暂时关掉；进入 R1 或离开地图时自动恢复。 |
| `ModulePanelRegistry.java` | 记录玩家自己这条连接的收发封包（出站交互/点格子/聊天/自定义通道，入站开界面/聊天/标题/拉回…），写 config/MICxToolkit/logs/ 下的日志文件；快捷键插标记线，用来查买弹到底走哪条通道 | 排错用：记录你自己这条连接收发的封包。出站有交互、点格子、聊天、自定义通道，入站有开界面、聊天、标题、拉回等，写到 config/MICxToolkit/logs/ 下的日志文件；用快捷键可以插一条标记线，方便查「买弹到底走哪条通道」。 |
| `ModulePanelRegistry.java` | 僵尸淡化 | 近身怪物淡化 |
| `ModulePanelRegistry.java` | 近距离敌对生物半透明淡化。 | 玩家周围的敌对生物在近距离内淡化成半透明，避免贴脸时挡住视野。 |
| `ModulePanelRegistry.java` | ZombieFade | 近身怪物淡化 |
| `ModulePanelRegistry.java` | 近距离怪物淡化 | ZombieFade · 近身淡化 |
| `ModulePanelRegistry.java` | Radius 半径 | 淡化半径 |
| `ModulePanelRegistry.java` | 屏蔽所有粒子效果（爆炸/破坏等一切），匹配 1.8.9 的完全关闭语义。 | 屏蔽所有粒子效果，爆炸、方块破坏等产生的粒子一并关掉，语义与 1.8.9 的「完全关闭」一致。 |
| `ModulePanelRegistry.java` | 队友倒地睡在附近时自动发救援交互包：每人每次倒地一包；冷却按目标分开——刚点过 A 不影响立刻点 B，interval 只挡对同一只的连点，每 tick 最多一包。 | 队友倒地睡在附近时，自动替他发一个救援交互包。每人每次倒地只发一包；冷却按目标分开算——刚点过 A 不影响马上点 B，Interval 只限制对同一个人的连点，每 tick 最多发一包。 |
| `ModulePanelRegistry.java` | ReviveAura | 自动救人 |
| `ModulePanelRegistry.java` | 自动救援发包 | ReviveAura · 自动救援发包 |
| `ModulePanelRegistry.java` | 残怪连线 | 剩余怪连线 |
| `ModulePanelRegistry.java` | 回合剩余怪 ≤N 时，准心向每只残怪拉黄色指示线（计分板权威计数）。 | 一个回合快清完时，从准心向每一只还没死的怪拉一条黄色指示线，帮你找剩下的怪。剩余数量按计分板的权威计数。 |
| `ModulePanelRegistry.java` | LastMobs | 剩余怪连线 |
| `ModulePanelRegistry.java` | 剩余怪牵线 | LastMobs · 剩余怪牵线 |
| `ModulePanelRegistry.java` | Max Count 阈值 | 触发阈值 |
| `ModulePanelRegistry.java` | Alpha % | 线条不透明度 % |
| `ModulePanelRegistry.java` | AA 已知刷怪点固定灰色光柱：11 地面点 + 4 UFO 放怪口（纯预设）。 | 在 AA 已知的刷怪点上画固定的灰色光柱：11 个地面刷怪点加 4 个 UFO 放怪口。位置是预设的，不随对局变化。 |
| `ModulePanelRegistry.java` | SpawnMarker | 刷怪点标记 |
| `ModulePanelRegistry.java` | 刷怪点光柱 | SpawnMarker · 刷怪点光柱 |
| `ModulePanelRegistry.java` | Alpha | 不透明度 |
| `ModulePanelRegistry.java` | 史莱姆预告 | 史莱姆波预告 |
| `ModulePanelRegistry.java` | 刷史莱姆/岩浆波次前在 12 个固定点显示绿 X（墨绿→亮绿→隐藏），Force 模式常显。 | 在会刷史莱姆或岩浆怪的波次到来之前，把 12 个固定点标成绿色 X（墨绿→亮绿→刷出后隐藏）。开启 Force 模式则一直显示。 |
| `ModulePanelRegistry.java` | SlimeForecast | 史莱姆波预告 |
| `ModulePanelRegistry.java` | 史莱姆波次预告 | SlimeForecast · 史莱姆波次预告 |
| `ModulePanelRegistry.java` | Alpha | 不透明度 |
| `ModulePanelRegistry.java` | 铁傀儡 5 个固定出生点贴地灰 X，穿墙可见。 | 在铁傀儡的 5 个固定出生点贴地画灰色 X，穿墙可见。 |
| `ModulePanelRegistry.java` | GolemMarker | 铁傀儡标记 |
| `ModulePanelRegistry.java` | 铁傀儡出生点 X | GolemMarker · 铁傀儡出生点 X |
| `ModulePanelRegistry.java` | Alpha | 不透明度 |
| `ModulePanelRegistry.java` | 按住放大镜键在屏幕中央 16:9 放大 2~8x，滚轮调倍率，松开恢复，灵敏度按 k/zoom 缩放。 | 按住放大镜键，屏幕中央按 16:9 放大 2~8 倍；滚轮调倍率，松开按键恢复。鼠标灵敏度会按倍率同步缩放。 |
| `ModulePanelRegistry.java` | 快捷视角 | 临时视角 |
| `ModulePanelRegistry.java` | 按住绑定键切到背后/正面视角，松开恢复第一人称；正面视角支持俯仰镜像（需先绑定按键）。 | 按住绑定键临时切到背后视角或正面视角，松开自动回到第一人称。正面视角支持俯仰镜像。需要先绑定按键。 |
| `ModulePanelRegistry.java` | 防松Shift | 救援防误松 |
| `ModulePanelRegistry.java` | 救援途中防误松 Shift（Type B）：不自动重按、不发包，默认关闭；低血/救起自动放行。 | 救援队友的过程中不小心松开 Shift 也不会中断（Type B 方案）：不自动重按、不发任何包，默认关闭。血量过低或刚被救起来时会自动放行。 |
| `ModulePanelRegistry.java` | AntiReshift | 救援防误松 |
| `ModulePanelRegistry.java` | 救援防误松 Shift | AntiReshift · 救援防误松 Shift |
| `ModulePanelRegistry.java` | 吸附 | 视角吸附 |
| `ModulePanelRegistry.java` | 按住右键时准心轻微吸向目标爆头点，手瞄快甩自动退场；仅 Zombies 生效。 | 按住右键时准心轻微吸向目标的爆头点；手瞄快速甩动时自动退场，只做辅助不跟你抢准心。只在 Zombies 对局里生效。 |
| `ModulePanelRegistry.java` | Magnet | 视角吸附 |
| `ModulePanelRegistry.java` | 视角吸附 | Magnet · 视角吸附 |
| `ModulePanelRegistry.java` | Hitbox Only | 仅命中框减速 |
| `ModulePanelRegistry.java` | Zombies Only | 仅 Zombies 生效 |
| `ModulePanelRegistry.java` | Radius 度 | 吸附角度范围 |
| `ModulePanelRegistry.java` | Headshot 停拉 | 爆头点停拉 |
| `ModulePanelRegistry.java` | 全亮 | 全屏亮度 |
| `ModulePanelRegistry.java` | 强制 gamma 全亮，Forge Fullbright 的 Fabric 等价实现；关闭时还原。 | 强制把 gamma 拉满，洞里和夜里不再漆黑。这是 Forge 版 Fullbright 的 Fabric 等价实现，关掉后会还原原来的亮度设置。 |
| `ModulePanelRegistry.java` | 波次、僵尸剩余、Power-up、警报、自动行为和 Alien Arcadium 状态 HUD。 | Zombies 比赛用的主 HUD 与播报总开关：波次与剩余怪数、Power-up、危险警报、自动行为，以及 Alien Arcadium 的实时状态。下面三个子分区分别管显示、警报、记分板与消息。 |
| `ModulePanelRegistry.java` | 刷怪窗口 | 刷怪窗口计数 |
| `ModulePanelRegistry.java` | 11 窗按出生点统计的刷怪量（P1/P2/P3/P4/P5/ULT/ALT/CL/CR/BL/BR），波次内累积、跨波次清，HUD 可拖动。TOO 生成时该窗显示 T 全红，顶部 TOO IN 持续 3 波。 | 按出生点统计每个窗口刷了多少怪：P1 / P2 / P3 / P4 / P5 / ULT / ALT / CL / CR / BL / BR 共 11 个窗口。计数在波次内累加、跨波次清零，HUD 位置可以拖动。TOO 从某个窗口生成时该窗标红显示 T，顶部还会列出 TOO IN（持续 3 波）。 |
| `ModulePanelRegistry.java` | WindowSpawns | 刷怪窗口计数 |
| `ModulePanelRegistry.java` | 刷怪窗口 · TOO 警报 | WindowSpawns · TOO 警报 |
| `ModulePanelRegistry.java` |   /pc 队内播报 | /pc 队内播报 |
| `ModulePanelRegistry.java` |   本地叮声 | 本地叮声 |
| `ModulePanelRegistry.java` | 抽到 The Puncher 时锁定 Lucky Chest 领取区右键 10.5 秒，防误领；领到其他物品自动解除。 | 从 Lucky Chest 抽到 The Puncher 之后的 10.5 秒内，锁住领取区的右键，避免手快把别的物品也领了；一旦领到其他物品就立刻解除锁定。 |
| `ModulePanelRegistry.java` | AntiAXE | 防误领 Puncher |
| `ModulePanelRegistry.java` | Puncher 领取保护 | AntiAXE · Puncher 领取保护 |
| `ModulePanelRegistry.java` | HUD Offset X | 提示水平偏移 |
| `ModulePanelRegistry.java` | HUD Offset Y | 提示垂直偏移 |
| `ModulePanelRegistry.java` | 按住右键轮换武器槽并发包重置武器状态跳过换弹动画；与 AutoSwitch 互斥，默认关闭。 | 按住右键时自动轮换武器槽，并给服务端发一个重置武器状态的包，跳过换弹动作。和 AutoSwitch 互斥，所以默认关闭。 |
| `ModulePanelRegistry.java` | NoReload | 免换弹 |
| `ModulePanelRegistry.java` | 免换弹 | NoReload · 免换弹 |
| `ModulePanelRegistry.java` | Delay ms | 轮换间隔 ms |
| `ModulePanelRegistry.java` | 槽 2 参与轮换 | 第 2 格参与轮换 |
| `ModulePanelRegistry.java` | 槽 3 参与轮换 | 第 3 格参与轮换 |
| `ModulePanelRegistry.java` | 槽 4 参与轮换 | 第 4 格参与轮换 |
| `ModulePanelRegistry.java` | 瞄准辅助 | 自动瞄准 |
| `ModulePanelRegistry.java` | 从 1.8.9 迁移的目标筛选、优先级、AimLead 头部点、鼠标接管与 Hold-Lock；默认关闭。 | 从 1.8.9 版本迁移过来的自动瞄准：按选怪策略（忽略 / 清场 / 威胁 / 最近 / 粘性）挑目标，用 AimLead 的头部点瞄准，直接接管鼠标并支持 Hold-Lock 锁死目标。默认关闭。 |
| `ModulePanelRegistry.java` | Aimbot HUD | Aimbot 状态组 |
| `ModulePanelRegistry.java` | 独立显示 TOO/Golem/Slime 忽略、Clown/Giant/Baby 优先和 Closest 状态，并处理分组快捷键。 | 单独显示 Aimbot 的分组状态：TOO / Golem / Slime 的忽略，Clown / Giant / Baby 的优先，以及 Closest。分组快捷键也在这里处理。 |
| `ModulePanelRegistry.java` | 按服务端 movement packet 轨迹预判目标位置，标出开火提前点。 | 按服务端 movement packet 记录的轨迹预判目标接下来会走到哪里，并标出应该开火的提前点。 |
| `ModulePanelRegistry.java` | 透过墙壁提交非玩家实体方框轮廓，带范围、透明度和自动门控。 | 隔墙显示非玩家实体的方框轮廓，可以设作用范围、透明度和自动门控。 |
| `ModulePanelRegistry.java` | 以原贴图模型穿过墙壁显示被方块遮挡的目标；独立于 ESP 线框。 | 用原贴图模型把被方块挡住的目标画出来，穿墙可见；和 ESP 的线框是两套独立开关。 |
| `ModulePanelRegistry.java` | 使用 26.2 原生 outline phase 的玩家绿色轮廓；厚度由客户端原生管线控制。 | 给玩家套一圈绿色轮廓，用的是 26.2 原生 outline 阶段绘制；线条粗细由客户端原生管线决定。 |
| `ModulePanelRegistry.java` | 按住时真实模拟快速右键（默认 20 CPS，每 tick 一发；面板/指令可调，与 AimLead 单引擎互让） | 按住时真实模拟快速右键，默认 20 CPS、每 tick 最多发一发；速率可以在面板或指令里调。和 Aimbot 共用同一套引擎，两边同时开会自动互让，不会叠成两倍速度。 |
| `ModulePanelRegistry.java` | 技能释放 | 技能一键释放 |
| `ModulePanelRegistry.java` | 切槽5 + 原生右键 + 切回，单次激活 | 按一次走一轮：切到第 5 格 → 发一次原生右键 → 切回原来的格子。不是持续连发，只走一遍。 |
| `ModulePanelRegistry.java` | 自动切枪 AutoSwitch | 自动切枪 |
| `ModulePanelRegistry.java` | 键盘触发的自动切枪，多种组合 | 按住右键时自动在武器槽之间轮换，用来跳过换弹和防卡弹，有多种轮换组合可选。同一局里如果检测到金铲子，只在它第一次出现时调整一次键位模式，之后倒地再捡起来也不会反复改。 |
| `ModulePanelRegistry.java` | 实时统计你的每秒伤害（DPS） | 实时统计你自己每秒打出多少伤害（DPS）。 |
| `ModulePanelRegistry.java` | 准心目标血量与吸收盾 HUD；粒子字段保留但尚未接入。 | 显示准心目标的血量和吸收盾；粒子相关的字段还留着，但尚未接入。 |
| `ModulePanelRegistry.java` | 屏幕卡片显示队友血量、吸收盾与距离。 | 用屏幕卡片显示每个队友的血量、吸收盾和距离。 |
| `ModulePanelRegistry.java` | 通过安全 WebSocket 共享队伍位置、目标与手动标记；连接失败不会阻塞客户端。 | 通过一条加密的 WebSocket 把队伍位置、当前目标和手动标记同步给队友。连不上服务器不会卡住客户端。 |
| `ModulePanelRegistry.java` | 隐藏或淡化其他玩家，视野更清爽 | 把其他玩家隐藏或淡化，让视野更干净。 |
| `ModulePanelRegistry.java` | 潜行视觉抬回 1.8 高度（仅视觉，1.5 格缝照样能钻）。 | 把潜行时的视觉高度抬回 1.8 的样子。只是视觉，实际碰撞没变，1.5 格的缝照样能钻过去。 |
| `ModulePanelRegistry.java` | 一键锁定 Sprint，无需长按前进键 | 一键把 Sprint 锁住，不用一直按着前进键。 |
| `ModulePanelRegistry.java` | 合并重复 / 刷屏的聊天信息 | 把重复刷屏的聊天消息合并成一条，减少刷屏。 |
| `ModulePanelRegistry.java` | 点击聊天行即可复制其文本 | 点击任意一行聊天就能复制那一行的文字。 |
| `ModulePanelRegistry.java` | 中文队聊后台翻成 AA 英文，返回后自动发送 | 把你要发的中文队聊在后台翻成 AA 常用的英文，翻译回来后自动帮你发出去。 |
| `ModulePanelRegistry.java` | 聊天行尾 [T] 点击后翻译成简体中文本地显示，原文不动 | 聊天行尾有一个 [T] 按钮，点一下就把那条外语消息翻译成简体中文显示，原文保持不变。 |
| `ModulePanelRegistry.java` | 进服时在聊天里显示 MICx 提示与 /micx 入口 | 进服时在聊天里打一条 MICx 提示，并告诉你 /micx 入口在哪。 |
| `ModulePanelRegistry.java` | 绑定快捷键立即发送预设消息到聊天 | 给预设消息绑一个快捷键，按下就直接发到聊天。 |
| `ModulePanelRegistry.java` | 定时把「<选中的 Rank> pls」发到聊天（默认 3 秒一条，大厅与局内都发；面板可换档位与间隔）；开启后打开书本界面会连响 1 分钟提醒，切到别的应用也不会自动暂停 | 定时把「<当前选中的 Rank> pls」发到聊天，默认 3 秒一条，大厅和局内都发；档位与间隔可以在面板里换。开启后打开书本界面会连响 1 分钟提醒，切到别的应用也不会自动暂停。 |
| `ModulePanelRegistry.java` | 不开界面点到远处商店：客户端射程 3/4.5→5.5 格，面板可扫附近全息并「买一次」最近目标（服务端硬上限实体 6 格/方块 5.5 格，超了丢包） | 不用打开商店界面就能点到远处的商店：客户端交互距离从 3 / 4.5 格放宽到 5.5 格，面板里还能扫描附近的全息并「买一次」离你最近的那个。注意服务端硬上限是实体 6 格 / 方块 5.5 格，超出去的包会被丢弃。 |
| `ModulePanelRegistry.java` | 按住 PTT 录音并将识别结果发送到聊天。 | 按住 PTT 键开始录音，松开后把识别出来的文字发到聊天。 |
| `ModulePanelRegistry.java` | Powerup/BadHeadshot 标记：必出红/预测粉/最后怪绿/线上黄，线框盒+头顶标签。 | 从 ZombiesExplorer 移植过来的标记：用线框盒加头顶标签标出 Powerup 归属怪（必出暗红 / 预测亮红）、本回合的特殊怪（绿）和线上怪（黄）。 |
| `ModulePanelRegistry.java` | ZombiesExplorer | 僵尸标记 |
| `ModulePanelRegistry.java` | 僵尸标记 · ZE 移植 | ZombiesExplorer · ZE 移植 |
| `ModulePanelRegistry.java` | NameTag 标签 | 头顶标签 |
| `ModulePanelRegistry.java` | Predictor 预测数 | 额外预测数 |
| `ModulePanelRegistry.java` | 每波刷怪 pling 提示、最终波 orb、DE/BB 终波前 3-2-1 倒计时（SST 移植）。 | 每波刷怪时给一声 pling 提示，最终波换成 orb 音；DE / BB 的终波前还有 3-2-1 倒计时。整套是从 SST 移植过来的。 |
| `ModulePanelRegistry.java` | WaveSpawnSound | 波次音效 |
| `ModulePanelRegistry.java` | 波次音效 · SST 移植 | WaveSpawnSound · SST 移植 |
| `ModulePanelRegistry.java` | SwingChat | 系统输入法 |
| `ModulePanelRegistry.java` | Forge Display 分区：HUD、Power-up、命中统计、经济和原版 scoreboard。 | 对应 Forge 版的 Display 分区：HUD、Power-up、命中统计、经济和原生 scoreboard。 |
| `ModulePanelRegistry.java` | Forge Alerts 分区：TOO、BLOCK、弹药、LS、FR 和威胁提示。 | 对应 Forge 版的 Alerts 分区：TOO、BLOCK、弹药、LS、FR 和威胁提示。 |
| `ModulePanelRegistry.java` | Forge Auto 分区：回合播报、自动提醒、赛后统计和 noRotate。 | 对应 Forge 版的 Auto 分区：回合播报、自动提醒、赛后统计和 noRotate。 |
| `PacketLogConfigScreen.java` | PacketLog | 封包日志 |
| `PacketLogConfigScreen.java` | 封包日志 · 抓买弹现场 | PacketLog · 抓买弹现场 |
| `PacketLogConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `PacketLogConfigScreen.java` | OPTIONS / 选项 | 选项 |
| `PacketLogConfigScreen.java` | ACTIONS / 操作 | 操作 |
| `PacketLogConfigScreen.java` | RECENT / 最近事件 | 最近事件 |
| `PlayerOutlineEspConfigScreen.java` | PlayerOutlineESP | 玩家轮廓 |
| `PlayerOutlineEspConfigScreen.java` | 玩家轮廓 · native outline phase | PlayerOutlineESP · 26.2 原生 outline |
| `PlayerOutlineEspConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `PlayerOutlineEspConfigScreen.java` | Enable | 启用模块 |
| `PlayerOutlineEspConfigScreen.java` | 为其他玩家设置绿色 outlineColor，交给 26.2 原生 outline phase。 | 把其他玩家设成绿色轮廓，绘制交给 26.2 原生的 outline 阶段。 |
| `PlayerOutlineEspConfigScreen.java` | Active | 实际生效 |
| `PlayerOutlineEspConfigScreen.java` | 范围外玩家不进入 outline state。 | 距离之外的玩家不会进入轮廓绘制状态。 |
| `PlayerOutlineEspConfigScreen.java` | RANGE / 范围 | 生效范围 |
| `PlayerOutlineEspConfigScreen.java` | Range | 生效距离 |
| `PlayerOutlineEspConfigScreen.java` | 范围 8–256 格。原 Forge 的投影厚度 1–5px × 1.20 尚未替换原生固定 outline shader，因此本页不提供假厚度滑块。 | 生效距离 8–256 格，范围外的玩家不进入轮廓绘制。轮廓粗细用的是 26.2 原生 fixed outline，暂时改不了，所以这里没有粗细滑块。 |
| `PlayerVisibilityConfigScreen.java` | PlayerVisibility | 玩家隐身 |
| `PlayerVisibilityConfigScreen.java` | 玩家隐身 · 隐藏或透明 | PlayerVisibility · 隐藏或淡化 |
| `PlayerVisibilityConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `PlayerVisibilityConfigScreen.java` | Enable | 启用模块 |
| `PlayerVisibilityConfigScreen.java` | 渲染钩子只作用于其他玩家；自己和睡觉玩家保留。 | 只对其他玩家生效；你自己和已经倒下的玩家不受影响。 |
| `PlayerVisibilityConfigScreen.java` | Active | 实际生效 |
| `PlayerVisibilityConfigScreen.java` | Hide mode | 隐藏方式 |
| `PlayerVisibilityConfigScreen.java` | 隐藏模式取消附近玩家和其乘坐实体的渲染。 | 隐藏模式会让附近的玩家以及他们骑着的实体完全不渲染。 |
| `PlayerVisibilityConfigScreen.java` | RANGE / 范围 | 生效范围 |
| `PlayerVisibilityConfigScreen.java` | Blocks | 方块遮挡 |
| `PlayerVisibilityConfigScreen.java` | Opacity | 不透明度 |
| `PlayerVisibilityConfigScreen.java` | 范围 0.5–64 格；透明度 0.05–1.0。旧配置中的 rangeSq 会按平方根转换。 | 作用距离 0.5–64 格，淡化的不透明度 0.05–1.0，都可以调。 |
| `PlayerVisibilityConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `PlayerVisibilityConfigScreen.java` | Fabric 透明模式使用实体 extraction state 的 translucent render type，不修改全局 OpenGL 状态。 | 淡化是用半透明渲染实现的，不改动全局渲染状态。 |
| `RankUpToolConfigScreen.java` | RankUpTool | 求 Rank |
| `RankUpToolConfigScreen.java` | 求 Rank · 定时发送 | RankUpTool · 定时发送 |
| `RankUpToolConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `RankUpToolConfigScreen.java` | 任何界面打开时都不发送（含聊天栏输入和本面板）；换世界会重新计时。 | 只要有界面开着就不会发送（包括正在打字和开着这个面板）。换世界会重新计时。 |
| `RankUpToolConfigScreen.java` | BOOK ALERT / 书本警报 | 书本提醒 |
| `RankUpToolConfigScreen.java` | RANK / 档位 | 档位 |
| `RankUpToolConfigScreen.java` | MESSAGE / 话术 | 话术 |
| `RankUpToolConfigScreen.java` | TIMING / 节奏 | 节奏 |
| `RankUpToolConfigScreen.java` | 「立即发一条」按一次只补发一条，并重置自动计时，不会连着再发一条。 | 「立即发一条」按一次只补一条，并重置自动计时，不会连着多发。 |
| `RemoteShopConfigScreen.java` | RemoteShop | 远程商店 |
| `RemoteShopConfigScreen.java` | 远程商店 · 远程买弹 | RemoteShop · 远程买弹 |
| `RemoteShopConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `RemoteShopConfigScreen.java` | SCAN / 附近全息 | 扫描附近全息 |
| `RemoteShopConfigScreen.java` | 没有命中关键词的目标——把你看到的全息文字填进下面的关键词再重扫。 | 没有扫到符合关键词的目标。把你看到的全息文字填进下面的关键词，再扫一次。 |
| `RemoteShopConfigScreen.java` | KEYWORD / 关键词 | 关键词 |
| `RemoteShopConfigScreen.java` | 逗号或空格分隔，名字里含任意一个就算目标（不分大小写）。 | 用逗号或空格分隔；名字里含其中任意一个就算目标，不分大小写。 |
| `RemoteShopConfigScreen.java` | ACTION / 买一次 | 买一次 |
| `RemoteShopConfigScreen.java` | 只按一下发一次（1 秒冷却），不会自动连买。目标超出 5.5 格时只报告、不发包。 | 按一下只买一次，带 1 秒冷却，不会自动连买。目标超过 5.5 格时只报告、不发包。 |
| `RightClickerConfigScreen.java` | RightClicker | 自动右键 |
| `RightClickerConfigScreen.java` | 自动右键 · 真实模拟 (20 CPS/每 tick 一发) | RightClicker · 默认 20 CPS，每 tick 一发 |
| `RightClickerConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `RightClickerConfigScreen.java` | Enable | 启用模块 |
| `RightClickerConfigScreen.java` | 真实注入 KeyMapping.click，每 tick 一发封顶；SkillCast 期间自动让路。 | 真实注入 KeyMapping.click，每 tick 最多一发。SkillCast 触发时会自动让路，不会撞在一起。 |
| `RightClickerConfigScreen.java` | Active | 实际生效 |
| `RightClickerConfigScreen.java` | 按住原生右键时按间隔自动点击。 | 按住原生右键时按间隔自动点击。 |
| `RightClickerConfigScreen.java` | FIRE RATE / 射速 | 射速 |
| `RightClickerConfigScreen.java` | CPS Min | CPS 下限 |
| `RightClickerConfigScreen.java` | CPS Max | CPS 上限 |
| `RightClickerConfigScreen.java` | Current:  | 当前： |
| `RightClickerConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `RightClickerConfigScreen.java` | Toggle | 开关快捷键 |
| `RightClickerConfigScreen.java` | 按「使用键」当前绑定注入：改成左右键互换（左键=使用）后自动跟随改成左键连点，未绑定则不注入。 | 按「使用键」当前的绑定来注入：如果你把左右键互换过（左键=使用），这里会自动跟着改成左键连点；没绑定就不注入。 |
| `RightClickerConfigScreen.java` | 区间可在 1-50 调节，Min>Max 会自动交换；与 SkillCast 互斥，不会叠加包。 | CPS 区间可以填 1-50，Min 比 Max 大时会自动交换。和 SkillCast 互斥，不会叠加发包。 |
| `SimpleModuleScreen.java` | STATUS / 状态 | 开关与状态 |
| `SimpleModuleScreen.java` | Enable | 启用模块 |
| `SkillCastConfigScreen.java` | SkillCast | 技能一键释放 |
| `SkillCastConfigScreen.java` | 技能释放 · 原生使用时序 | SkillCast · 原生使用时序 |
| `SkillCastConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `SkillCastConfigScreen.java` | Enable | 启用模块 |
| `SkillCastConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `SkillCastConfigScreen.java` | Primary | 主键 |
| `SwordBlockConfigScreen.java` | SwordBlock | 剑格挡动画 |
| `SwordBlockConfigScreen.java` | 剑格挡 · 客户端视觉设置 | SwordBlock · 客户端视觉 |
| `SwordBlockConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `SwordBlockConfigScreen.java` | Enable | 启用模块 |
| `SwordBlockConfigScreen.java` | BOUNDARY / 边界 | 使用限制 |
| `TeamSyncConfigScreen.java` | TeamSync | 队伍同步 |
| `TeamSyncConfigScreen.java` | 队伍同步 · 安全连接与本地显示 | TeamSync · 安全连接与本地显示 |
| `TeamSyncConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `TeamSyncConfigScreen.java` | Enable | 启用模块 |
| `TeamSyncConfigScreen.java` | Connection | 连接状态 |
| `TeamSyncConfigScreen.java` | ENDPOINT / 连接地址 | 连接地址 |
| `TeamSyncConfigScreen.java` | WebSocket | WebSocket 地址 |
| `TeamSyncConfigScreen.java` | Token HTTPS | Token 地址 |
| `TeamSyncConfigScreen.java` | 仅接受 wss / https、无 userinfo、query 或 fragment；不会显示或保存 token 内容。 | 地址只接受 wss / https，且不能带 userinfo、query 或 fragment。token 内容不会被显示或保存。 |
| `TeamSyncConfigScreen.java` | DISPLAY / 显示 | 显示 |
| `TeamSyncConfigScreen.java` | HUD overlay | HUD 叠层 |
| `TeamSyncConfigScreen.java` | World markers | 世界标记 |
| `TeamSyncConfigScreen.java` | Local target | 本地瞄准兜底 |
| `TeamSyncConfigScreen.java` | Show ping | 显示延迟 |
| `TeamSyncConfigScreen.java` | Ping Button | 延迟测试键 |
| `TeamSyncConfigScreen.java` | TIMING / 周期 | 周期 |
| `TeamSyncConfigScreen.java` | State interval | 状态上报间隔 |
| `TeamSyncConfigScreen.java` | Roster check | 队伍检测间隔 |
| `TeamSyncConfigScreen.java` | 状态上报 50–5000 ms；队伍检测 500–10000 ms。连接和 token 请求在独立线程执行。 | 状态上报间隔 50–5000 毫秒，队伍检测间隔 500–10000 毫秒。连接和取 token 都在独立线程里跑，不会卡住游戏。 |
| `TeamSyncConfigScreen.java` | LAYOUT / 位置 | 位置 |
| `TeamSyncConfigScreen.java` | Right offset | 右侧偏移 |
| `TeamSyncConfigScreen.java` | HUD Y | HUD 垂直位置 |
| `TeamSyncConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `TeamSyncConfigScreen.java` | Toggle | 开关快捷键 |
| `TeamSyncConfigScreen.java` | 主键首次按下启用模块；再次按下切换 HUD。Ping 鼠标键沿用 Forge 编码 -100 + button。 | 主键第一次按是开模块，再按一次是开关 HUD。鼠标键沿用 Forge 的编码方式。 |
| `TeammateHpConfigScreen.java` | TeammateHP | 队友血量 |
| `TeammateHpConfigScreen.java` | 队友血量卡片 · 最多显示四名玩家 | TeammateHP · 最多显示四名玩家 |
| `TeammateHpConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `TeammateHpConfigScreen.java` | Enable | 启用模块 |
| `TeammateHpConfigScreen.java` | 注册队友卡片 HUD 和 H 键切换。 | 注册队友卡片 HUD 和 H 键开关。 |
| `TeammateHpConfigScreen.java` | Show Cards | 显示卡片 |
| `TeammateHpConfigScreen.java` | In-Game overlay | 游戏内叠层 |
| `TeammateHpConfigScreen.java` | 是否在游戏 HUD 上绘制队友卡片。 | 在游戏 HUD 上画出队友卡片。 |
| `TeammateHpConfigScreen.java` | Show Hidden | 包含隐身队友 |
| `TeammateHpConfigScreen.java` | 包含被客户端标记为 invisible 的队友。 | 连被客户端标记为隐身的队友也算进去。 |
| `TeammateHpConfigScreen.java` | Show Distance | 显示距离 |
| `TeammateHpConfigScreen.java` | 在卡片右侧显示与自己的距离。 | 在卡片右侧显示和你自己的距离。 |
| `TeammateHpConfigScreen.java` | Revive Timer | 救援计时 |
| `TeammateHpConfigScreen.java` | CARD / 卡片 | 卡片 |
| `TeammateHpConfigScreen.java` | Card width | 卡片宽度 |
| `TeammateHpConfigScreen.java` | Background alpha | 背景不透明度 |
| `TeammateHpConfigScreen.java` | Card width 100–400；背景 alpha 0–200。X/Y、缩放和 H 键在本页以外保留默认布局。 | 卡片宽度 100–400，背景不透明度 0–200。X / Y、缩放和 H 键仍用默认值，本页不提供。 |
| `TeammateHpConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `TeammateHpConfigScreen.java` | 默认 H 键；名单优先使用当前加载的其他玩家，最多固定显示四张卡片。 | 默认是 H 键。名单优先用当前已加载的其他玩家，最多同时显示四张卡片。 |
| `ToggleSprintConfigScreen.java` | ToggleSprint | 疾跑切换 |
| `ToggleSprintConfigScreen.java` | 疾跑切换 · 客户端状态 | ToggleSprint · 客户端状态 |
| `ToggleSprintConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ToggleSprintConfigScreen.java` | Enable | 启用模块 |
| `ToggleSprintConfigScreen.java` | 启用后沿用原版 sprint 条件与同步逻辑。 | 打开后仍然沿用原版的冲刺条件和同步逻辑。 |
| `ToggleSprintConfigScreen.java` | Sprint lock | 锁定疾跑 |
| `ToggleSprintConfigScreen.java` | HUD Text | HUD 文字 |
| `ToggleSprintConfigScreen.java` | 屏幕左下角显示 [Sprint] / [Sprint OFF] 状态字样。 | 在屏幕左下角显示 [Sprint] / [Sprint OFF] 状态字样。 |
| `ToggleSprintConfigScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `ToggleSprintConfigScreen.java` | Primary | 主键 |
| `ToggleSprintConfigScreen.java` | 绑定语义与原 1.8.9 一致：键盘使用 GLFW key code，鼠标使用 -100 + button。 | 绑定的规则和原 1.8.9 完全一样。 |
| `ToroHealthConfigScreen.java` | ToroHealth | 伤害数字 |
| `ToroHealthConfigScreen.java` | 准心目标血量 · Hearts / Numeric | ToroHealth · 准心目标血量 |
| `ToroHealthConfigScreen.java` | X | 水平位置 X |
| `ToroHealthConfigScreen.java` | Y | 垂直位置 Y |
| `ToroHealthConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ToroHealthConfigScreen.java` | Enable | 启用模块 |
| `ToroHealthConfigScreen.java` | 启用准心目标血量 HUD。 | 显示准心目标的血量和吸收盾。 |
| `ToroHealthConfigScreen.java` | Overlay | 显示方式 |
| `ToroHealthConfigScreen.java` | DISPLAY / 显示 | 显示 |
| `ToroHealthConfigScreen.java` | Mode | 模式 |
| `ToroHealthConfigScreen.java` | 原 Forge 的 Hearts / Numeric / Off 三种模式。 | 沿用原 Forge 的三种模式：心形 / 数字 / 关闭。 |
| `ToroHealthConfigScreen.java` | Position | 显示位置 |
| `ToroHealthConfigScreen.java` | X | 水平位置 X |
| `ToroHealthConfigScreen.java` | Y | 垂直位置 Y |
| `ToroHealthConfigScreen.java` | 位置范围为 -20000 到 20000；非 Custom 位置会忽略 X/Y。 | 位置可填 -20000 到 20000。选了预设位置时，X / Y 会被忽略。 |
| `ToroHealthConfigScreen.java` | TIMING / 时长 | 时长 |
| `ToroHealthConfigScreen.java` | Hide delay | 消失延迟 |
| `ToroHealthConfigScreen.java` | 准心离开目标后保留显示 50–5000 ms。 | 准心移开之后，数字还会保留 50–5000 毫秒。 |
| `ToroHealthConfigScreen.java` | DAMAGE PARTICLES / 伤害粒子 | 伤害粒子 |
| `ToroHealthConfigScreen.java` | 原 Forge 字段已保留并兼容配置，但 26.2 粒子注入尚未迁移；当前没有可点击的假开关。 | 原 Forge 的伤害粒子字段还留着、配置也兼容，但 26.2 上还没接上，所以这里没有对应的开关。 |
| `ToroHealthConfigScreen.java` | Damage color | 伤害数字颜色 |
| `ToroHealthConfigScreen.java` | Heal color | 治疗数字颜色 |
| `UiText.java` |  |  |
| `ViewHoldConfigScreen.java` | ViewHold | 临时视角 |
| `ViewHoldConfigScreen.java` | 按住切视角 · 目标视角与俯仰镜像 | ViewHold · 按住切换，松开恢复 |
| `ViewHoldConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ViewHoldConfigScreen.java` | Enable | 启用模块 |
| `ViewHoldConfigScreen.java` | TARGET VIEW / 目标视角 | 目标视角 |
| `ViewHoldConfigScreen.java` | 正面视角时渲染帧内取反 pitch（相机翻到面前上方俯视自己）。 | 切到正面视角时会在这一帧里取反俯仰角，相机就像翻到你面前上方往下看你。 |
| `ZombiesAlertsConfigScreen.java` | ZombiesAssist · Alerts | 提示与警报 |
| `ZombiesAlertsConfigScreen.java` | 提示与警报 · TOO / FR / LS | ZombiesAssist · Alerts |
| `ZombiesAssistConfigScreen.java` | ZombiesAssist | 僵尸助手 |
| `ZombiesAssistConfigScreen.java` | 僵尸波次 HUD · scoreboard tracker | ZombiesAssist · 波次 HUD 与记分板 |
| `ZombiesAssistConfigScreen.java` | STATUS / 状态 | 开关与状态 |
| `ZombiesAssistConfigScreen.java` | Enable | 启用模块 |
| `ZombiesAssistConfigScreen.java` | 启用 scoreboard tracker 与当前已迁移的 HUD。 | 打开 scoreboard 追踪和已经迁移过来的那部分 HUD。 |
| `ZombiesAssistConfigScreen.java` | HUD Overlay | HUD 叠层 |
| `ZombiesAssistConfigScreen.java` | 显示 Round / Left / AA 状态行。 | 显示 Round / Left / AA 那一行状态。 |
| `ZombiesAssistConfigScreen.java` | Mobs count | 剩余怪数 |
| `ZombiesAssistConfigScreen.java` | 显示当前 scoreboard 中解析出的 Left 数值。 | 显示从 scoreboard 解析出来的 Left（剩余怪数）。 |
| `ZombiesAssistConfigScreen.java` | HUD POSITION / 位置 | HUD 位置 |
| `ZombiesAssistConfigScreen.java` | Top Y | 顶部 Y 坐标 |
| `ZombiesAssistConfigScreen.java` | Scale | 缩放 |
| `ZombiesAssistConfigScreen.java` | Y 范围 0–20000；缩放范围 0.5–2.0。X 偏移仍可通过配置文件保留。 | Y 可填 0–20000，缩放可填 0.5–2.0。X 偏移仍然只能改配置文件。 |
| `ZombiesAssistConfigScreen.java` | TRACKER / 追踪边界 | 追踪范围 |
| `ZombiesAssistConfigScreen.java` | 已接入 scoreboard 波次/剩余数、AA 和 Fast Revive latency 代码路径；FR、Power-up、TOO、LS、声音/title 完整相关性、自动聊天和世界 beam 尚未完成 Prism 实机验收，继续标记 PORTING。 | 目前接上的有：scoreboard 的波次 / 剩余数、AA 状态、Fast Revive 延迟。还有一批（FR、Power-up、TOO、LS、声音与 title 的完整对应、自动聊天、世界光柱）还没在 Prism 实例上实机验收，暂时标为 PORTING。 |
| `ZombiesAutoConfigScreen.java` | ZombiesAssist · Auto | 记分板 / 消息 |
| `ZombiesAutoConfigScreen.java` | 记分板 / 消息 · 自动行为 | ZombiesAssist · Auto & Chat |
| `ZombiesDisplayConfigScreen.java` | ZombiesAssist · Display | 显示开关 |
| `ZombiesDisplayConfigScreen.java` | 显示区域 · HUD 与 Power-up | ZombiesAssist · Display |
| `ZombiesDisplayConfigScreen.java` | HUD Overlay | HUD 叠层 |
| `ZombiesDisplayConfigScreen.java` | Top Y | 顶部 Y 坐标 |
| `ZombiesSubConfigScreen.java` | Enable | 启用模块 |
| `ZombiesSubConfigScreen.java` | 启用或关闭 ZombiesAssist 全部运行逻辑。 | 打开或关闭 ZombiesAssist 的全部运行逻辑。 |
| `ZombiesSubConfigScreen.java` | MODULE / 模块 | 模块开关 |
| `ZombiesSubConfigScreen.java` | OPTIONS / 选项 | 选项 |
| `ZombiesSubConfigScreen.java` | HUD POSITION / 位置与缩放 | HUD 位置与缩放 |
| `ZombiesSubConfigScreen.java` | 修改在离开页面时校验并保存；整数和缩放值均按 Forge 范围限制。 | 离开这个页面时会校验并保存；整数和缩放都按 Forge 的范围限制。 |
| `ZoomScopePanelScreen.java` | ZoomScope | 放大镜 |
| `ZoomScopePanelScreen.java` | 放大镜 · 按住放大 + 滚轮调倍率 | ZoomScope · 按住放大 + 滚轮调倍率 |
| `ZoomScopePanelScreen.java` | STATUS / 状态 | 开关与状态 |
| `ZoomScopePanelScreen.java` | Enable | 启用模块 |
| `ZoomScopePanelScreen.java` | KEYBIND / 快捷键 | 快捷键 |
| `ZoomScopePanelScreen.java` | 按住局部放大，松开恢复；按住时滚轮调 2~8x。单键绑定，支持鼠标侧键。 | 按住才放大，松开恢复；按住期间滚轮调 2~8 倍。单键绑定，支持鼠标侧键。 |
| `ZoomScopePanelScreen.java` | 按住放大键期间滚动滚轮：上滚放大 / 下滚缩小，调整后立即保存。 | 按住放大键时滚轮上滚放大、下滚缩小，调完立即保存。 |
| `ZoomScopePanelScreen.java` | SENSITIVITY / 灵敏度 | 灵敏度 |
| `ZoomScopePanelScreen.java` | 放大时灵敏度 = k/倍率。k=1 为 1:1 手感（4x→1/4），k=2 整体抬高（4x→1/2）。 | 放大后的灵敏度 = k ÷ 倍率。k=1 是等比手感（4 倍时变成 1/4），k=2 整体抬高（4 倍时是 1/2）。 |
| `ZoomScopePanelScreen.java` | ABOUT / 说明 | 说明 |
| `ZoomScopePanelScreen.java` | 按住快捷键：屏幕中央 16:9 矩形内显示清晰的局部放大画面（当前为全屏 FOV 缩放，画中画二期用小 FBO 实现），周边保持正常视野，松开立即消失，无平滑过渡。 | 按住快捷键时，屏幕中央按 16:9 放大显示局部画面，周边保持正常视野，松开立刻消失、没有过渡动画。目前用全屏 FOV 缩放实现，画中画版本还没做。 |
| `ZoomScopePanelScreen.java` | 放大时鼠标灵敏度按 k/倍率降低，每个倍率固定对应、即时生效。纯客户端渲染，不发包。 | 放大时鼠标灵敏度按 k ÷ 倍率降低，每个倍率对应一个固定值，即时生效。纯客户端渲染，不发包。 |

## 二、原样保留（两版一致）

| 文件 | 文案 |
| --- | --- |
| `ModulePanelRegistry.java` | 队伍协同 |
| `ModulePanelRegistry.java` | 聊天 |
| `ModulePanelRegistry.java` | 回合计时 |
| `ModulePanelRegistry.java` | 封包日志 |
| `ModulePanelRegistry.java` | 粒子屏蔽 |
| `ModulePanelRegistry.java` | 自动救人 |
| `ModulePanelRegistry.java` | 刷怪点标记 |
| `ModulePanelRegistry.java` | 铁傀儡标记 |
| `ModulePanelRegistry.java` | 放大镜 |
| `ModulePanelRegistry.java` | 僵尸助手 |
| `ModulePanelRegistry.java` | 防误领 Puncher |
| `ModulePanelRegistry.java` | 免换弹 |
| `ModulePanelRegistry.java` | 瞄准提前量 |
| `ModulePanelRegistry.java` | 线框透视 |
| `ModulePanelRegistry.java` | 模型透视 |
| `ModulePanelRegistry.java` | 玩家轮廓 |
| `ModulePanelRegistry.java` | 自动右键 |
| `ModulePanelRegistry.java` | DPS 计数 |
| `ModulePanelRegistry.java` | 伤害数字 |
| `ModulePanelRegistry.java` | 队友血量 |
| `ModulePanelRegistry.java` | 队伍同步 |
| `ModulePanelRegistry.java` | 玩家隐身 |
| `ModulePanelRegistry.java` | 潜行高度 1.8 |
| `ModulePanelRegistry.java` | 疾跑切换 |
| `ModulePanelRegistry.java` | 聊天清理 |
| `ModulePanelRegistry.java` | 聊天复制 |
| `ModulePanelRegistry.java` | 聊天翻译 |
| `ModulePanelRegistry.java` | 消息点击翻译 |
| `ModulePanelRegistry.java` | 欢迎横幅 |
| `ModulePanelRegistry.java` | 快捷文本 |
| `ModulePanelRegistry.java` | 求 Rank |
| `ModulePanelRegistry.java` | 远程商店 |
| `ModulePanelRegistry.java` | 语音输入 |
| `ModulePanelRegistry.java` | 僵尸标记 |
| `ModulePanelRegistry.java` | 波次音效 |
| `ModulePanelRegistry.java` | 26.2/GLFW 原生支持系统输入法，Forge 的 Swing 外部输入框（LWJGL2 IME 变通）不再需要。 |
| `ModulePanelRegistry.java` | 自动 Re-Shift |
| `ModulePanelRegistry.java` | 原 Forge 版本因反作弊封禁风险停用，Fabric 端不会启用；防误松需求由 anti_reshift（AntiReshift）覆盖。 |
| `ModulePanelRegistry.java` | AC 测试日志 |
| `ModulePanelRegistry.java` | 原 Forge 版本是测试/诊断模块，Fabric 端不会启用。 |
| `ModulePanelRegistry.java` | 显示开关 |
| `ModulePanelRegistry.java` | 提示与警报 |
| `ModulePanelRegistry.java` | 记分板 / 消息 |
| `ModulePanelRegistry.java` |  |
| `UiText.java` |  |

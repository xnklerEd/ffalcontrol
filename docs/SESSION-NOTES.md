# ffalcontrol 会话总结（按「扩清单 / 装 HOME / 出厂一键恢复包」三条线重述）

设备：雷鸟 鹤7 75R79A / MT9655_4K_CN / Android 14 / `UTT2.250416.001 AU01` / 灵控 3.x
会话时间：2026-10-23 ~ 10-24
交付版本：`ffalcontrol 0.1.3`（包名 `app.ffalcontrol`）

---

## 三行结论

1. **线一（扩清单）**：22 个包 + 41 个组件写进 `Guard_disable_App`，44 条写进 `AppBootPolicy`；包级实测全部生效，**组件级 guard 条目全部被 top-skip 跳过**，组件拦截只能靠 AppBootPolicy。
2. **线二（装 HOME）**：**成功**。`aerial-tv` standard 版装上并设为默认 Home，靠 `InstallConfig.launcherStrategy.packages` 放行 `app.orchard.tv`；冷启动仍会被 cyberui 抢一次前台，按 Home 键回到 aerial。
3. **线三（出厂一键恢复包）**：**成功**。`ffalcontrol 0.1.3` 一个按钮写三行配置，幂等（重复点提示"已经执行过"），恢复出厂后 ≤10 分钟回到目标状态，**不需要电脑**。

---

## 基线与「判死」的更新（推翻旧会话结论）

| 旧会话说 | 本会话实测 |
|---|---|
| ADB 判死（TLS 握手断、无配对 UI、USB 不枚举） | **不再判死**。USB 枚举多次成功（`180AC0C20670840B3`），`adb shell` uid 2000 可用，`pm disable-user` 可用 |
| root 判死（截至 2026-10-07 无公开方案） | **仍然判死**，全程没有 root |
| 通道只有 ConfigProvider + Termux sshd | 仍然成立，但多了一条 **USB adb**（`adb install` 依旧被 TCL verifier 拒） |
| cyberui 的 `CheckUpdateActivity` 在清单里 | **本机固件没有这个类**，旧类名失效 |

---

## 线一：扩清单 —— 结果与实测

### 实际写入

```
Guard_disable_App   63 条 = 22 包 + 41 组件
  packages:   bootadservice / profilemanager / logkit / c2dm.client / atomic.servicecenter
              xian.StartandroidService / repairguide / useragreement / appreciate.art
              playskill / ffeducation / appmarket2 / wholenetsearch / vod / ffkaraoke
              gamebar / tvsmartalbum / hotelmenu / t_solo / karaoke.micmanager
              usercenter / walleve
  components: mlchannel.MlChannelActivity / mlchange.MlChangeSysActivity / DeskSwitchActivityAlias
              subject.*（两套前缀）/ addwidget.*（7 条）/ checkupdate.* / pwcFullScreen 等

AppBootPolicy       44 条 componentName（与上面组件基本同集，额外含应用中心与桌面切换）
```

### 关键实测结论

- **包级没有 top-skip**：22 个包在 `pm list packages -e` 里全部消失，说明 guard 在开机时确实执行了包级禁用。
- **组件级有 top-skip**：开机时前台是 `com.tcl.cyberui`，guard 会跳过整条组件列表。所以 41 个组件条目**从来没真正生效过**——真正起作用的是 AppBootPolicy。
- **AppBootPolicy 跨包有效**：`com.tcl.mlauncher.mlchannel.MlChannelActivity` 不属于 cyberui 包，加进去之后应用中心被拦住了。之前误以为"跨包无效"，其实是选错了 Activity（`AllApplicationActivity` 是应用列表，不是应用中心）。
- **`activityRestrictionBgList` 不是黑名单**：它的语义是"caller 在后台时限制启动"，前台点击不受影响，拦不住任何东西。
- **`pm list packages -d` 不可信**：TCL 口径只列 5 条，实际禁用状态要看 `pm list packages -e`（不在里面 = 禁用）。

### 红线遵守

`cyberui / tv / settings / systemserver / ttvs / TFDS.service / guard / providers.config / initsetup / sogou / ui_mediaCenter / autopair / tcl_bt_rcu_service / tvos` 全部没动。

---

## 线二：装 HOME —— 结果与细节

### 成功路径

1. 装 `aerial-standard.apk`（声明 `HOME` 类别）；
2. 如果之前装过 `aerial-guard`（不声明 HOME），需要先点「放行 aerial」把 `app.orchard.tv` 写进 `InstallConfig.launcherStrategy.packages`；
3. 系统默认应用页设为 Home。

### 关键细节（容易搞错）

- **干净设备直接装 standard 不需要白名单**（用户实测）；
- **只有 guard → standard 换变体时才需要**放行；
- `aerial-guard` 不声明 HOME，靠 `HomeGuardService` 守护，**不是**系统默认桌面；
- 冷启动时 `WindowManager` 硬编码 `startHome cyberui`，默认 Home 会生效但会被抢一次前台，按 Home 键回到 aerial；
- `RoleManager` 的 `HomeSettingsActivity` 可以用来设默认，但 TVControl 没有 `MANAGE_ROLE_HOLDERS`，只能拉起系统页面让用户点。

### 没有解决的

- 冷启动第一次前台仍是灵控（TCL 固件层硬编码）；
- `dpm set-device-owner` 不可用（`mHasFeature=false`）；
- 后台启动 Activity 被 BAL 严格拦截，overlay / AlarmClock / DeviceOwner 豁免全部无效。

---

## 线三：出厂一键恢复包 —— 结果与交付物

### 交付物

```
ffalcontrol_v0.1.3.apk
  package   app.ffalcontrol
  label     ffalcontrol 净化
  version   0.1.3 (versionCode 36)
  签名      a549dac0…
  界面      横向一排五个按钮：刷新诊断 / 一键恢复 / 放行 aerial / 默认桌面 / 停止并退出

Mac        ~/Downloads/ffalcontrol_v0.1.3.apk
电视       /sdcard/Download/ffalcontrol_v0.1.3.apk
U 盘       /storage/5850-1BFE/ffalcontrol_v0.1.3.apk

U 盘附带
  README.txt                恢复步骤 + 手工回写示例
  AppBootPolicy.json        44 条原文（备份/对照）
  Guard_disable_App.json    63 条原文
  InstallConfig.json        launcherStrategy 原文
```

### 一键恢复做了什么

```
POST /provision 或点「一键恢复」
  → Guard_disable_App：跳过 enabled=3 的包，其余写入（行不存在时 insert）
  → AppBootPolicy：读 raw → JSON 追加缺失的 TARGETS → 通用写入
  → InstallConfig：把 app.orchard.tv 加进 launcherStrategy
  → 三行都已是目标状态时，返回 already_provisioned，界面提示"已经执行过，无需重复执行"
```

### 恢复出厂后的标准流程（不需要电脑）

1. 装 `ffalcontrol_v0.1.3.apk`（电视卫士 / 文件管理器）；
2. 点「一键恢复」；
3. **断电 30 秒**（不是待机）；
4. 完成。

### 为什么之前会 422（已修）

| 422 来源 | 修法 |
|---|---|
| `GuardControl.inspectOrdinaryVisibility` 认为包"不可见" | provision 先跳过 `enabled=3`，再绕过可见性校验直接写 provider |
| UI 按钮在 provision 成功后又补一次严格校验的 `appBootControl.apply` | 删掉那段补刀，改为读 provision 返回值 |
| `AppBootPolicy.propose` 要求"按固定顺序追加" | 改成 JSON 解析 + 逐条追加 + 通用写入 |

---

## 验收清单（实测）

| 项 | 结果 |
|---|---|
| 应用中心方块 | ✅ 点不开（`MlChannelActivity` 被 AppBootPolicy 拦） |
| 桌面切换方块 | ✅ 点不开（`MlChangeSysActivity` / `DeskSwitchActivityAlias`） |
| 检查桌面更新方块 | ❌ 仍可用——它跳 `com.tcl.versionUpdateApp`，与恢复出厂共用 `SystemUpdateActivity`，拦了恢复出厂也废 |
| 恢复出厂 | ✅ 可用（OTA 三包 `versionUpdateApp` / `snm.upgrade` / `UpdatePeripheral` 全程 enabled） |
| 系统更新 | ✅ 可用（无法单独禁，与恢复出厂共用） |
| 灵控负一屏整体 | ❌ 拦不住——是 `MainActivity` 内 ViewPager 的一页 |
| 预装包删除 | ❌ 只读分区 + 空卸载白名单，禁用是上限 |

---

## 补充发现：负一屏是云端下发的

- 恢复出厂 + **不联网** → 负一屏空白，"应用中心 / 桌面切换 / 检查桌面更新"方块都不出现；
- 联网后立刻出现。

说明这些方块来自 `launcher.tcloudfamily.com` 下发的面板配置。想从界面上彻底去掉，只能 DNS 屏蔽：

```
0.0.0.0 launcher.tcloudfamily.com
0.0.0.0 oss.launcher.tcloudfamily.com
0.0.0.0 test.launcher.tcloudfamily.com
```

**`sntp-o.api.leiniao.com` 是 NTP 时间源，不能屏蔽。**

---

## 回滚

1. U 盘里的三个 JSON 就是原始内容，可用 `/config-write` 原样写回；
2. 或者恢复出厂（会清空三行，但保留默认结构）；
3. `pm enable <pkg>` 可单独恢复某个包（需要 adb）。

---

## 当前状态快照（2026-10-24）

```
AppBootPolicy          44 条组件
Guard_disable_App      63 条（22 包 + 41 组件）
InstallConfig          launcherStrategy.packages = ["app.orchard.tv"]
enabled 包总数         120
TVControl              app.ffalcontrol 0.1.3
aerial                 app.orchard.tv 1.40（standard）
```

# ffalcontrol 会话技术总结（2026-10 实测）

设备：雷鸟 75R79A / TCL MT9655_4K_CN / Android 14 / `UTT2.250416.001 AU01`
调试通道：USB adb（uid 2000）+ Termux openssh（app uid），**不需要 root**

---

## 一、实测有效的机制

### 1. `pm disable-user --user 0 <pkg>`（仅 adb shell）
- 结果 `enabled=3`（DISABLED_USER），重启保持。
- **Termux（app uid）执行会被拒**：`SecurityException: uid=10105`。
- 组件级 `pm disable-user pkg/cls` 被 TCL 拒绝：`Shell cannot change component state`。

### 2. `Guard_disable_App`（com.tcl.providers.config）
- **包级**条目（`componentName` 为空）由 `com.tcl.guard` 执行，**没有 top-skip**，22 个包实测全部禁用。
- **组件级**条目**有 top-skip**：开机时前台是 `com.tcl.cyberui`，guard 跳过整条组件列表。**组件级基本不可用**。
- 行不存在时可用 `ContentResolver.insert` 建行（project_id + config_content）。

### 3. `AppBootPolicy.activityBlackList.componentName`
- TCL 在 `ActivityStarter` 里拦（`Activity start intercepted by TclAppBoot!`），**无同包/系统应用/前台豁免**。
- **支持跨包组件**：`com.tcl.mlauncher.mlchannel.MlChannelActivity`（不在 cyberui 包里）实测拦住了应用中心。
- **精确匹配**：纯类名，不带 `package/`，不解析前缀/通配符。
- 只能拦 Activity 启动，**拦不住 ViewPager 页切换**（灵控负一屏）。
- **必须冷启动加载**；待机唤醒不重载。热更新链（ConfigObserver）在 MT9655 上不可靠。

### 4. `InstallConfig.launcherStrategy.packages`
- HOME 类 APK 的安装白名单。
- **干净设备直接装 `aerial-standard`（声明 HOME）不需要放行**（实测）。
- **只有先装过 `aerial-guard` 再换 standard 时才需要**放行 `app.orchard.tv`。

### 5. ConfigProvider 写入（app 上下文）
- `/config-write` 通用写入可绕过 `GuardControl` / `AppBootPolicy` 的严格校验。
- 每行写入都要 `expected_sha256`（CAS），写完读回校验。

---

## 二、实测无效 / 已作废（不要再试）

| 做法 | 结论 |
|---|---|
| 组件级 `Guard_disable_App` | top-skip 跳过，**不生效** |
| `activityRestrictionBgList` = appmarket2 | 这是"后台 Activity 限制"，**不是黑名单**，拦不住前台点击 |
| `com.tcl.cyberui.application.AllApplicationActivity` 拦截 | 它是**应用列表**，不是应用中心，拦了没用 |
| `com.tcl.cyberui.checkupdate.main.CheckUpdateActivity` | 当前固件**没有这个类**（旧类名） |
| 拦 `com.tcl.versionUpdateApp.SystemUpdateActivity` | 与恢复出厂**共用**，拦了恢复出厂一起废 |
| 禁 `com.tcl.versionUpdateApp` / `com.snm.upgrade` | 恢复出厂报"此功能未安装" |
| 禁 `com.tcl.UpdatePeripheral` | 无影响（外设 OTA，与恢复出厂无关） |
| `dpm set-device-owner` | `mHasFeature=false`，DPM 被裁 |
| `SYSTEM_ALERT_WINDOW` / AlarmClock PI / Device Owner 绕过 BAL | 全部被拦 |
| `pm list packages -d` 作为禁用依据 | TCL 口径只列 5 个，**不可信**；用 `pm list packages -e`（不在 = 禁用） |
| Termux 里 `dumpsys` / `content query` | 无权限（app uid） |
| TVControl 调 `PowerManager.reboot()` / `ACTION_REQUEST_SHUTDOWN` / `reboot` 命令 | 普通 app 无 REBOOT 权限，**冷重启按钮做不了** |
| 灵控负一屏（应用中心/桌面切换/检查桌面更新方块） | 是 `MainActivity` 内 `ViewPager` 的页，**AppBootPolicy 拦不住** |

---

## 三、容易忽略的关键细节

1. **两个包名前缀别混**：
   - `com.tcl.mlauncher`（**单 l**）→ 应用中心 `mlchannel.MlChannelActivity`、`subject.*`
   - `com.tcl.mllauncher`（**双 l**）→ 桌面场景 `mlscene.addwidget.*`、`mlchange.MlChangeSysActivity`
2. **guard 的 top-skip 只跳组件级**，包级照常执行。
3. **AppBootPolicy 必须冷启动**（断电 30 秒），待机唤醒不加载。
4. **恢复出厂清空 `/data`，但 provider 行保留默认结构**（`AppBootPolicy` 有完整默认模板，`componentName` 为空）。
5. **provision 必须先跳过 `enabled=3` 的包**，否则 `GuardControl` 的 `inspectOrdinaryVisibility` 抛 422（`Ordinary PM visibility is unknown`）。
6. **UI 按钮的 422 真凶是 `runProvision` 里那段补刀**（provision 成功后又调一次严格校验的 `appBootControl.apply`）——HTTP 端点只走 provision，所以从电脑 curl 成功、点按钮失败。
7. **负一屏的方块是云端下发**（`launcher.tcloudfamily.com`）：断网恢复出厂后负一屏空白，联网立刻出现。
8. **`sntp-o.api.leiniao.com` 是 NTP 时间源，不能屏蔽**；`launcher.tcloudfamily.com` / `oss.launcher.tcloudfamily.com` 可屏蔽。
9. **InstallConfig 白名单只在换 aerial 变体时需要**，干净设备直接装 standard 不需要。
10. **TVControl 的 token**：v33 及以前是固定值（`generated/BuildToken.java` 的 `VALUE`）；v0.1.x 改为运行时生成、只打 logcat、界面显示前 8 位。

---

## 四、版本演进（重要节点）

| 版本 | 关键变化 |
|---|---|
| v1–v4（.codex 会话） | 只有 `/guard` + `/app-boot-policy`；v4 是 codex 最后一版 |
| v17 | 加 `/provision`、`/config-write`、30 包清单 |
| v25 | 加 `activityRestrictionBgList`（后来证明是后台限制） |
| v27–v31 | 41 组件合并、`GuardControl` 可见性校验绕过、TARGETS 扩到 48 |
| v33 | 删除 UI 补刀，provision 一把过（不再 422） |
| **0.1.1** | 包名改 `app.ffalcontrol`、按钮横向、标题显示版本 |
| **0.1.2** | 应用名 `ffalcontrol 净化`、token 只显示前 8 位、说明文案修正 |
| **0.1.3** | 重复执行提示"已经执行过，无需重复执行" |

---

## 五、恢复出厂后的标准流程（不需要电脑）

1. 装 `ffalcontrol_v0.1.3.apk`（电视卫士/文件管理器）；
2. 打开，点「一键恢复」；
3. **断电 30 秒**（不是待机）；
4. 完成。

产物位置：
- Mac `~/Downloads/ffalcontrol_v0.1.3.apk`
- 电视 `/sdcard/Download/ffalcontrol_v0.1.3.apk`
- U 盘 `/storage/5850-1BFE/ffalcontrol_v0.1.3.apk`

---

## 六、当前配置的快照（2026-10-24）

```
AppBootPolicy          44 条组件（含 MlChannelActivity / MlChangeSysActivity / DeskSwitchActivityAlias）
Guard_disable_App      63 条 = 22 包 + 41 组件
InstallConfig          launcherStrategy.packages = ["app.orchard.tv"]
enabled 包总数         120
```

---

## 七、已知天花板

- 预装包**不能真删**（只读分区 + 空卸载白名单），禁用是上限。
- 后台 Service（`LauncherUpdateService` / `AppInstallReceiver`）禁不掉（AppBootPolicy 无 service 黑名单 + guard 跳组件）。
- "检查桌面更新"与恢复出厂**共用 `SystemUpdateActivity`**，无法单独禁。
- 灵控负一屏是 ViewPager 页，只能靠 DNS 屏蔽让它不下发。
- 冷启动进灵控（`WindowManager` 硬编码 `startHome cyberui`）无法用第三方桌面完全绕过，默认 Home 生效但开机可能被抢一次前台。

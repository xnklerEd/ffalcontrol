> English: [README.md](README.md)

# ffalcontrol

雷鸟（FFALCON）/ TCL 电视的本机控制工具。跑在电视上，通过 TCL 自带的 `ConfigProvider` 读写系统配置，不需要 root，也不需要 ADB 常驻——装一次，点一下，把干净状态恢复回来。

> 名字取自 FFALCON + control。最初是一个私有诊断工具，用来在没有 root、没有开发者选项的 MT9655 电视上把广告/遥测/推荐入口关掉。

## 适用机型

- 雷鸟 鹤7 75R79A（`MT9655_4K_CN`，Android 14，`UTT2.250416.001 AU01`）——实测机型
- 其他使用 `com.tcl.providers.config` 的 TCL / 雷鸟 MT9655、MT9653 机型，理论上通用，未逐一验证

**不要在不是自己的设备上运行。** 它会改动系统配置。

## 它做什么

TCL 把大量系统行为放在 `com.tcl.providers.config` 这个 ConfigProvider 里，通过它向系统应用和工具提供配置读写。TCL 自己的 `com.tcl.guard`（系统 uid）会在开机和待机唤醒时读取其中的禁用清单并执行。

ffalcontrol 就是把这个通道封装成一个可点按的工具：

| 端点 | 作用 |
|---|---|
| `GET /health` | 服务状态、版本、UID |
| `GET /snapshot` | 包列表、配置行、HOME 解析、应用状态 |
| `GET /app-boot-policy` | 当前 AppBootPolicy 原文与 SHA |
| `GET /install-config` | 当前 InstallConfig 原文与 SHA |
| `GET /provision` | 一键恢复的当前状态 |
| `GET /settings` / `GET /settings-all` | 系统设置快照 |
| `GET /pkg/<package>` | 单个包的详情 |
| `POST /guard` | 写 `Guard_disable_App`（包级 + 组件级禁用） |
| `POST /app-boot-policy` | 写 `AppBootPolicy`（Activity 启动拦截） |
| `POST /install-config` | 写 `InstallConfig`（HOME 包白名单） |
| `POST /config-write` | 通用配置行写入（AppBootPolicy / bootStagedStartupConfig / InstallConfig / Guard_disable_App） |
| `POST /provision` | **一键恢复**：Guard + InstallConfig + AppBootPolicy 一次写完 |
| `POST /cyberui-messages/*` | 灵控桌面消息表读写 |

所有写入都是 compare-and-set：先读当前原文算 SHA256，请求里带上 `expected_sha256`，写完再读回校验，不匹配就报错而不是硬写。

## 构建

需要 JDK 17 和 Android SDK（platform 34 + build-tools 34.0.0）：

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

产物是 `ffalcontrol.apk`，默认用 `~/.android/debug.keystore` 签名。要用自己的 key：

```sh
KS=my.p12 KS_PASS=pass:xxx KS_ALIAS=myalias ./build.sh
```

没有 Gradle，`build.sh` 直接走 `javac → d8 → aapt2 → zipalign → apksigner`。

## 界面按钮

五个按钮横向排在一行：

| 按钮 | 行为 |
|---|---|
| **刷新诊断** | 只读地重新读取状态并刷新摘要 |
| **一键恢复** | 写 `Guard_disable_App`（22 包 + 41 组件）、追加 `AppBootPolicy` 的 44 条拦截、把 `app.orchard.tv` 加进 `InstallConfig.launcherStrategy`；若三行都已是目标状态，直接提示"已经执行过，无需重复执行"而不重写 |
| **放行 aerial** | 把 `app.orchard.tv` 加进安装白名单 |
| **默认桌面** | 打开系统默认应用页 |
| **停止并退出** | 停止本机服务并退出 |

API token 每次安装随机生成，只打到 logcat，界面只显示前 8 位。

## 安装

电视上 `adb install` 通常会被 TCL 的安装校验拒绝（`INSTALL_FAILED_VERIFICATION_FAILURE`）。可行路径是：

1. APK 放进 U 盘；
2. U 盘插电视，用「电视卫士」安装；
3. 或者如果电视上有文件管理器，直接点开 APK。

装完打开一次，服务会监听 `127.0.0.1:18742`。

## 使用

令牌是每次安装随机生成的，没有编译进 APK。启动时打印到 logcat：

```sh
adb logcat -d | grep "FFalControl token"
```

拿到令牌后通过 adb 转发访问：

```sh
adb forward tcp:18742 tcp:18742
TOK=<token>
curl -s -H "Authorization: Bearer $TOK" http://127.0.0.1:18742/health
```

一键恢复：

```sh
curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d '{"level":"full","write":true}' http://127.0.0.1:18742/provision
```

然后重启电视，让 `com.tcl.guard` 在 `BOOT_COMPLETED` 执行一次包级禁用。

## 搭配使用：aerial-tv

ffalcontrol 负责"关掉什么"，桌面替换交给 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv)——一个参照 tvOS 设计的开源 Android TV 桌面，无广告、无推荐流，针对小米/海信/TCL 等国产电视做了适配。

两者组合的分工：

| 项目 | 负责 |
|---|---|
| **ffalcontrol** | 包级禁用（广告/遥测/OTA/内容入口）、AppBootPolicy 组件拦截、ConfigProvider 一键恢复 |
| **aerial-tv** | 替代灵控桌面，提供干净的 tvOS 风格首页 |

使用顺序：

1. 先用 ffalcontrol 的 `/provision` 关掉预装广告与推荐入口；
2. 装 aerial-tv（它的 `app.orchard.tv` 需要先在 `InstallConfig.launcherStrategy.packages` 里放行，ffalcontrol 的 `POST /install-config` 可以做）；
3. 在系统默认应用页把它设为 Home。

注意：MT9655 上 TCL 的 `WindowManager` 在 `systemReady` 时会硬编码启动 `com.tcl.cyberui`，所以即使 aerial 已是默认 Home，开机前台仍可能被灵控抢一次，按一下 Home 键即可回到 aerial。aerial 的 guard 版（`aerial-guard.apk`）带 `HomeGuardService`，是专门针对这个行为的尝试，但该版本不声明 HOME 类别，是否适用取决于机型。

## 原理与限制

- **包级禁用**走 `Guard_disable_App`，由 `com.tcl.guard` 执行，重启保持。
- **组件级禁用**同样写进 `Guard_disable_App`，但 guard 有一个 top-skip：执行时如果前台是 `com.tcl.cyberui`，会跳过整条组件级条目（防止把当前桌面禁掉导致黑屏）。所以组件级在开机时基本不生效。
- **Activity 拦截**改用 `AppBootPolicy.activityBlackList`——TCL 在 Activity 真正启动前拦截，不受 top-skip 影响。这是本项目里唯一稳定生效的组件级手段。
- **后台 Service**（如 `LauncherUpdateService`）既不在 AppBootPolicy 的黑名单语义里，也过不了 guard 的 top-skip，目前禁不掉。
- 预装应用**不能真删除**：它们在只读分区，TCL 的"可卸载白名单"是空的，`pm uninstall --user 0` 会失败。禁用是上限。

## 已知的 TCL 限制（MT9655）

- `WindowManager` 在 `systemReady` 时硬编码启动 `com.tcl.cyberui`，第三方 launcher 即使被设为默认 Home，开机前台也会被抢一次。
- `PackageManagerService` 禁止 shell 修改组件状态（`SecurityException: Shell cannot change component state`）。
- `DevicePolicyManager` 的 feature 被关闭（`mHasFeature=false`），无法设置 device owner。
- 后台启动 Activity（BAL）严格 enforce，`SYSTEM_ALERT_WINDOW`、AlarmClock PendingIntent、Device Owner 豁免都不能用。

## Responsible disclosure

本文描述的都是可公开验证的固件行为（`adb` + `dumpsys` 即可复现），不包含厂商密钥、签名材料或未修复漏洞的利用细节。如果厂商认为其中某项属于安全问题，欢迎通过 issue 联系，愿意配合调整描述或在后续固件中移除相关说明。

## 免责

这是一个直接操作系统配置的工具。写错配置可能导致桌面异常、应用无法启动，极端情况下需要恢复出厂。

- 仅用于你自己的设备；
- 每次写入前先 `GET` 读原文并备份 SHA；
- 出问题时的恢复手段：`/config-write` 写回原文，或者恢复出厂。

## License

MIT

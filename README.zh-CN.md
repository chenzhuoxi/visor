<p align="center">
  <img src="docs/banner.png" alt="Visor：去 Meta 化的 Meta 眼镜客户端" width="100%">
</p>

<p align="center">
  <b>一个替代 Meta AI 应用的 Meta 眼镜安卓客户端。</b><br>
  照片留在你的手机里，「Hey Meta」由你选定的模型回答。
</p>

<p align="center">
  <a href="https://github.com/chenzhuoxi/visor/releases/latest"><img src="https://img.shields.io/github/v/release/chenzhuoxi/visor?style=for-the-badge&label=%E4%B8%8B%E8%BD%BD&color=FFD2A1&labelColor=161617&v=1" alt="下载最新版本"></a>
  <a href="https://github.com/macedonga/visor"><img src="https://img.shields.io/badge/%E4%B8%8A%E6%B8%B8-macedonga%2Fvisor-FFD2A1?style=for-the-badge&labelColor=161617" alt="上游仓库"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-AGPL--3.0-FFD2A1?style=for-the-badge&labelColor=161617" alt="许可证：AGPL-3.0"></a>
</p>

<p align="center">
  <a href="#它能做什么">功能</a> |
  <a href="#本分支">本分支</a> |
  <a href="#安装">安装</a> |
  <a href="#它会发送什么发到哪里">隐私</a> |
  <a href="#使用须知">须知</a> |
  <a href="#从源码构建">构建</a>
</p>

Visor 支持 Ray-Ban Meta、Oakley Meta 以及其他 Meta 眼镜。它通过蓝牙和 Wi-Fi Direct 直接说眼镜自己的协议。

> [!NOTE]
> 这是上游 [macedonga/visor](https://github.com/macedonga/visor) 的**中文汉化分支**，由社区维护，非官方。Visor 本身是独立项目，与 Meta Platforms、EssilorLuxottica 无任何关联、背书或赞助关系。
>
> **English** speakers: this fork is upstream Visor with a Chinese interface. Upstream documentation is in [README.md](README.md).

---

## 本分支

| | |
|---|---|
| **上游** | [macedonga/visor](https://github.com/macedonga/visor)，当前同步到 **v1.4.9** |
| **内容** | 界面完整汉化 + 上游原版功能，**不含任何语音路径的行为改动** |
| **分支** | `zh-1.4.9` |
| **中文语音** | ⚠️ **待补充，尚未实现** —— 原因见 [`docs/chinese-tts.md`](docs/chinese-tts.md) |

### 汉化范围

- **界面字符串**：`values-zh/strings.xml`，935 条字符串 + 13 组复数，与英文源逐键对齐
- **眼镜设置目录**：`settings_ui.json` 的 `label_zh` / `note_zh` / `detail_zh`，设置页的每个条目名与说明都是中文
- **Kotlin 里的硬编码文案**：设备页、功能开关页、手机控制、日程编辑器、结构化编辑器、唤醒词等，全部转为资源
- **音色表**：语言与音色的中文名称（如「音调高，柔和」「音调低，深沉」）
- **上游 1.4.9 新增内容**：开发者模式令牌等三个字符串一并翻译

### 关于中文语音

**眼镜自己的声音目前念不了中文**，这是固件层面的限制，不是本项目能修的。三条路都已试过并留下日志证据：

1. 中文没有 classic path（固件参数名册里没有 `aipc_enable_zh_cn`，只有 `sci_enable_zh_cn`）
2. 用中文音色时每一轮都是 session 回合（`5=7`），那条路上眼镜从不念手机发来的文本
3. 英文音色 + 在语音请求里指定中文音色，固件会忽略该指定、仍用英文音色念 → 只听得到语气词、没有汉字

可行的替代方向是**手机合成语音 + 蓝牙出声**（听感上和眼镜自己说话一致）。完整的机制分析、日志证据和实现要点都记在 [`docs/chinese-tts.md`](docs/chinese-tts.md)，将来要做的时候从那里接手即可，不必重新排查。

---

## 它能做什么

| 功能 | 说明 |
|---|---|
| **首页** | 眼镜的 3D 模型，灯光显示状态：已连接、充电中、休眠、录像中、超出范围。下方是两块电池、剩余空间、音量、温度和最近的照片。 |
| **相册** | 通过 Wi-Fi Direct 导入照片和视频，可在眼镜充电时自动进行，也可手动触发。Live 照片会转成动态照片。文件存到 `Pictures/Visor` 和 `Movies/Visor`。 |
| **实时取景** | 在手机上看眼镜的摄像头画面，支持录像、回看最近几分钟、截图、RTMP 直播和 Live AI。 |
| **助手** | 「Hey Meta」交给你的模型：Claude，或任何 OpenAI 兼容的接口。手机上的日记会记录每段对话，可以在应用里继续。 |
| **通知** | 消息通知转发到眼镜，眼镜会用通讯录里的名字念出发件人。 |
| **设置** | 眼镜的全部设置项，和 Meta 应用里的呈现方式一致。高级设置里还有助手语言包和固件的功能开关。 |
| **应用之外** | 桌面小组件、快捷设置磁贴、启动器快捷方式，以及实时取景运行时的实时更新通知。 |
| **开发者模式** | 其他应用和自动化可以拍照录像、导入拍摄内容、获知新内容。见 [开发者模式](docs/developer-mode.md)。 |

## 要求

- **眼镜**：一副 Meta 眼镜，例如 Ray-Ban Meta 或 Oakley Meta。对 Meta Ray-Ban Display 的支持有限，在 [`display`](https://github.com/macedonga/visor/tree/display) 分支。
- **手机**：Android 10 或更高。
- **Meta 账号**，用于初始化。用密码或邮箱验证码登录，通行密钥（passkey）不可用。
- **助手**：Anthropic 或 OpenAI 兼容服务的 API key。

## 安装

1. 从[最新发布](https://github.com/chenzhuoxi/visor/releases/latest)下载 `Visor-<版本>.apk`。
2. 和旁边的 `.sha256` 文件比对 SHA-256。
3. 安装 APK。Android 会要求你允许来自浏览器或文件管理器的安装。
4. 打开 Visor，按引导完成设置：权限、Meta 登录、配对，然后选择你的眼镜。

> [!TIP]
> 让眼镜进入配对模式：**折叠两根镜腿，按住拍摄键约 5 秒**，镜框内侧的 LED 会蓝色闪烁。

<details>
<summary>发布版签名证书</summary>

```
CN=Visor
SHA-256: F6:01:C7:FE:63:EF:04:E0:7F:66:8A:EC:BA:5A:2B:9C:23:90:C4:C0:22:B5:2B:5F:B0:F6:D3:04:22:7C:B9:C7
```

> 注意：本分支的构建用的是调试签名（与上游发布版不同），**只能覆盖安装同来源的包**。要从本分支的包换到上游官方包，或反过来，需要先卸载再装——卸载会清掉手机里的登录信息和眼镜密钥，需要重新设置。

</details>

## 它会发送什么、发到哪里

Visor 没有自己的服务器，没有埋点，也没有崩溃上报。它只会连接下面这些：

| 去向 | 何时 | 用途 |
|---|---|---|
| Meta（`accounts.meta.com`、`graph.facebook-hardware.com`） | 设置过程中 | 登录，以及让 Visor 成为眼镜归属应用的凭证 |
| Meta（`ar-genai.graph.meta.com`） | 需要时 | 你这副眼镜的镜框款式和镜片颜色，以及助手语言包 |
| 你的模型服务商 | 你问助手时 | 你的问题，用你自己的 API key |
| 你的 RTMP 服务器 | 你从实时取景直播时 | 直播流 |
| GitHub（`api.github.com`） | 每天两次 | 检查最新版本，提醒你有更新。可在「设置 → 关于」里关闭。 |

其余一切都在手机和眼镜之间。

## 使用须知

> [!IMPORTANT]
> 眼镜同一时间只接受一个会话。如果 Meta AI 应用连上它，Visor 的会话就结束，反之亦然。

- **自己构建的包和发布版不能混装。** 自建包的签名和发布版不同，装其中一个前要先卸载另一个。卸载会清掉手机里的登录信息和眼镜密钥，需要重新设置。
- **第一次「Hey Meta」可能听不到。** 为了让眼镜用自身声音念英文回答，Visor 连接时有时会关掉 Meta 的某项设置并重启眼镜助手。
- **助手的好坏取决于你选的模型。** Visor 把问题、最近的对话，以及（当你问到眼前所见时）眼镜拍下的照片发给它。

## 从源码构建

需要 JDK 21（Android Studio 自带的那个就行）和带 platform 37 的 Android SDK。

```
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

> [!WARNING]
> 设置流程用 Meta AI 应用自己的客户端令牌登录 Meta，而这些令牌不在仓库里。没有它们，Visor 能构建、能运行，但 Meta 登录和设置里的归属步骤会失败。

<details>
<summary>添加 Meta 客户端令牌</summary>

从官方 Meta AI 应用中提取令牌，它们是以 `HW|`、`AR|` 和 `FRL|` 开头的字符串。构建前加到 `local.properties`：

```
VISOR_META_HW_TOKEN=HW|...
VISOR_META_AR_TOKEN=AR|...
VISOR_META_FRL_TOKEN=FRL|...
```

CI 的发布构建从仓库 secrets 里读取同名的值。

</details>

<details>
<summary>调试包与发布包</summary>

调试包会把每一帧写到手机上的 `files/wire.log`：

```
adb exec-out run-as com.macedonga.visor cat files/wire.log
```

调试包还有一个可以用 adb 驱动应用的广播接收器（`app/src/debug`），发布包两者都没有。

打一个形如 `v1.2.3` 的标签，CI 就会构建签名的发布 APK 并作为 GitHub 发布（`.github/workflows/release.yml`）。

</details>

<details>
<summary>代码结构</summary>

| 模块 | 内容 |
|---|---|
| `core/protocol` | 线上格式、加密、握手消息。纯 JVM。 |
| `core/transport` | RFCOMM 字节管道和线上日志。 |
| `core/session` | 链路：握手、通道复用、重连梯度。 |
| `core/identity` | 归属密钥库、信任、Meta 登录、归属变更、语言包。 |
| `core/connection` | `GlassesConnection` 和 `GlassesState`，设备状态。 |
| `core/media` | 媒体导入、实时取景、眼镜的 MobileConfig。 |
| `core/settings` | 眼镜设置和通讯录。 |
| `core/assistant` | 在眼镜一侧扮演 Meta 的助手服务器，接上你自己的模型。 |
| `core/design` | 颜色与排版。 |
| `app` | 服务、各页面、设置引导、小组件和快捷磁贴。 |

[CLAUDE.md](CLAUDE.md) 列出了最费时间才查清的协议陷阱。

</details>

## 参与

| 你有 | 去处 |
|---|---|
| 问题、想法，或想反馈 Visor 在你那副眼镜上的表现 | [Discussions](https://github.com/macedonga/visor/discussions) |
| Bug | [Issues](https://github.com/macedonga/visor/issues/new/choose)，请附上「设置 → 高级 → 事件日志」 |
| 无障碍问题 | [Issues](https://github.com/macedonga/visor/issues/new/choose) 里的无障碍表单。[ACCESSIBILITY.md](ACCESSIBILITY.md) 说明了哪些可用、哪些还没有。 |
| 安全问题 | 按 [SECURITY.md](SECURITY.md) 私下报告，**不要**公开发 issue。 |
| 想贡献代码 | 先看 [CONTRIBUTING.md](CONTRIBUTING.md)。项目没有测试套件：改动都是在真机上试出来的，PR 里要写清试过什么。 |

**汉化相关的问题**请提到本 fork（[chenzhuoxi/visor](https://github.com/chenzhuoxi/visor/issues)），功能与协议的问题请提给上游。

## 许可证

Visor 是自由软件，采用 [GNU Affero General Public License v3.0](LICENSE)。

Meta、Ray-Ban、Oakley、Ray-Ban Meta 和 Oakley Meta 是其所有者的商标。Visor 使用这些名称仅用于说明它支持哪些眼镜。

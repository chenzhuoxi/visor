# 中文语音输出（待补充）

**状态：未实现。** 1.4.9-zh1 只带界面汉化，语音链路保持 upstream 原样。

下面是 2026-11-03 在 1.4.8 系列 fork（zh11–zh21）上查清的结论，供将来做中文
TTS 时直接从这里接手，不必重新排查。

## 结论：眼镜自己的声音念不了中文

三条路都堵死了，且都有日志证据：

| 尝试 | 结果 | 证据 |
|---|---|---|
| 写 `wearables_ai_phone_call.aipc_enable_zh_cn` | 静默丢弃 | 固件参数名册 `mobileconfig/maps/current/merged_params_names_v4_u0.txt` 里没有这个 key；`aipc_enable_*` 只有 13 个 locale，不含 zh/ja/ko |
| 用中文 `assistant_voice_id`（`zh_CN.0` / `zh_CN.1`） | 完全静音 | 回合恒为 `5=7`（session），那条路上眼镜从不念手机发来的文本 |
| 英文 `assistant_voice_id` + VARM 写 `zh_CN.0` | 只有语气词，没有汉字 | `in the VARM: zh_CN.0` 证明固件收到了中文音色，但它忽略之、用 `assistant_voice_id` 的英文音色念 |

## 机制备忘

- `start interaction {… 5=N}` 的 **N 是回合类型**：`0` 普通（会念），`7` session（不念）。
  N 由眼镜自己的 `assistant_voice_id` 决定：英文 → 0，中文 → 7。**读日志先看这一位。**
- classic path（会念手机文本的那条）按语言逐个开关，key 形如
  `aipc_enable_<locale>`；中文在固件里只有 `sci_enable_zh_cn`（session path），
  没有 aipc 对应项。`aipc_arbitration = true` 是两者之间的仲裁器。
- VARM（`Oxide.kt` 的 `varm()`）带 `speakRequestVoiceId` 槽位（模板里是 `en_US.58`），
  `Thrift.replaceStrings` **正确改写长度前缀**，拼接没问题 —— 固件只是不认它。
- 日志里三个音色要看全：`voice <想要的>, assistant <眼镜的>, in the VARM <实际发出的>`。
  少一个就分不清「拼接失败」和「固件不认」。

## 可行的方向（当时选了第 1 条）

1. **手机合成 + 蓝牙出声。** 手机 TTS 没有上述任何限制，音频经蓝牙仍从眼镜扬声器出来，
   听感上和眼镜自己说话一致。配合中文识别即为全中文链路。
   需要：`deliver()` 手机优先；manifest `<queries>` 里补
   `android.speech.RecognitionService`（否则 Android 11+ 报「手机没有语音识别」，
   和「装了 TTS 引擎却看不见」同一个包可见性坑）；`SPEECH_LANGUAGES` 加 `zh-CN`。
   以上三点在 fork 的 `v1.4.8-zh21` 里已经实现过，可直接搬。
2. 等 Meta 给中文开 `aipc_enable_zh_cn`。
3. 云端 TTS（MiniMax speech 等）合成后按音频下发 —— 但下发的是音频，仍受眼镜
   播放路径限制，需另查。

## 相关 fork 版本

`chenzhuoxi/visor` 的 `feature/chinese-localization` 分支（基于 1.4.8）保留了全部
尝试过的实现，zh11–zh21 的 commit message 逐版记录了当时的推理与纠偏。
`v1.4.8-zh21` 是中文链路可用的那版。

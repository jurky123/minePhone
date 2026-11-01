# BrowserLink 0.9.5 验证记录

2026-11-01，目标完整整合包「登神者：天阶咏叹 v1.6.1fix」、现有光影、窗口模式、Chrome 抖音视频、720p / 30 FPS / JPEG、后台流畅辅助小窗。

- Node 62 项测试、Java 27 项测试通过，离线 Gradle build 成功。包含临时限帧恢复、尊重更低上限、运行期间用户/其他模组修改上限，以及采集状态与发送帧来源验证。
- 原全局上限 260（菜单显示无限制）：实际游戏约 88–89 FPS，GPU 利用率 99–100%，HUD 网页接收 4.9–5.8 FPS、纹理更新 4.0–4.8 FPS。
- 全局限制 60：实际游戏 60 FPS，GPU 75–77%，网页接收 17.1–19.0 FPS、纹理更新 17.1–18.0 FPS。
- 全局限制 40：实际游戏 40 FPS，GPU 约 56%，网页接收 21.9–24.8 FPS、纹理更新 20.9–23.8 FPS。
- 上述是顺序进行的稀疏 HUD 读数，不是连续统计分位数；网页视频内容随播放变化。采样时固定镜头，60 / 40 两轮焦点日志分别 226 / 216 条，均为游戏前台。GPU 指整卡利用率。
- 点击辅助小窗后游戏自动暂停，并发生视角变化，故该轮不能作为只改变焦点的严格对照。
- 这些数据支持 GPU 资源竞争是重要因素，不能排除浏览器后台调度，也不能证明所有卡顿及长期断连均已解决。本轮尚未验证原版 F11 / 底层独占全屏。
- 安装 0.9.5 时恢复全局 maxFps:260，仅在 BrowserLink 显示配置启用 browserGameFps:40；旧模组和修改前配置位于实例 browserlink-backups/before-0.9.5。
- 正式安装后进入原存档，扩展自动重连，手机近屏成功显示原网页。40 档下 HUD 先后读取到 RX 22.5 / 25.5 / 28.1 FPS、纹理 22.5 / 21.6 / 27.1 FPS，游戏暖机后 39–40 FPS；一分钟焦点日志 212 条均为游戏前台。该轮没有连续帧率日志，不将这些读数表示为稳定区间。
- 在同一手机近屏界面关闭“网页流畅优先”，游戏恢复到 105 FPS，GPU 100%，网页 RX / 纹理均回落到 4.9 FPS；确认临时限制可解除。之后恢复 40 档。全局 options.txt 的 maxFps 仍为 260。

本机证据：work/focus095-cap60-play.csv、work/focus095-cap40-play.csv、work/focus095-installed.csv、work/node095-final.log、work/build095-final.log。以下保留此前独立 QA 测试，勿与完整整合包数据混为一谈。

---
# BrowserLink 0.9.4 验证记录

2026-11-01，Windows / Chrome 当前登录配置 / Forge 1.20.1 独立 QA 世界。

- 59 项 Node 测试通过，包含发送拥塞恢复及停止后拒绝迟到调度；Gradle build 与 Java 测试通过。
- 本地 720p30 VP8 循环视频，按网页视口比例捕获 1280×580；JPEG 画质 82。
- 原版 F11，未使用 F6。采样期间不操作桌面；未修改系统全屏优化，因此不声称验证了底层独占呈现。
- 前四种短时实验和原始像素实验没有取得稳定收益，全部撤回。未压缩像素的流量可达约 85 MiB/s，明显高于 JPEG 的约 1.6 MiB/s。
- 最终辅助窗口 + JPEG 的五分钟数值来自 client094raw.log。该 QA 进程包含实验的 TGA 接收校验，但最终测试只发送 JPEG；正式包不包含 TGA 支持。
- Windows 桌面截图在原版全屏可能黑屏，性能结论依据实时游戏日志；不把静态截图当作持续流畅证明。
- 尚未完成完整目标整合包、真实视频网站、音画同步和长期偶发断线测试。零断连采样不等于断连问题彻底修复。

原始日志：work/client094b.log、work/client094raw.log。实验副本：work/fullscreen094-experiments。

```json
{
  "conditions": "QA world, F11 fullscreen, no PiP. Local 720p30 VP8, capture 1280x580. Short sequential samples, not a controlled long-duration benchmark.",
  "baseline": {
    "start": "14:14:05",
    "end": "14:14:55",
    "samples": 50,
    "median": 24.95,
    "min": 15.0,
    "p05": 17.9,
    "disconnected": 0,
    "staleOver1s": 0
  },
  "softwareCanvas": {
    "start": "14:15:15",
    "end": "14:16:00",
    "samples": 46,
    "median": 17.9,
    "min": 11.0,
    "p05": 11.9,
    "disconnected": 0,
    "staleOver1s": 0
  },
  "focusEmulation": {
    "start": "14:16:30",
    "end": "14:17:50",
    "samples": 81,
    "median": 24.0,
    "min": 14.9,
    "p05": 16.9,
    "disconnected": 0,
    "staleOver1s": 0
  },
  "synchronousCanvas": {
    "start": "14:18:30",
    "end": "14:19:30",
    "samples": 60,
    "median": 21.5,
    "min": 9.9,
    "p05": 13.9,
    "disconnected": 0,
    "staleOver1s": 0
  },
  "rawPixelsWorkerRetry": {
    "start": "14:26:20",
    "end": "14:26:55",
    "samples": 35,
    "median": 8.0,
    "min": 0.0,
    "p05": 2.0,
    "disconnected": 1,
    "staleOver1s": 1
  },
  "finalJpegWithHelperF11": {
    "start": "14:29:55",
    "end": "14:34:55",
    "samples": 300,
    "median": 29.9,
    "min": 22.8,
    "p05": 27.8,
    "disconnected": 0,
    "staleOver1s": 0
  }
}
```

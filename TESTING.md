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

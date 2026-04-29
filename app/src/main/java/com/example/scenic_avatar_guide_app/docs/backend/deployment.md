# 景灵智导后端 TTS 服务部署指南

版本：v1.0  
日期：2026-04-28  
适用范围：比赛演示、低成本中文数字人、无 GPU 部署

---

## 1. 方案概述

本项目采用 **Edge-TTS** 作为语音合成服务后端，为 Android 客户端提供：

- 文本转音频合成
- 音频文件缓存与分发
- 词级时间标记（marks）生成（用于口型同步）
- 流式问答中的短句分段音频生成（通过 `tts_segment` 事件下发）

架构简图：

```text
Android App
   │ POST /api/v1/tts/synthesize
   │ 或 POST /api/v1/chat/text/stream 中的 tts_segment
   ▼
FastAPI + Edge-TTS 服务
   │
   ├── 命中缓存 → 直接返回 audio_url
   └── 未命中 → 调用 Microsoft Edge TTS → 生成 mp3 → 缓存 → 返回 audio_url / tts_segment
```

---

## 2. 环境要求

| 项目 | 要求 |
|------|------|
| Python | 3.10 或 3.11 |
| 操作系统 | Windows 10+ / Ubuntu 20.04+ / macOS 12+ |
| 网络 | **服务器电脑必须可访问外网**（Edge-TTS 需连接 Microsoft 在线服务）；手机只需局域网连通服务器 |
| 磁盘 | 建议预留 2GB 用于音频缓存 |
| GPU | 不需要 |

---

## 3. 快速开始

### 3.1 创建项目目录

```bash
mkdir scenic-guide-backend
cd scenic-guide-backend
python -m venv venv

# Windows
venv\Scripts\activate

# Linux / macOS
source venv/bin/activate
```

### 3.2 安装依赖

创建 `requirements.txt`：

```txt
fastapi==0.115.0
uvicorn[standard]==0.32.0
edge-tts==7.0.0
aiofiles==24.1.0
python-multipart==0.0.17
```

安装：

```bash
pip install -r requirements.txt
```

### 3.3 验证 Edge-TTS

```bash
# 查看可用中文音色
edge-tts --list-voices | grep zh-CN

# 测试生成
edge-tts --text "您好，欢迎来到灵山胜境。" \
  --voice zh-CN-XiaoxiaoNeural \
  --write-media demo.mp3 \
  --write-subtitles demo.srt
```

若能正常生成 `demo.mp3`，说明环境就绪。

---

## 4. 目录结构

```text
scenic-guide-backend/
├── venv/                      # Python 虚拟环境
├── app/
│   ├── __init__.py
│   ├── main.py                # FastAPI 入口
│   ├── api/
│   │   ├── __init__.py
│   │   └── v1/
│   │       ├── __init__.py
│   │       ├── health.py      # 健康检查
│   │       └── tts.py         # TTS 接口
│   ├── services/
│   │   ├── __init__.py
│   │   └── tts_service.py     # TTS 业务逻辑、缓存管理
│   ├── integrations/
│   │   ├── __init__.py
│   │   └── tts/
│   │       ├── __init__.py
│   │       └── edge_tts_adapter.py  # edge-tts 调用封装
│   └── utils/
│       ├── __init__.py
│       └── audio_cache.py     # 音频缓存工具
├── storage/
│   └── audio/                 # 音频缓存目录
│       └── *.mp3
├── requirements.txt
└── run.sh / run.bat           # 启动脚本
```

---

## 5. 核心代码示例

### 5.1 FastAPI 主入口（app/main.py）

```python
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from app.api.v1 import health, tts

app = FastAPI(title="Scenic Guide TTS Service", version="1.0.0")

# 注册路由
app.include_router(health.router, prefix="/api/v1")
app.include_router(tts.router, prefix="/api/v1")

# 挂载音频文件目录，使 /api/v1/tts/file/*.mp3 可直接访问
import os
os.makedirs("storage/audio", exist_ok=True)
app.mount("/api/v1/tts/file", StaticFiles(directory="storage/audio"), name="audio_files")

@app.on_event("startup")
async def startup():
    print("TTS Service started. Audio cache: ./storage/audio")
```

### 5.2 TTS 接口（app/api/v1/tts.py）

```python
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List
from app.services.tts_service import TTSService

router = APIRouter()
tts_service = TTSService()

class SynthesizeRequest(BaseModel):
    text: str = Field(..., max_length=500)
    voice: str = "zh-CN-XiaoxiaoNeural"
    rate: str = "+0%"
    volume: str = "+0%"
    pitch: str = "+0Hz"
    format: str = "audio"  # audio | audio_with_marks

class MarkItem(BaseModel):
    text: str
    start_ms: int
    end_ms: int

class SynthesizeResponseData(BaseModel):
    audio_url: str
    duration_ms: int
    voice: str
    marks: Optional[List[MarkItem]] = None

@router.post("/tts/synthesize")
async def synthesize(req: SynthesizeRequest):
    try:
        result = await tts_service.synthesize(
            text=req.text,
            voice=req.voice,
            rate=req.rate,
            volume=req.volume,
            pitch=req.pitch,
            with_marks=(req.format == "audio_with_marks")
        )
        return {
            "code": 0,
            "message": "ok",
            "data": result
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/tts/voices")
async def list_voices():
    # 可预置常用中文音色，或动态调用 edge-tts 列表
    voices = [
        {"id": "zh-CN-XiaoxiaoNeural", "locale": "zh-CN", "gender": "Female", "friendly_name": "晓晓"},
        {"id": "zh-CN-YunyangNeural", "locale": "zh-CN", "gender": "Male", "friendly_name": "云扬"},
        {"id": "zh-CN-XiaoyiNeural", "locale": "zh-CN", "gender": "Female", "friendly_name": "晓伊"},
        {"id": "zh-CN-YunjianNeural", "locale": "zh-CN", "gender": "Male", "friendly_name": "云健"},
    ]
    return {"code": 0, "message": "ok", "data": voices}
```

### 5.3 TTS 服务层（app/services/tts_service.py）

```python
import os
import hashlib
import asyncio
from pathlib import Path
from typing import Optional, List, Dict
from app.integrations.tts.edge_tts_adapter import EdgeTTSAdapter

class TTSService:
    def __init__(self):
        self.cache_dir = Path("storage/audio")
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.adapter = EdgeTTSAdapter()

    def _get_cache_key(self, text: str, voice: str, rate: str, pitch: str, volume: str) -> str:
        content = f"{text}|{voice}|{rate}|{pitch}|{volume}"
        return hashlib.md5(content.encode("utf-8")).hexdigest()

    def _get_cache_path(self, cache_key: str) -> Path:
        return self.cache_dir / f"{cache_key}.mp3"

    async def synthesize(
        self,
        text: str,
        voice: str = "zh-CN-XiaoxiaoNeural",
        rate: str = "+0%",
        volume: str = "+0%",
        pitch: str = "+0Hz",
        with_marks: bool = False
    ) -> Dict:
        cache_key = self._get_cache_key(text, voice, rate, pitch, volume)
        cache_path = self._get_cache_path(cache_key)

        # 命中缓存
        if cache_path.exists():
            return self._build_response(cache_key, voice, duration_ms=0, marks=None)

        # 调用 edge-tts 生成
        await self.adapter.synthesize(
            text=text,
            voice=voice,
            rate=rate,
            volume=volume,
            pitch=pitch,
            output_path=str(cache_path)
        )

        # 估算时长（实际可用 ffprobe 或 mutagen 精确计算）
        duration_ms = self._estimate_duration(text)

        marks = None
        if with_marks:
            marks = self._generate_marks(text, duration_ms)

        return self._build_response(cache_key, voice, duration_ms, marks)

    def _build_response(self, cache_key: str, voice: str, duration_ms: int, marks: Optional[List]) -> Dict:
        return {
            "audio_url": f"/api/v1/tts/file/{cache_key}.mp3",
            "duration_ms": duration_ms,
            "voice": voice,
            "marks": marks
        }

    def _estimate_duration(self, text: str) -> int:
        # 粗略估算：中文约 180ms/字，标点 300ms
        duration = 0
        for ch in text:
            if ch in "，。！？、；：":
                duration += 300
            else:
                duration += 180
        return max(duration, 500)

    def _generate_marks(self, text: str, total_ms: int) -> List[Dict]:
        # 简易分词级 marks（实际可接入 jieba 分词）
        chars = list(text)
        marks = []
        ms_per_char = total_ms // max(len(chars), 1)
        i = 0
        while i < len(chars):
            word = chars[i]
            start = i * ms_per_char
            end = (i + 1) * ms_per_char
            marks.append({"text": word, "start_ms": start, "end_ms": end})
            i += 1
        return marks
```

### 5.4 Edge-TTS 适配器（app/integrations/tts/edge_tts_adapter.py）

```python
import edge_tts
import asyncio

class EdgeTTSAdapter:
    async def synthesize(
        self,
        text: str,
        voice: str,
        rate: str,
        volume: str,
        pitch: str,
        output_path: str
    ):
        communicate = edge_tts.Communicate(
            text=text,
            voice=voice,
            rate=rate,
            volume=volume,
            pitch=pitch
        )
        await communicate.save(output_path)
```

### 5.5 健康检查（app/api/v1/health.py）

```python
from fastapi import APIRouter

router = APIRouter()

@router.get("/health")
async def health():
    return {
        "code": 0,
        "message": "ok",
        "data": {
            "service": "scenic-guide-tts",
            "status": "running",
            "version": "1.0.0"
        }
    }
```

---

## 6. 启动服务

### 6.1 开发模式

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload
```

### 6.2 生产模式（比赛演示）

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8081 --workers 2
```

### 6.3 后台运行（Linux）

```bash
nohup uvicorn app.main:app --host 0.0.0.0 --port 8081 --workers 2 > tts.log 2>&1 &
```

---

## 7. Android 端配置

设置页配置示例：

| 配置项 | 示例值 | 说明 |
|--------|--------|------|
| 协议 | `http` | 局域网测试用 http |
| IP | `192.168.1.100` | 后端所在电脑 IP |
| 端口 | `8081` | 与 uvicorn 启动端口一致 |

完整 Base URL：`http://192.168.1.100:8081`

---

## 8. 缓存策略

### 8.1 缓存键生成

```python
md5(f"{text}|{voice}|{rate}|{pitch}|{volume}")
```

### 8.2 缓存清理

建议定期清理，保留最近 7 天文件：

```bash
# Linux / macOS
find storage/audio -name "*.mp3" -mtime +7 -delete
```

### 8.3 比赛演示建议

- 核心讲解词提前批量生成并保留缓存
- 现场问答走实时合成
- 这样既保证演示稳定，又支持开放性问答

---

## 9. 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| `edge-tts --list-voices` 无输出 | 外网不通 | 检查网络，Edge-TTS 需访问 Microsoft 服务 |
| 合成超时 | 文本过长 | 限制单条 text 最长 500 字 |
| 音频播放失败 | 文件路径错误 | 确认 `StaticFiles` 挂载正确，URL 拼接无误 |
| 缓存不生效 | 参数变化 | 检查 voice/rate/pitch/volume 是否一致 |
| 音色不存在 | 使用了不支持的 voice ID | 先用 `edge-tts --list-voices` 确认可用音色 |

---

## 10. 后续演进路线

| 阶段 | 目标 | 动作 |
|------|------|------|
| 第一阶段 | 最小可用 | 上述代码直接运行，Android 播放 `audio_url` |
| 流式阶段 | 低延迟体验 | 问答流返回 `tts_segment`，Android 分段排队播放 |
| 第二阶段 | 口型同步 | 后端返回 `marks`，Android 按时间驱动口型 |
| 第三阶段 | 缓存优化 | 预生成常用讲解词，减少实时请求 |
| 第四阶段 | 音色管理 | 管理后台动态配置默认音色列表 |
| 长期 | 商用替换 | 后端内部替换为 MeloTTS / CosyVoice，Android 接口不变 |

---

## 11. 参考

- Edge-TTS GitHub：`https://github.com/rany2/edge-tts`
- FastAPI 文档：`https://fastapi.tiangolo.com/`
- Android 端接口契约：`../api/API_CONTRACT.md`
- 流式重构方案：`../api/STREAMING_REFACTOR_PLAN.md`

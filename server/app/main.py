import os
import time
import json
import hashlib
import logging
import traceback
import functools
from pathlib import Path
from typing import Optional

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger("whisperx-server")

# Force CPU-only operation before any torch/whisperx imports
os.environ["CUDA_VISIBLE_DEVICES"] = ""
os.environ["TORCH_CUDA_ARCH_LIST"] = ""

import torch

# ---------------------------------------------------------
# PyTorch 2.6+ Compatibility Patch for WhisperX / Pyannote
# Pyannote checkpoints contain pickled omegaconf/lightning objects
# that fail under PyTorch's new strict weights_only=True default.
# ---------------------------------------------------------
_original_load = torch.load

@functools.wraps(_original_load)
def _patched_load(*args, **kwargs):
    # Unconditionally overwrite lightning_fabric's request for weights_only=True
    kwargs["weights_only"] = False
    return _original_load(*args, **kwargs)

torch.load = _patched_load
# ---------------------------------------------------------

import torchaudio

# TorchAudio 2.9+ removed torchaudio.list_audio_backends(), but
# pyannote.audio (up to at least 3.4.0) still calls it during import
# and expects at least one backend (preferring "soundfile").
# Provide a fallback that returns the available backends.
if not hasattr(torchaudio, "list_audio_backends"):
    try:
        import soundfile  # noqa: F401
        _backends = ["soundfile"]
    except ImportError:
        _backends = []
    torchaudio.list_audio_backends = lambda: _backends

# TorchAudio 2.9+ also removed torchaudio.AudioMetaData, but
# pyannote.audio 3.x still imports it. Provide a minimal shim.
if not hasattr(torchaudio, "AudioMetaData"):
    class AudioMetaData:
        def __init__(self, sample_rate, num_frames, num_channels, bits_per_sample, encoding):
            self.sample_rate = sample_rate
            self.num_frames = num_frames
            self.num_channels = num_channels
            self.bits_per_sample = bits_per_sample
            self.encoding = encoding
    torchaudio.AudioMetaData = AudioMetaData

import whisperx
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel

app = FastAPI(title="WhisperX Transcription Server")

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
DEVICE = os.getenv("DEVICE", "cpu")
# COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", "int8")
COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", "int8_float32")
MAX_UPLOAD_SIZE_MB = int(os.getenv("MAX_UPLOAD_SIZE_MB", "50"))
LLM_URL = os.getenv("LLM_URL", "")
HF_TOKEN = os.getenv("HF_TOKEN", "")
CACHE_DIR = Path(os.getenv("CACHE_DIR", "/root/.cache/huggingface"))

_model = None
_diarize_model = None


def get_model():
    global _model
    if _model is None:
        try:
            logger.info("Loading WhisperX model: %s on device=%s compute_type=%s", WHISPER_MODEL, DEVICE, COMPUTE_TYPE)
            _model = whisperx.load_model(
                WHISPER_MODEL,
                device=DEVICE,
                compute_type=COMPUTE_TYPE,
                download_root=str(CACHE_DIR),
            )
            logger.info("WhisperX model loaded successfully")
        # Catch BaseException (including SystemExit) to prevent process shutdown
        except BaseException as e:
            logger.error("Failed to load WhisperX model (caught %s)", type(e).__name__, exc_info=True)
            raise RuntimeError(
                f"Failed to load transcription model: {type(e).__name__}: {e}."
            ) from None
    return _model


class TranscribeResponse(BaseModel):
    text: str
    language: str
    duration_ms: int
    segments: list[dict]


class MetadataRequest(BaseModel):
    transcript: str
    duration_ms: int = 0
    language: str = "en"


class MetadataResponse(BaseModel):
    summary: str
    tags: list[str]
    notes: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/transcribe", response_model=TranscribeResponse)
async def transcribe(
    file: UploadFile = File(...),
    model: Optional[str] = Form(None),
    language: Optional[str] = Form(None),
    diarize: bool = Form(False),
):
    model_name = model or WHISPER_MODEL
    lang = language or "auto"

    content = await file.read()
    
    # 1. Reject 0-byte files
    if not content or len(content) == 0:
        raise HTTPException(status_code=400, detail="Uploaded audio file is empty (0 bytes)")

    size_mb = len(content) / (1024 * 1024)
    if size_mb > MAX_UPLOAD_SIZE_MB:
        raise HTTPException(status_code=413, detail=f"Upload size {size_mb:.1f}MB exceeds limit of {MAX_UPLOAD_SIZE_MB}MB")

    tmp_path = f"/tmp/{hashlib.sha256(content).hexdigest()}.audio"
    with open(tmp_path, "wb") as f:
        f.write(content)

    # 2. Validate audio header to prevent C++ divide-by-zero (SIGFPE / 136)
    tmp_path = f"/tmp/{hashlib.sha256(content).hexdigest()}.audio"
    with open(tmp_path, "wb") as f:
        f.write(content)

    # Validate audio using WhisperX's FFmpeg loader (handles OGG, Opus, M4A, MP3, WAV, etc.)
    try:
        audio_data = whisperx.load_audio(tmp_path)
        if len(audio_data) == 0:
            raise ValueError("Audio stream contains 0 samples")
    except Exception as e:
        Path(tmp_path).unlink(missing_ok=True)
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid, unsupported, or corrupted audio file: {e}"
        )

    try:
        start = time.time()
        try:
            whisper_model = get_model()
        except BaseException as e:
            logger.error("Model loading failed: %s", e)
            raise HTTPException(status_code=500, detail=str(e))
        
        logger.info("Starting transcription for file size=%.2fMB lang=%s", size_mb, lang)
        
        try:
            # Explicitly set batch_size=4 (or 1 for low RAM) instead of default 16
            result = whisper_model.transcribe(
                tmp_path, 
                batch_size=1, 
                language=lang if lang != "auto" else None
            )
        except BaseException as e:
            logger.error("WhisperX transcribe failed", exc_info=True)
            raise HTTPException(status_code=500, detail=f"Transcription failed: {e}")

        logger.info("Transcription completed: %d segments", len(result.get("segments", [])))

        segments = []
        for seg in result.get("segments", []):
            segments.append({
                "start_ms": int(seg["start"] * 1000),
                "end_ms": int(seg["end"] * 1000),
                "text": seg["text"].strip(),
            })

        full_text = " ".join(s["text"] for s in segments)
        detected_lang = result.get("language", lang)
        duration_ms = int((time.time() - start) * 1000)

        if diarize:
            if not HF_TOKEN:
                raise HTTPException(status_code=500, detail="Diarization requires HF_TOKEN environment variable")
            try:
                logger.info("Loading diarization pipeline")
                diarize_model = whisperx.DiarizationPipeline(use_auth_token=HF_TOKEN, device=DEVICE)
            except Exception as e:
                logger.error("Diarization model loading failed", exc_info=True)
                raise HTTPException(status_code=500, detail=f"Failed to load diarization model: {type(e).__name__}: {e}")
            logger.info("Running diarization")
            diarize_segments = diarize_model(tmp_path)
            result = whisperx.assign_word_speakers(diarize_segments, result)
            segments = []
            for seg in result.get("segments", []):
                speaker = seg.get("speaker", "SPEAKER_00")
                segments.append({
                    "start_ms": int(seg["start"] * 1000),
                    "end_ms": int(seg["end"] * 1000),
                    "text": seg["text"].strip(),
                    "speaker": speaker,
                })
            full_text = " ".join(s["text"] for s in segments)

        return TranscribeResponse(
            text=full_text,
            language=detected_lang,
            duration_ms=duration_ms,
            segments=segments,
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Transcription endpoint error", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Transcription failed: {type(e).__name__}: {e}")
    finally:
        Path(tmp_path).unlink(missing_ok=True)


@app.post("/v1/metadata", response_model=MetadataResponse)
def metadata(req: MetadataRequest):
    transcript = req.transcript.strip()
    if not transcript:
        return MetadataResponse(summary="", tags=[], notes="")

    if LLM_URL:
        try:
            import urllib.request
            payload = json.dumps({
                "model": "default",
                "messages": [
                    {"role": "user", "content": f"Summarize in one sentence and extract 5 tags. Transcript: {transcript[:2000]}"}
                ],
            }).encode()
            req_llm = urllib.request.Request(
                LLM_URL + "/v1/chat/completions",
                data=payload,
                headers={"Content-Type": "application/json"},
            )
            with urllib.request.urlopen(req_llm, timeout=30) as resp:
                data = json.loads(resp.read())
            content = data["choices"][0]["message"]["content"]
            summary = content.split("\n")[0].strip()
            tags = [w.strip(".,!?;:\"'()[]{}") for w in transcript.split() if 4 < len(w.strip(".,!?;:\"'()[]{}")) < 20][:5]
            return MetadataResponse(summary=summary, tags=tags, notes="")
        except Exception as e:
            logger.warning("LLM metadata generation failed: %s", e, exc_info=True)
            pass

    summary = transcript[:200] + ("..." if len(transcript) > 200 else "")
    words = transcript.split()
    tag_set = {}
    for w in words:
        cleaned = w.strip(".,!?;:\"'()[]{}").lower()
        if 4 < len(cleaned) < 20:
            tag_set[cleaned] = True
    tags = list(tag_set.keys())[:5]
    return MetadataResponse(summary=summary, tags=tags, notes="")

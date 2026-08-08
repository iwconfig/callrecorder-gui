import os
import time
import json
import hashlib
import logging
import traceback
from pathlib import Path
from typing import Optional

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger("whisperx-server")

# Force CPU-only operation before any torch/whisperx imports
os.environ["CUDA_VISIBLE_DEVICES"] = ""
os.environ["TORCH_CUDA_ARCH_LIST"] = ""

import torch
import torchaudio

import whisperx
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel

app = FastAPI(title="WhisperX Transcription Server")

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
DEVICE = os.getenv("DEVICE", "cpu")
COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", "int8")
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
        except Exception as e:
            logger.error("Failed to load WhisperX model", exc_info=True)
            raise RuntimeError(
                f"Failed to load transcription model: {type(e).__name__}: {e}. "
                "This may indicate incompatible package versions. "
                "Please ensure CPU-only PyTorch and compatible torchaudio/pyannote.audio are installed "
                "(e.g., reinstall with: uv sync --extra cpu)."
            ) from e
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
    size_mb = len(content) / (1024 * 1024)
    if size_mb > MAX_UPLOAD_SIZE_MB:
        raise HTTPException(status_code=413, detail=f"Upload size {size_mb:.1f}MB exceeds limit of {MAX_UPLOAD_SIZE_MB}MB")

    tmp_path = f"/tmp/{hashlib.sha256(content).hexdigest()}.audio"
    with open(tmp_path, "wb") as f:
        f.write(content)

    try:
        start = time.time()
        try:
            whisper_model = get_model()
        except RuntimeError as e:
            logger.error("Model loading failed: %s", e)
            raise HTTPException(status_code=500, detail=str(e))
        
        logger.info("Starting transcription for file size=%dMB lang=%s", size_mb, lang)
        result = whisper_model.transcribe(tmp_path, language=lang if lang != "auto" else None)
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

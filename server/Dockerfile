ARG PYTORCH_INDEX=https://download.pytorch.org/whl/cpu

FROM python:3.12-slim AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ffmpeg \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml .
RUN sed -i "s|https://download.pytorch.org/whl/cpu|${PYTORCH_INDEX}|" pyproject.toml && \
    pip install --no-cache-dir uv && \
    uv sync --extra cpu --frozen --no-dev

COPY . .

ARG BASE_IMAGE=python:3.12-slim
FROM ${BASE_IMAGE}

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/.venv .venv
COPY app/ app/
COPY pyproject.toml .

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

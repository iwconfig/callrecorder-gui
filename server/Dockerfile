ARG BASE_IMAGE=python:3.12-slim
ARG PYTORCH_INDEX=https://download.pytorch.org/whl/cpu

FROM ${BASE_IMAGE}

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ffmpeg \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml .
RUN sed -i "s|https://download.pytorch.org/whl/cpu|${PYTORCH_INDEX}|" pyproject.toml && \
    pip install --no-cache-dir uv && \
    uv sync --extra cpu --frozen --no-dev

COPY . .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

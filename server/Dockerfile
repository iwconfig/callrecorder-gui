ARG BASE_IMAGE=python:3.12-slim
ARG UV_EXTRA=cpu

FROM ${BASE_IMAGE}

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ffmpeg \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml .
RUN pip install --no-cache-dir uv && \
    uv sync --extra ${UV_EXTRA} --frozen --no-dev

COPY . .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

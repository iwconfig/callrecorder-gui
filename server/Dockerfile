ARG BASE_IMAGE=python:3.14-slim
ARG UV_EXTRAS=cpu

FROM python:3.14-slim AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ffmpeg \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pyproject.toml uv.lock .
RUN pip install --no-cache-dir uv && \
    uv sync --extra ${UV_EXTRAS} --no-dev

COPY . .

FROM ${BASE_IMAGE}
ARG UV_EXTRAS

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Install Intel oneAPI runtime for XPU support
RUN if [ "$UV_EXTRAS" = "xpu" ]; then \
        apt-get update && apt-get install -y --no-install-recommends \
        wget gnupg \
        && wget -qO - https://apt.repos.intel.com/keys/intel-gpg-key.asc | gpg --dearmor -o /usr/share/keyrings/intel-gpg-key.gpg \
        && echo "deb [signed-by=/usr/share/keyrings/intel-gpg-key.gpg] https://apt.repos.intel.com/oneapi all main" > /etc/apt/sources.list.d/intel-oneapi.list \
        && apt-get update && apt-get install -y --no-install-recommends \
        intel-oneapi-runtime-level-zero \
        && rm -rf /var/lib/apt/lists/* \
    ; fi

WORKDIR /app

COPY --from=builder /app/.venv .venv
COPY app/ app/
COPY pyproject.toml .

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

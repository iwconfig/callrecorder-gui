ARG BASE_IMAGE=python:3.12-slim

# ==========================================
# Stage 1: Build Environment
# ==========================================
FROM ${BASE_IMAGE} AS builder
ARG UV_EXTRAS=cpu

# uv best practices environment variables
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PYTHON=python3.12

# Install uv directly from the official image
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ffmpeg \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Dependency installation strategy (Optimal layer caching)
# Uses --mount=type=bind to read files without adding them to the Docker layer
# Uses --mount=type=cache to dramatically speed up subsequent rebuilds
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=bind,source=uv.lock,target=uv.lock \
    --mount=type=bind,source=pyproject.toml,target=pyproject.toml \
    uv sync --locked --extra ${UV_EXTRAS} --no-install-project --no-dev

# Copy application code and perform final sync
COPY . /app
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --extra ${UV_EXTRAS} --no-dev


# ==========================================
# Stage 2: Runtime Environment
# ==========================================
FROM ${BASE_IMAGE}

# Install runtime system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libsndfile1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the entire app directory (including the .venv) from the builder stage
COPY --from=builder /app /app

# Place executables in the environment at the front of the PATH
ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM python:3.12.9-slim@sha256:48a11b7ba705fd53bf15248d1f94d36c39549903c5d59edcfa2f3f84126e7b44

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app
COPY pyproject.toml ./
COPY app ./app
COPY alembic.ini ./
COPY migrations ./migrations
COPY entrypoint.sh ./
RUN pip install --no-build-isolation .
RUN useradd --create-home --uid 10001 ashwake
USER 10001
EXPOSE 8080
CMD ["sh", "entrypoint.sh"]

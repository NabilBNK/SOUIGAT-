# SOUIGAT — Internal Financial Management System

> Intercity bus & cargo operations platform with offline-first mobile capability.

## Architecture

See [souigat-architecture.md](./souigat-architecture.md) for the full v3.1 architecture plan.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Django 5.x + DRF 3.15 |
| Database | PostgreSQL 16 |
| Web Frontend | React 19 + Vite 6 + TypeScript |
| Mobile | Kotlin + Room + Retrofit + WorkManager |
| Auth | JWT (simplejwt) — 15min access, 7d refresh |
| Task Queue | Celery + Redis |
| Cache | Redis |
| Dev Env | Docker Compose |

## Quick Start

### Prerequisites

- Docker Desktop + WSL 2
- Git (`git config --global core.autocrlf input`)

### Setup

```bash
# 1. Copy and configure environment
cp .env.example .env

# 2. Generate secrets (run each, paste into .env)
python -c "import secrets; print(secrets.token_urlsafe(32))"  # → DB_PASSWORD
python -c "import secrets; print(secrets.token_urlsafe(50))"  # → DJANGO_SECRET_KEY

# 3. Start services
docker compose up -d --build

# 4. Run migrations
docker compose exec backend python manage.py migrate

# 5. Create admin user
docker compose exec backend python manage.py createsuperuser

# 6. Verify
curl http://localhost:8000/api/   # → {"status": "ok"}
```

### Services

| Service | Port | Purpose |
|---------|------|---------|
| backend | 8000 | Django + Gunicorn (4 workers) |
| db | 5432 (localhost only) | PostgreSQL 16 |
| redis | internal only | Cache + Celery broker |
| celery | — | Async task worker |
| celery-beat | — | Periodic task scheduler |

## Project Structure

```
SOUIGAT/
├── backend/          # Django REST API
│   ├── souigat/      # Project config (settings, celery, urls)
│   └── api/          # DRF app (models, views, serializers)
├── web/              # React + Vite (Phase 2)
├── mobile/           # Kotlin Android (Phase 3)
└── docker-compose.yml
```

## License

Internal use only.

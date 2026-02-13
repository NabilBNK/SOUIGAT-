# Phase 0: Setup & Infrastructure

## Goal
Working Docker stack with Django + Gunicorn, PostgreSQL, Redis, Celery worker + beat — production-grade from day 1.

---

## Pre-Requisites (One-Time)

```powershell
# Fix Windows line endings (prevents bash script failures in Docker)
git config --global core.autocrlf input
```

---

## Tasks

### Day 1-2: Project Structure

- [ ] **T0.1**: Create folder structure
  ```
  SOUIGAT/
  ├── backend/
  │   ├── Dockerfile
  │   ├── .dockerignore
  │   ├── requirements.txt
  │   ├── manage.py
  │   ├── souigat/
  │   │   ├── __init__.py
  │   │   ├── settings.py
  │   │   ├── urls.py
  │   │   ├── celery.py
  │   │   └── wsgi.py
  │   └── api/
  │       ├── __init__.py
  │       └── apps.py
  ├── web/                  # Phase 2 placeholder
  │   └── .gitkeep
  ├── mobile/               # Phase 3 placeholder
  │   └── .gitkeep
  ├── docker-compose.yml
  ├── .env.example
  ├── .gitignore
  └── README.md
  ```
  → Verify: `ls backend/souigat/settings.py` exists

- [ ] **T0.2**: Initialize Git repo + first commit
  ```powershell
  cd C:\Users\Perfetto\Desktop\SOUIGAT
  git init
  git add .
  git commit -m "chore: initial project structure"
  ```
  → Verify: `git log --oneline` shows 1 commit

---

### Day 3-4: Docker Infrastructure

- [ ] **T0.3**: Create `docker-compose.yml` (5 services)
  ```yaml
  services:
    db:
      image: postgres:16-alpine
      environment:
        POSTGRES_DB: souigat_db
        POSTGRES_USER: souigat_user
        POSTGRES_PASSWORD: ${DB_PASSWORD}
      ports: ["127.0.0.1:5432:5432"]    # Localhost only — never 0.0.0.0
      volumes: [pgdata:/var/lib/postgresql/data]
      healthcheck:
        test: pg_isready -U souigat_user -d souigat_db
        interval: 5s
        retries: 5

    redis:
      image: redis:7-alpine
      # No ports exposed — only accessible via Docker network
      healthcheck:
        test: redis-cli ping
        interval: 5s
        retries: 5

    backend:
      build: ./backend
      command: >
        gunicorn souigat.wsgi:application
        --bind 0.0.0.0:8000
        --workers 4
        --timeout 120
        --reload  
      # NOTE: --reload is DEV MODE only. For production: remove --reload, add --preload
      ports: ["8000:8000"]
      volumes: [./backend:/app]
      env_file: .env
      depends_on:
        db: { condition: service_healthy }
        redis: { condition: service_healthy }

    celery:
      build: ./backend
      command: >
        celery -A souigat worker
        -l info
        --concurrency=2
        --max-tasks-per-child=50
      volumes: [./backend:/app]
      env_file: .env
      depends_on:
        db: { condition: service_healthy }
        redis: { condition: service_healthy }
      deploy:
        resources:
          limits:
            memory: 512M

    celery-beat:
      build: ./backend
      command: celery -A souigat beat -l info --scheduler django_celery_beat.schedulers:DatabaseScheduler
      volumes: [./backend:/app]
      env_file: .env
      depends_on:
        db: { condition: service_healthy }
        redis: { condition: service_healthy }

  volumes:
    pgdata:
  ```
  → Verify: file exists, YAML valid (`docker compose config`)

- [ ] **T0.4**: Create `backend/Dockerfile` + `.dockerignore`

  **Dockerfile:**
  ```dockerfile
  FROM python:3.12-slim

  ENV PYTHONDONTWRITEBYTECODE=1 \
      PYTHONUNBUFFERED=1

  WORKDIR /app

  # Install system deps for psycopg
  RUN apt-get update && apt-get install -y --no-install-recommends \
      libpq-dev gcc && \
      rm -rf /var/lib/apt/lists/*

  COPY requirements.txt .
  RUN pip install --no-cache-dir -r requirements.txt

  COPY . .
  ```

  **`.dockerignore`:**
  ```
  __pycache__/
  *.pyc
  *.pyo
  .env
  .git/
  .gitignore
  .vscode/
  *.md
  .mypy_cache/
  .pytest_cache/
  ```
  → Verify: `docker build ./backend` succeeds, image < 300MB

- [ ] **T0.5**: Create `backend/requirements.txt`
  ```
  Django>=5.1,<6.0
  djangorestframework>=3.15,<4.0
  djangorestframework-simplejwt>=5.3,<6.0
  django-cors-headers>=4.3,<5.0
  django-filter>=24.0
  django-celery-beat>=2.6,<3.0
  psycopg[binary]>=3.1,<4.0
  celery[redis]>=5.4,<6.0
  redis>=5.0,<6.0
  openpyxl>=3.1,<4.0
  gunicorn>=22.0,<23.0
  python-decouple>=3.8
  ```
  → Verify: 12 packages listed

- [ ] **T0.6**: Create `.env.example` + generate `.env`

  **`.env.example`:**
  ```env
  # ⚠️ SECURITY: Generate secrets before use!
  # Run: python -c "import secrets; print(secrets.token_urlsafe(32))"

  # Database
  POSTGRES_DB=souigat_db
  POSTGRES_USER=souigat_user
  DB_PASSWORD=GENERATE_STRONG_PASSWORD_HERE

  # Django
  DJANGO_SECRET_KEY=GENERATE_50_CHAR_SECRET_HERE
  DJANGO_DEBUG=True
  DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1
  # Production: DJANGO_ALLOWED_HOSTS=souigat.example.dz

  # Redis (internal Docker network — no host exposure)
  REDIS_URL=redis://redis:6379/0

  # Celery
  CELERY_BROKER_URL=redis://redis:6379/0
  CELERY_RESULT_BACKEND=redis://redis:6379/1
  ```

  **Generate `.env`:**
  ```powershell
  # Copy template
  cp .env.example .env

  # Generate secrets (run each, paste into .env)
  python -c "import secrets; print(secrets.token_urlsafe(32))"  # → DB_PASSWORD
  python -c "import secrets; print(secrets.token_urlsafe(50))"  # → DJANGO_SECRET_KEY
  ```
  → Verify: `.env` has generated passwords (not placeholders), `.gitignore` excludes `.env`

---

### Day 4-5: Django Bootstrap

- [ ] **T0.7**: Create Django project + `api` app
  ```python
  # souigat/settings.py — key sections:

  from decouple import config
  from datetime import timedelta

  SECRET_KEY = config('DJANGO_SECRET_KEY')
  DEBUG = config('DJANGO_DEBUG', default=False, cast=bool)
  ALLOWED_HOSTS = config('DJANGO_ALLOWED_HOSTS', default='', cast=lambda v: v.split(','))

  INSTALLED_APPS = [
      'django.contrib.admin',
      'django.contrib.auth',
      'django.contrib.contenttypes',
      'django.contrib.sessions',
      'django.contrib.messages',
      'django.contrib.staticfiles',
      # Third-party
      'rest_framework',
      'corsheaders',
      'rest_framework_simplejwt.token_blacklist',
      'django_filters',
      'django_celery_beat',
      # Local
      'api',
  ]

  MIDDLEWARE = [
      'corsheaders.middleware.CorsMiddleware',       # ← FIRST (before SecurityMiddleware)
      'django.middleware.security.SecurityMiddleware',
      'django.contrib.sessions.middleware.SessionMiddleware',
      'django.middleware.common.CommonMiddleware',
      'django.middleware.csrf.CsrfViewMiddleware',
      'django.contrib.auth.middleware.AuthenticationMiddleware',
      'django.contrib.messages.middleware.MessageMiddleware',
      'django.middleware.clickjacking.XFrameOptionsMiddleware',
  ]

  # Database (python-decouple, no dj-database-url needed)
  DATABASES = {
      'default': {
          'ENGINE': 'django.db.backends.postgresql',
          'NAME': config('POSTGRES_DB'),
          'USER': config('POSTGRES_USER'),
          'PASSWORD': config('DB_PASSWORD'),
          'HOST': 'db',
          'PORT': '5432',
      }
  }

  # DRF
  REST_FRAMEWORK = {
      'DEFAULT_AUTHENTICATION_CLASSES': [
          'rest_framework_simplejwt.authentication.JWTAuthentication',
      ],
      'DEFAULT_THROTTLE_CLASSES': [
          'rest_framework.throttling.AnonRateThrottle',
          'rest_framework.throttling.UserRateThrottle',
      ],
      'DEFAULT_THROTTLE_RATES': {
          'anon': '10/minute',
          'user': '1000/hour',
      },
      'DEFAULT_FILTER_BACKENDS': [
          'django_filters.rest_framework.DjangoFilterBackend',
      ],
  }

  # JWT
  SIMPLE_JWT = {
      'ACCESS_TOKEN_LIFETIME': timedelta(minutes=15),
      'REFRESH_TOKEN_LIFETIME': timedelta(days=7),
  }

  # CORS (Phase 2: React on :5173)
  CORS_ALLOWED_ORIGINS = [
      'http://localhost:5173',
      'http://127.0.0.1:5173',
  ]

  # Celery
  CELERY_BROKER_URL = config('CELERY_BROKER_URL')
  CELERY_RESULT_BACKEND = config('CELERY_RESULT_BACKEND')
  CELERY_ACCEPT_CONTENT = ['json']
  CELERY_TASK_SERIALIZER = 'json'
  CELERY_RESULT_SERIALIZER = 'json'

  # Cache (Redis)
  CACHES = {
      'default': {
          'BACKEND': 'django.core.cache.backends.redis.RedisCache',
          'LOCATION': config('REDIS_URL'),
      }
  }
  ```
  → Verify: `python manage.py check` returns no errors

- [ ] **T0.8**: Create `souigat/celery.py` + `souigat/__init__.py`

  **`celery.py`:**
  ```python
  import os
  from celery import Celery

  os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
  app = Celery('souigat')
  app.config_from_object('django.conf:settings', namespace='CELERY')
  app.autodiscover_tasks()
  ```

  **`__init__.py`:**
  ```python
  from .celery import app as celery_app
  __all__ = ('celery_app',)
  ```
  → Verify: `docker compose logs celery` shows "celery@... ready"

---

### Day 5: Smoke Test

- [ ] **T0.9**: Start full stack + verify
  ```powershell
  docker compose up -d --build
  docker compose ps                                              # 5 services "Up (healthy)"
  docker compose exec backend python manage.py migrate           # Creates default tables
  docker compose exec backend python manage.py createsuperuser   # Create admin user
  ```

  **Verification checklist:**
  ```powershell
  # 1. All services healthy
  docker compose ps
  # Expected: db, redis, backend, celery, celery-beat all "Up"

  # 2. Django responds
  curl http://localhost:8000/admin/
  # Expected: HTML login page (200 OK)

  # 3. Concurrent requests work (proves gunicorn multi-worker)
  # Open 2 browser tabs to http://localhost:8000/admin/ simultaneously
  # Both load instantly (runserver would serialize them)

  # 4. Celery worker active
  docker compose logs celery --tail 5
  # Expected: "celery@... ready" + "Connected to redis://redis:6379/0"

  # 5. Celery beat active
  docker compose logs celery-beat --tail 5
  # Expected: "beat: Starting..."

  # 6. Redis not exposed
  curl http://localhost:6379
  # Expected: Connection refused (Redis only on Docker network)

  # 7. PG localhost-only
  # Can connect via pgAdmin on 127.0.0.1:5432 ✅
  # Cannot connect from another machine on network ✅
  ```
  → Verify: All 7 checks pass

- [ ] **T0.10**: Git commit
  ```powershell
  git add .
  git commit -m "feat: Docker infrastructure (PG + Redis + Gunicorn + Celery + Beat)"
  ```
  → Verify: `git log --oneline` shows 2 commits

---

## Done When

- [ ] `docker compose up` starts **5** services (db, redis, backend, celery, celery-beat)
- [ ] `docker compose ps` shows all healthy
- [ ] `http://localhost:8000/admin/` loads (gunicorn, multi-worker)
- [ ] Celery worker + beat running
- [ ] Redis **not exposed** to host
- [ ] PostgreSQL bound to **127.0.0.1 only**
- [ ] `.env` has **generated** secrets (not placeholders)
- [ ] `.env.example` committed, `.env` gitignored
- [ ] CORS configured for `localhost:5173`
- [ ] Git repo with 2 commits

## Notes

- **No models yet** — that's Phase 1 (T1-T3)
- **No React/mobile code** — placeholder folders only
- All config uses `python-decouple` (env vars, not hardcoded)
- Docker volumes persist PG data across restarts
- `gunicorn --reload` enables hot-reload during development
- Celery limited to 2 concurrent workers + 512MB RAM cap

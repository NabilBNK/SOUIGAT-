# Friend Setup Checklist (Same Firebase)

Use this exact checklist to run the project on another PC without creating a new Firebase project.

1. Clone and switch to branch

```bash
git clone https://github.com/NabilBNK/SOUIGAT-.git
cd SOUIGAT-
git checkout friend-firebase-realtime-sync
```

2. Prepare web environment (Firebase already prefilled)

```bash
cd web
copy .env.example .env.local
npm install
```

3. Prepare backend environment (local admin-only usage)

```bash
cd ..\backend
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

4. Set backend Firebase env (only service-account path needed)

- In root `.env` (or backend runtime env), set:
  - `FIREBASE_PROJECT_ID=souigat-6be49`
  - `FIREBASE_SERVICE_ACCOUNT_PATH=<absolute_path_to_service_account_json>`

5. Run services

```bash
cd ..
dev-stack.bat start 8013 4003
```

6. Build and install mobile app

```bash
cd mobile
gradlew.bat installDebug
```

7. Verify quickly

- Web opens at `http://localhost:4003`
- Mobile app package `com.souigat.mobile.debug` installs and launches
- Firestore project in use is `souigat-6be49`

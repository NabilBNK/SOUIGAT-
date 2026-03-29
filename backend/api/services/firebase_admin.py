import json
import logging
import re
from threading import Lock, Thread

from decouple import config
from django.contrib.auth import get_user_model
from django.db import close_old_connections

try:
    import firebase_admin
    from firebase_admin import auth, credentials
except ModuleNotFoundError:  # pragma: no cover - safe fallback for partially provisioned envs
    firebase_admin = None
    auth = None
    credentials = None

logger = logging.getLogger(__name__)

_firebase_app = None
_firebase_app_lock = Lock()


class FirebaseConfigurationError(RuntimeError):
    """Raised when Firebase Admin SDK is not configured correctly."""


def _load_credentials():
    if credentials is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )

    service_account_json = config('FIREBASE_SERVICE_ACCOUNT_JSON', default='').strip()
    service_account_path = config('FIREBASE_SERVICE_ACCOUNT_PATH', default='').strip()

    if service_account_json:
        try:
            service_account_info = json.loads(service_account_json)
        except json.JSONDecodeError as exc:
            raise FirebaseConfigurationError(
                'FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON.',
            ) from exc
        return credentials.Certificate(service_account_info)

    if service_account_path:
        try:
            return credentials.Certificate(service_account_path)
        except Exception as exc:
            raise FirebaseConfigurationError(
                f'Unable to load service account file at {service_account_path}.',
            ) from exc

    google_application_credentials = config('GOOGLE_APPLICATION_CREDENTIALS', default='').strip()
    if google_application_credentials:
        return credentials.ApplicationDefault()

    raise FirebaseConfigurationError(
        'Firebase Admin is not configured. Set FIREBASE_SERVICE_ACCOUNT_PATH, FIREBASE_SERVICE_ACCOUNT_JSON, or GOOGLE_APPLICATION_CREDENTIALS.',
    )


def get_firebase_app():
    global _firebase_app

    if firebase_admin is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )

    if _firebase_app is not None:
        return _firebase_app

    with _firebase_app_lock:
        if _firebase_app is not None:
            return _firebase_app

        try:
            _firebase_app = firebase_admin.get_app()
            return _firebase_app
        except ValueError:
            pass

        creds = _load_credentials()
        options = {}
        project_id = config('FIREBASE_PROJECT_ID', default='').strip()
        if project_id:
            options['projectId'] = project_id

        _firebase_app = firebase_admin.initialize_app(
            creds,
            options=options if options else None,
        )
        logger.info('Firebase Admin SDK initialized successfully.')
        return _firebase_app


def build_firebase_claims_for_user(user, platform='web'):
    claims = {
        'user_id': int(user.id),
        'role': user.role,
        'sync_writer': user.role in ('admin', 'office_staff'),
        'platform': platform,
    }

    if user.office_id is not None and user.role != 'conductor':
        claims['office_id'] = int(user.office_id)

    if user.department:
        claims['department'] = user.department

    return claims


def firebase_uid_for_user(user) -> str:
    return f'souigat-user-{int(user.id)}'


def firebase_uid_for_user_id(user_id: int) -> str:
    return f'souigat-user-{int(user_id)}'


def firebase_email_for_phone(phone: str) -> str:
    digits = ''.join(ch for ch in str(phone or '') if ch.isdigit())
    if not digits:
        raise ValueError('Cannot build Firebase email: phone is empty or invalid.')
    return f'{digits}@accounts.souigat.local'


def extract_phone_from_firebase_email(email: str | None) -> str | None:
    if not email:
        return None
    match = re.fullmatch(r'(\d+)@accounts\.souigat\.local', email.strip().lower())
    if not match:
        return None
    return match.group(1)


def sync_firebase_auth_user(user, raw_password: str | None = None, allow_create: bool = True):
    app = get_firebase_app()
    if auth is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )

    uid = firebase_uid_for_user(user)
    email = firebase_email_for_phone(user.phone)
    display_name = user.get_full_name() or user.phone
    claims = build_firebase_claims_for_user(user, platform='mobile')

    try:
        auth.get_user(uid, app=app)
        update_kwargs = {
            'email': email,
            'display_name': display_name,
            'disabled': not bool(user.is_active),
        }
        if raw_password:
            update_kwargs['password'] = raw_password
        auth.update_user(uid, app=app, **update_kwargs)
        operation = 'updated'
    except auth.UserNotFoundError:
        if not allow_create:
            return {'uid': uid, 'operation': 'skipped_missing', 'email': email}

        if not raw_password:
            raise FirebaseConfigurationError(
                f'Firebase account for user {user.id} is missing and cannot be created without a password.',
            )

        auth.create_user(
            uid=uid,
            email=email,
            password=raw_password,
            display_name=display_name,
            disabled=not bool(user.is_active),
            app=app,
        )
        operation = 'created'

    auth.set_custom_user_claims(uid, claims, app=app)
    return {
        'uid': uid,
        'email': email,
        'claims': claims,
        'operation': operation,
    }


def schedule_firebase_auth_user_sync(user_id: int, raw_password: str | None = None, allow_create: bool = True):
    """Queue Firebase Auth sync in background without blocking request latency."""

    def _run():
        try:
            UserModel = get_user_model()
            user = UserModel.objects.filter(pk=user_id).first()
            if user is None:
                logger.warning('Skipping Firebase sync: user %s no longer exists.', user_id)
                return

            result = sync_firebase_auth_user(
                user,
                raw_password=raw_password,
                allow_create=allow_create,
            )
            logger.info('Firebase auth sync completed for user=%s operation=%s', user_id, result.get('operation'))
        except Exception:
            logger.exception('Background Firebase auth sync failed for user=%s', user_id)
        finally:
            close_old_connections()

    Thread(target=_run, daemon=True, name=f'firebase-auth-sync-{user_id}').start()


def delete_firebase_auth_user(user_id: int, phone: str | None = None):
    app = get_firebase_app()
    if auth is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )

    uid = firebase_uid_for_user_id(user_id)
    deleted_uid = False
    deleted_email = False

    try:
        auth.delete_user(uid, app=app)
        deleted_uid = True
    except auth.UserNotFoundError:
        pass

    if phone:
        try:
            email = firebase_email_for_phone(phone)
            user_record = auth.get_user_by_email(email, app=app)
            auth.delete_user(user_record.uid, app=app)
            deleted_email = True
        except auth.UserNotFoundError:
            pass

    return {
        'uid': uid,
        'deleted_uid': deleted_uid,
        'deleted_email': deleted_email,
    }


def schedule_firebase_auth_user_delete(user_id: int, phone: str | None = None):
    """Delete Firebase Auth account in background after backend hard delete."""

    def _run():
        try:
            result = delete_firebase_auth_user(user_id=user_id, phone=phone)
            logger.info(
                'Firebase auth delete completed for user=%s deleted_uid=%s deleted_email=%s',
                user_id,
                result.get('deleted_uid'),
                result.get('deleted_email'),
            )
        except Exception:
            logger.exception('Background Firebase auth delete failed for user=%s', user_id)
        finally:
            close_old_connections()

    Thread(target=_run, daemon=True, name=f'firebase-auth-delete-{user_id}').start()


def reset_firebase_user_password(user, raw_password: str):
    app = get_firebase_app()
    if auth is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )

    uid = firebase_uid_for_user(user)
    auth.update_user(uid, password=raw_password, app=app)
    return {'uid': uid, 'operation': 'password_reset'}


def verify_firebase_id_token(id_token: str):
    app = get_firebase_app()
    if auth is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )
    return auth.verify_id_token(id_token, app=app, check_revoked=False)


def create_custom_token_for_user(user, platform='web'):
    app = get_firebase_app()
    if auth is None:
        raise FirebaseConfigurationError(
            'firebase-admin package is not installed in this environment.',
        )
    claims = build_firebase_claims_for_user(user, platform=platform)
    firebase_uid = f'souigat-user-{user.id}'
    token_bytes = auth.create_custom_token(firebase_uid, developer_claims=claims, app=app)
    return token_bytes.decode('utf-8'), claims

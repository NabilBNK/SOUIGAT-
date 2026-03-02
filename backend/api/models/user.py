from django.contrib.auth.models import AbstractUser, BaseUserManager
from django.db import models


class UserManager(BaseUserManager):
    """Custom manager: phone is the login identifier, not username."""

    def create_user(self, phone, password=None, **extra_fields):
        if not phone:
            raise ValueError('Phone number is required')
        user = self.model(phone=phone, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password=None, **extra_fields):
        extra_fields.setdefault('is_staff', True)
        extra_fields.setdefault('is_superuser', True)
        extra_fields.setdefault('role', 'admin')
        return self.create_user(phone, password, **extra_fields)


class User(AbstractUser):
    ROLE_CHOICES = [
        ('admin', 'Admin'),
        ('office_staff', 'Office Staff'),
        ('conductor', 'Conductor'),
    ]
    DEPARTMENT_CHOICES = [
        ('all', 'All'),
        ('cargo', 'Cargo'),
    ]

    username = None
    phone = models.CharField(max_length=20, unique=True)
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='office_staff')
    department = models.CharField(
        max_length=20, choices=DEPARTMENT_CHOICES,
        null=True, blank=True,
        help_text='Only for office_staff. Admin/conductor = null.',
    )
    office = models.ForeignKey(
        'api.Office', on_delete=models.SET_NULL,
        null=True, blank=True, related_name='staff',
    )
    device_id = models.CharField(max_length=64, null=True, blank=True)
    device_bound_at = models.DateTimeField(null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    USERNAME_FIELD = 'phone'
    REQUIRED_FIELDS = ['first_name', 'last_name']

    objects = UserManager()

    class Meta:
        db_table = 'users'

    def __str__(self):
        return f"{self.get_full_name()} ({self.role})"

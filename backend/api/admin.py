from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin

from .models import (
    Office, User, Bus, Trip,
    PassengerTicket, CargoTicket, TripExpense,
    AuditLog, PricingConfig, SyncLog, QuarantinedSync,
)


@admin.register(Office)
class OfficeAdmin(admin.ModelAdmin):
    list_display = ['name', 'city', 'is_active', 'created_at']
    list_filter = ['is_active', 'city']
    search_fields = ['name', 'city']


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    list_display = ['phone', 'first_name', 'last_name', 'role', 'department', 'office', 'is_active']
    list_select_related = ['office']
    list_filter = ['role', 'department', 'is_active']
    search_fields = ['phone', 'first_name', 'last_name']
    ordering = ['phone']

    fieldsets = (
        (None, {'fields': ('phone', 'password')}),
        ('Personal', {'fields': ('first_name', 'last_name')}),
        ('Role', {'fields': ('role', 'department', 'office')}),
        ('Device', {'fields': ('device_id', 'device_bound_at')}),
        ('Permissions', {'fields': ('is_active', 'is_staff', 'is_superuser', 'groups', 'user_permissions')}),
        ('Dates', {'fields': ('last_login', 'date_joined')}),
    )
    add_fieldsets = (
        (None, {
            'classes': ('wide',),
            'fields': ('phone', 'password1', 'password2', 'first_name', 'last_name', 'role'),
        }),
    )


@admin.register(Bus)
class BusAdmin(admin.ModelAdmin):
    list_display = ['plate_number', 'office', 'capacity', 'is_active', 'created_at']
    list_select_related = ['office']
    list_filter = ['is_active', 'office']
    search_fields = ['plate_number']


@admin.register(Trip)
class TripAdmin(admin.ModelAdmin):
    list_display = [
        'id', 'origin_office', 'destination_office', 'conductor',
        'status', 'departure_datetime', 'currency', 'created_at',
    ]
    list_select_related = ['origin_office', 'destination_office', 'conductor', 'bus']
    list_filter = ['status', 'origin_office', 'currency']
    search_fields = ['conductor__phone', 'conductor__first_name']
    raw_id_fields = ['conductor', 'bus']


@admin.register(PassengerTicket)
class PassengerTicketAdmin(admin.ModelAdmin):
    list_display = ['ticket_number', 'trip', 'passenger_name', 'price', 'status', 'created_at']
    list_select_related = ['trip', 'created_by']
    list_filter = ['status', 'payment_source']
    search_fields = ['ticket_number', 'passenger_name']
    raw_id_fields = ['trip', 'created_by']


@admin.register(CargoTicket)
class CargoTicketAdmin(admin.ModelAdmin):
    list_display = [
        'ticket_number', 'trip', 'sender_name', 'receiver_name',
        'cargo_tier', 'price', 'status', 'created_at',
    ]
    list_select_related = ['trip', 'created_by', 'delivered_by', 'status_override_by']
    list_filter = ['status', 'cargo_tier', 'payment_source']
    search_fields = ['ticket_number', 'sender_name', 'receiver_name']
    raw_id_fields = ['trip', 'created_by', 'delivered_by']
    readonly_fields = ['status_override_reason', 'status_override_by']


@admin.register(TripExpense)
class TripExpenseAdmin(admin.ModelAdmin):
    list_display = ['trip', 'description', 'amount', 'currency', 'created_by', 'created_at']
    list_select_related = ['trip', 'created_by']
    list_filter = ['currency']
    raw_id_fields = ['trip', 'created_by']


@admin.register(AuditLog)
class AuditLogAdmin(admin.ModelAdmin):
    list_display = ['created_at', 'action', 'table_name', 'record_id', 'user', 'ip_address']
    list_select_related = ['user']
    list_filter = ['action', 'table_name']
    search_fields = ['table_name', 'record_id']
    readonly_fields = [
        'user', 'action', 'table_name', 'record_id',
        'old_values', 'new_values', 'ip_address', 'created_at',
    ]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(PricingConfig)
class PricingConfigAdmin(admin.ModelAdmin):
    list_display = [
        'origin_office', 'destination_office', 'passenger_price',
        'currency', 'effective_from', 'effective_until', 'is_active',
    ]
    list_select_related = ['origin_office', 'destination_office']
    list_filter = ['is_active', 'currency']
    raw_id_fields = ['origin_office', 'destination_office']


@admin.register(SyncLog)
class SyncLogAdmin(admin.ModelAdmin):
    list_display = ['key', 'conductor', 'trip', 'accepted', 'quarantined', 'created_at']
    list_select_related = ['conductor', 'trip']
    search_fields = ['key']
    raw_id_fields = ['conductor', 'trip']


@admin.register(QuarantinedSync)
class QuarantinedSyncAdmin(admin.ModelAdmin):
    list_display = ['id', 'conductor', 'trip', 'status', 'reason', 'reviewed_by', 'created_at']
    list_select_related = ['conductor', 'trip', 'reviewed_by']
    list_filter = ['status']
    search_fields = ['reason']
    raw_id_fields = ['conductor', 'trip', 'reviewed_by']

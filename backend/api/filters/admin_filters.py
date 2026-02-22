import django_filters
from api.models import AuditLog

class AuditLogFilter(django_filters.FilterSet):
    created_after = django_filters.IsoDateTimeFilter(
        field_name='created_at', lookup_expr='gte'
    )
    created_before = django_filters.IsoDateTimeFilter(
        field_name='created_at', lookup_expr='lte'
    )

    class Meta:
        model = AuditLog
        fields = ['user', 'action', 'table_name']

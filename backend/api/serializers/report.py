from rest_framework import serializers


class DailyReportSerializer(serializers.Serializer):
    """Daily revenue summary."""
    date = serializers.DateField()
    passenger_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    cargo_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    total_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    expense_total = serializers.DecimalField(max_digits=12, decimal_places=2)
    net = serializers.DecimalField(max_digits=12, decimal_places=2)
    ticket_count = serializers.IntegerField()
    cargo_count = serializers.IntegerField()
    trip_count = serializers.IntegerField()


class TripReportSerializer(serializers.Serializer):
    """Detailed single-trip breakdown."""
    trip_id = serializers.IntegerField()
    origin = serializers.CharField()
    destination = serializers.CharField()
    departure = serializers.DateTimeField()
    status = serializers.CharField()
    passenger_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    cargo_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    expense_total = serializers.DecimalField(max_digits=12, decimal_places=2)
    net = serializers.DecimalField(max_digits=12, decimal_places=2)
    ticket_count = serializers.IntegerField()
    cargo_by_tier = serializers.DictField()
    expenses = serializers.ListField()


class RouteReportSerializer(serializers.Serializer):
    """Route aggregation summary."""
    origin = serializers.CharField()
    destination = serializers.CharField()
    trip_count = serializers.IntegerField()
    total_revenue = serializers.DecimalField(max_digits=12, decimal_places=2)
    avg_ticket_count = serializers.FloatField()

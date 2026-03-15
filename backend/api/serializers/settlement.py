from rest_framework import serializers

from api.models import Settlement
from api.services.settlements import build_settlement_preview


class SettlementSerializer(serializers.ModelSerializer):
    office_name = serializers.CharField(source='office.name', read_only=True)
    conductor_name = serializers.CharField(source='conductor.get_full_name', read_only=True)
    settled_by_name = serializers.CharField(source='settled_by.get_full_name', read_only=True)
    origin_name = serializers.CharField(source='trip.origin_office.name', read_only=True)
    destination_name = serializers.CharField(source='trip.destination_office.name', read_only=True)
    trip_status = serializers.CharField(source='trip.status', read_only=True)
    reimbursement_gap = serializers.SerializerMethodField()

    class Meta:
        model = Settlement
        fields = [
            'id', 'trip', 'trip_status',
            'office', 'office_name',
            'conductor', 'conductor_name',
            'settled_by', 'settled_by_name',
            'origin_name', 'destination_name',
            'expected_passenger_cash', 'expected_cargo_cash',
            'expected_total_cash', 'agency_presale_total',
            'outstanding_cargo_delivery', 'expenses_to_reimburse',
            'net_cash_expected',
            'actual_cash_received', 'actual_expenses_reimbursed',
            'discrepancy_amount', 'reimbursement_gap',
            'status', 'notes', 'dispute_reason',
            'calculation_snapshot',
            'created_at', 'updated_at', 'settled_at', 'disputed_at', 'resolved_at',
        ]
        read_only_fields = fields

    def get_reimbursement_gap(self, obj):
        return obj.actual_expenses_reimbursed - obj.expenses_to_reimburse


class SettlementListSerializer(serializers.ModelSerializer):
    office_name = serializers.CharField(source='office.name', read_only=True)
    conductor_name = serializers.CharField(source='conductor.get_full_name', read_only=True)
    origin_name = serializers.CharField(source='trip.origin_office.name', read_only=True)
    destination_name = serializers.CharField(source='trip.destination_office.name', read_only=True)

    class Meta:
        model = Settlement
        fields = [
            'id', 'trip', 'office', 'office_name',
            'conductor', 'conductor_name',
            'origin_name', 'destination_name',
            'status', 'expected_total_cash', 'expenses_to_reimburse',
            'net_cash_expected', 'actual_cash_received',
            'actual_expenses_reimbursed', 'discrepancy_amount',
            'created_at', 'settled_at', 'disputed_at', 'resolved_at',
        ]
        read_only_fields = fields


class SettlementRecordSerializer(serializers.Serializer):
    actual_cash_received = serializers.IntegerField(min_value=0)
    actual_expenses_reimbursed = serializers.IntegerField(
        min_value=0, required=False, default=0,
    )
    notes = serializers.CharField(required=False, allow_blank=True, allow_null=True)


class SettlementDisputeSerializer(serializers.Serializer):
    dispute_reason = serializers.CharField(required=True, allow_blank=False)
    notes = serializers.CharField(required=False, allow_blank=True, allow_null=True)


class SettlementResolveSerializer(serializers.Serializer):
    actual_cash_received = serializers.IntegerField(min_value=0, required=False)
    actual_expenses_reimbursed = serializers.IntegerField(min_value=0, required=False)
    notes = serializers.CharField(required=True, allow_blank=False)


class SettlementPreviewSerializer(serializers.Serializer):
    settlement_id = serializers.IntegerField()
    status = serializers.CharField()
    office_name = serializers.CharField()
    expected_total_cash = serializers.IntegerField()
    expenses_to_reimburse = serializers.IntegerField()
    net_cash_expected = serializers.IntegerField()
    agency_presale_total = serializers.IntegerField()
    outstanding_cargo_delivery = serializers.IntegerField()

    @classmethod
    def from_settlement(cls, settlement):
        return cls(build_settlement_preview(settlement)).data

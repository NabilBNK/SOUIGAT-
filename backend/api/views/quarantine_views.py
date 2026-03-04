import logging

from django.db import models, transaction
from django.utils import timezone
from rest_framework import viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.exceptions import ValidationError

from api.models import QuarantinedSync, Trip
from api.serializers.quarantine import (
    QuarantinedSyncSerializer,
    QuarantineReviewSerializer,
    BulkQuarantineReviewSerializer,
)
from api.permissions import RBACPermission
from api.views.sync_views import _process_item

logger = logging.getLogger(__name__)


class QuarantineViewSet(viewsets.ReadOnlyModelViewSet):
    """
    Quarantine review management.

    List/retrieve: admin sees all, office_staff sees their office's trips.
    Review: approve (re-process) or reject a single item.
    Bulk review: approve/reject up to 200 items at once.
    """

    # Required by DRF router for basename resolution. Overridden by get_queryset().
    queryset = QuarantinedSync.objects.all()
    serializer_class = QuarantinedSyncSerializer
    required_roles = ['admin', 'office_staff']
    permission_classes = [RBACPermission]

    def get_queryset(self):
        """Filter by user scope. Admin: all. Staff: own office trips."""
        user = self.request.user
        qs = QuarantinedSync.objects.select_related(
            'conductor', 'trip', 'reviewed_by',
        ).order_by('-created_at')

        if user.role == 'admin':
            return qs

        if user.role == 'office_staff':
            return qs.filter(
                models.Q(trip__origin_office_id=user.office_id)
                | models.Q(trip__destination_office_id=user.office_id)
            )

        return qs.none()

    @action(detail=True, methods=['post'])
    def review(self, request, pk=None):
        """Approve or reject a single quarantined item."""
        item = self.get_object()

        if item.status != 'pending':
            raise ValidationError(
                f'Item already reviewed. Status: {item.status}'
            )

        ser = QuarantineReviewSerializer(data=request.data)
        ser.is_valid(raise_exception=True)

        new_status = ser.validated_data['status']
        review_notes = ser.validated_data.get('review_notes', '')

        with transaction.atomic():
            item.status = new_status
            item.reviewed_by = request.user
            item.reviewed_at = timezone.now()
            item.review_notes = review_notes
            item.save()

            if new_status == 'approved':
                self._reprocess_item(item)

        return Response(QuarantinedSyncSerializer(item).data)

    @action(detail=False, methods=['post'])
    def bulk_review(self, request):
        """Bulk approve/reject up to 200 quarantined items."""
        ser = BulkQuarantineReviewSerializer(data=request.data)
        ser.is_valid(raise_exception=True)

        ids = ser.validated_data['ids']
        action_type = ser.validated_data['action']
        review_notes = ser.validated_data.get('review_notes', '')

        new_status = 'approved' if action_type == 'approve' else 'rejected'

        with transaction.atomic():
            qs = self.get_queryset().filter(id__in=ids, status='pending')
            items = list(qs)

            processed = 0
            reprocess_errors = 0

            for item in items:
                if new_status == 'approved':
                    try:
                        self._reprocess_item(item)
                    except Exception as e:
                        logger.warning(
                            'Re-process failed for QS #%d: %s', item.pk, e,
                        )
                        reprocess_errors += 1
                        continue

                item.status = new_status
                item.reviewed_by = request.user
                item.reviewed_at = timezone.now()
                item.review_notes = review_notes
                item.save()
                processed += 1

        return Response({
            'processed': processed,
            'reprocess_errors': reprocess_errors,
            'skipped': len(ids) - processed,
        })

    @staticmethod
    def _reprocess_item(item):
        """Re-process approved quarantined item through sync pipeline."""
        data = item.original_data
        item_type = data.get('type')
        payload = data.get('payload', {})

        try:
            trip = Trip.objects.select_for_update().get(pk=item.trip_id)
        except Trip.DoesNotExist:
            raise ValidationError('Trip no longer exists.')

        # Bug #13 fix: Admin explicitly approved this quarantined item.
        # Allow processing regardless of trip status — the quarantine
        # mechanism exists precisely for closed-trip data recovery.

        from api.models import PassengerTicket, CargoTicket
        trip_state = {
            'active_passenger_count': PassengerTicket.objects.filter(trip=trip, status='active').count(),
            'total_passenger_count': PassengerTicket.objects.filter(trip=trip).count(),
            'cargo_count': CargoTicket.objects.filter(trip=trip).count(),
        }

        _process_item(
            item_type=item_type,
            payload=payload,
            trip=trip,
            user=item.conductor,
            trip_state=trip_state,
        )

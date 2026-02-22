"""Tests for T16: Excel export."""
from unittest.mock import patch, MagicMock
from datetime import timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from api.models import Bus, Office, Trip, User


class ExcelExportTests(TestCase):
    """Export trigger, status polling, download, and access control."""

    def setUp(self):
        self.office = Office.objects.create(name='HQ', city='Algiers')
        self.office_b = Office.objects.create(name='Branch', city='Oran')
        self.admin = User.objects.create_superuser(
            phone='0550000001', password='pass',
            first_name='Admin', last_name='X',
        )
        self.conductor = User.objects.create_user(
            phone='0550000003', password='pass',
            first_name='Cond', last_name='A',
            role='conductor', office=self.office,
        )
        self.client = APIClient()

    @patch('api.views.export_views.generate_excel_report')
    def test_trigger_export(self, mock_task):
        """POST /exports/ triggers Celery task and returns task_id."""
        mock_result = MagicMock()
        mock_result.id = 'test-task-id-123'
        mock_task.delay.return_value = mock_result

        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/exports/', {
            'report_type': 'daily',
            'filters': {'date_from': '2026-02-01', 'date_to': '2026-02-18'},
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_202_ACCEPTED)
        self.assertEqual(resp.data['task_id'], 'test-task-id-123')
        self.assertIn('download_token', resp.data)

    def test_invalid_report_type(self):
        """Reject invalid report type."""
        self.client.force_authenticate(self.admin)
        resp = self.client.post('/api/exports/', {
            'report_type': 'invalid',
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)

    def test_conductor_cannot_export(self):
        """Conductors are denied export access."""
        self.client.force_authenticate(self.conductor)
        resp = self.client.post('/api/exports/', {
            'report_type': 'daily',
        }, format='json')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

    def test_download_invalid_token(self):
        """Download with invalid token is rejected."""
        self.client.force_authenticate(self.admin)
        resp = self.client.get('/api/exports/fake-task/download/?token=invalidtoken')
        self.assertEqual(resp.status_code, status.HTTP_403_FORBIDDEN)

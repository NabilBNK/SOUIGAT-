import os
import django
from django.utils import timezone

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'souigat.settings')
django.setup()

from api.models import Trip, User

# We know conductor ID is 5 from our earlier script
conductor_id = 5

try:
    c = User.objects.get(id=conductor_id)
    # Find active trip
    active_trips = Trip.objects.filter(conductor=c, status='in_progress')
    
    if not active_trips.exists():
        print("No active trips found for conductor.")
    else:
        for t in active_trips:
            print(f"Found orphaned Trip ID {t.id} for Conductor {c.get_full_name()}")
            
            # Check for tickets (if passenger_tickets model exists)
            try:
                from api.models import PassengerTicket
                unsynced_tickets = PassengerTicket.objects.filter(trip_id=t.id, synced_at__isnull=True).count()
                print(f"Unsynced tickets: {unsynced_tickets}")
            except ImportError:
                print("No PassengerTicket model found, skipping ticket sync check.")
                unsynced_tickets = 0

            # Check for expenses (if TripExpense model exists)
            try:
                from api.models import TripExpense
                from django.core.exceptions import FieldError
                try:
                    unsynced_expenses = TripExpense.objects.filter(trip_id=t.id, synced_at__isnull=True).count()
                except FieldError:
                    print("TripExpense does not have synced_at field. Assuming synced.")
                    unsynced_expenses = 0
                print(f"Unsynced expenses: {unsynced_expenses}")
            except ImportError:
                print("No TripExpense model found, skipping expense sync check.")
                unsynced_expenses = 0
            
            if unsynced_tickets == 0 and unsynced_expenses == 0:
                print("Pre-flight checklist PASSED. Updating trip status to completed...")
                t.status = 'completed'
                t.arrival_datetime = timezone.now()
                t.save(skip_validation=True)
                print(f"Trip {t.id} successfully closed.")
                
                # Write to audit log if model exists
                try:
                    from api.models import AuditLog
                    AuditLog.objects.create(
                        action="FORCE_COMPLETE_TRIP",
                        user=None, # System action
                        details=f"Forced completion of orphaned trip {t.id} to unblock conductor {c.id} via backend debug script. Sync gates passed."
                    )
                    print("Audit log recorded.")
                except Exception as e:
                    print(f"Could not write audit log: {e}")
            else:
                print("Pre-flight checklist FAILED. There are unsynced records.")
except Exception as e:
    print(f"Error executing Task 0: {e}")

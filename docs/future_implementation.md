# SOUIGAT — Future Implementation Backlog

> Features deferred from MVP. To be revisited after production launch.

---

## 🖨️ Bluetooth Thermal Printing (Option B)

**Priority:** Post-MVP  
**Effort:** High  
**Status:** Deferred (March 2026)

### Description
Integrate portable ESC/POS Bluetooth thermal printers so conductors can print physical receipts when creating tickets.

### Requirements
- Android Bluetooth Classic (SPP) or BLE connection to thermal printer
- ESC/POS command builder for receipt layout
- QR Code generation on receipt (ticket ID + trip ID for inspector verification)
- Graceful handling of: printer disconnected, out of paper, low battery
- Printer pairing UI in Profile/Settings screen

### Receipt Layout (Draft)
```
================================
        SOUIGAT TRANSPORT
================================
Trajet: Ouargla → Alger
Date:   10/03/2026  14:30
Billet: PAX-2026-00412
Siège:  12
Prix:   1,500.00 DZD
Paiement: Espèces
--------------------------------
[QR CODE: ticket_id + trip_id]
================================
     Bon voyage ! 🚌
================================
```

### Technical Notes
- Library candidates: `android-print-sdk`, `escpos-coffee`, or raw socket to printer
- Must work fully offline (no server needed to print)
- Consider caching last-used printer MAC address for auto-reconnect
- Test with: Xprinter XP-P323B (common in Algeria)

---

## Architecture Drift Reconciliation

**Priority:** Post-MVP
**Effort:** Medium
**Status:** Deferred (March 2026)

### Description
Align the February 13, 2026 architecture documents with the March 2026 implementation, or bring the implementation back in line where the documented behavior is still the intended target.

### Current Drift Areas
- **Roles and permissions:** The docs describe either a 4-role model with `driver` and `department=passenger`, or a separate `officestaff_cargo` role. The live code currently uses `admin`, `office_staff`, and `conductor`, while some permission and frontend code still references legacy role values.
- **Auth policy:** The docs describe `15min` access tokens and `7d` refresh tokens. The current backend uses `2h` access and `24h` refresh lifetimes, with web/mobile refresh behavior implemented in custom view logic.
- **Django admin hardening:** The docs propose disabling admin in production or restricting it behind IP whitelist + 2FA. The implementation currently exposes Django admin behind a randomized path derived from `ADMIN_SECRET`.
- **Sync semantics:** The docs describe batch-level idempotency and whole-batch quarantine for closed trips. The current backend/mobile implementation uses per-item idempotency, partial accept/quarantine handling, and `resume_from`-based recovery.
- **Domain model:** The updated architecture doc still assumes fields such as `trip_code`, `driver_name`, `created_by_user_id`, and a single `office_id` on trips. The real model is route-based (`origin_office`, `destination_office`) and has richer ticket state/version fields.
- **Excel export target:** The docs promise `time_limit=600`, `max_rows=100K`, and streaming-oriented export behavior. The current Celery task uses `soft_time_limit=120` and builds the workbook in memory with `openpyxl`.

### Follow-up Work
- Decide which role model is authoritative: department-based `office_staff`, separate `officestaff_cargo`, or a restored 4-role model.
- Remove stale code paths and types that still reference unsupported roles or departments.
- Update the architecture docs so token lifetimes, admin exposure, sync behavior, and trip schema match the code.
- If the original export scalability target still matters, redesign the export task for bounded memory use and larger row counts.
- Produce a formal "planned vs implemented" architecture note for future onboarding and review.

### Output Goal
- One authoritative architecture document that matches the production code.
- One cleanup pass removing stale role/permission/frontend references left over from earlier designs.

---

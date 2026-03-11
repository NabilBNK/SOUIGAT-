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

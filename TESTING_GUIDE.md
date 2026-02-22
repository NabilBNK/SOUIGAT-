# SOUIGAT Web Portal - Manual Testing Guide

Welcome to the manual testing guide for the Phase 2 Web Portal! This guide walks you through verifying the role-based routing, core workflows, and recent security/UX improvements (multi-tab tokens, 60s export timeouts, and correct Cargo destination logic) on your local machine.

## 1. Environment Setup

### 1.1 Start the Infrastructure
Ensure Docker Desktop is running, then boot the entire stack (PostgreSQL, Redis, Celery, Django Backend, and Vite Frontend) using Docker Compose:

```bash
cd desktop/SOUIGAT
docker compose up --build
```

### 1.2 Seed the Database
In a new terminal, populate your local database with offices, buses, pricing rules, and test users:

```bash
docker compose exec backend python manage.py seed_data
```

## 2. Test Accounts

The following accounts have been provisioned by the seeder script. Use these phone numbers and passwords to log in at **http://localhost:5173**.

| Role | Department | Phone | Password | Expected Access |
|---|---|---|---|---|
| **Admin** | N/A | `0500000001` | `admin123` | Full access (`/admin/*`) |
| **Office Staff** | All (Algiers) | `0600000001` | `staff123` | Office & Cargo (`/office/*`, `/cargo/*`) |
| **Office Staff** | All (Oran) | `0600000002` | `staff123` | Office & Cargo (`/office/*`, `/cargo/*`) |
| **Office Staff** | Cargo (Algiers) | `0600000003` | `staff123` | Cargo Only (`/cargo/*`) |
| **Conductor** | N/A | `0700000001` | `conductor123` | Blocked (`/unauthorized`) |

---

## 3. Core Testing Scenarios

### Scenario A: Role-Based Routing & Guards
1. Open http://localhost:5173 and log in as the **Conductor** (`0700000001` / `conductor123`).
   * **Verify:** You are immediately redirected to `/unauthorized`.
2. Log out and log in as **Office Staff - Cargo Only** (`0600000003` / `staff123`).
   * **Verify:** You are routed to the Cargo Dashboard.
   * **Verify:** Manually changing the URL to `http://localhost:5173/office` redirects you to `/unauthorized` or back to `/cargo`.

### Scenario B: Admin Management & Audit Logs
1. Log in as **Admin** (`0500000001` / `admin123`).
2. Navigate to the **Offices** and **Buses** panels. 
   * **Verify:** The grids populate with the 5 seeded offices and 2 seeded buses (ensuring `urls.py` routing is fully functional).
3. Change a bus's capacity from 49 to 50.
4. Navigate to the **Audit Log**.
   * **Verify:** There is a new entry recording the `Bus` update, capturing the `old_values` and `new_values` accurately.

### Scenario C: Office Operations & Cargo Security
1. Log in as **Office Staff (Algiers)** (`0600000001` / `staff123`).
2. Navigate to **Trips** and create a new trip from Algiers to Oran.
3. Open the **Trip Details** and go to the **Cargo Tickets** tab. Create a Cargo Ticket.
   * Note the specific `trip_destination_office_id` (Oran).
4. Go to the **Cargo** view and try to click the newly created cargo ticket.
   * **Verify:** Because you are logged in at Algiers (Origin), the "Passer en: Delivered" button should **not** be visible.
5. Log out. Log in as **Office Staff (Oran)** (`0600000002` / `staff123`).
6. Navigate to the **Cargo** view and open that exact same cargo ticket.
   * **Verify:** You *can* see the "Passer en: Delivered" button, proving that UI Destination Security Enforcement works.

### Scenario D: Financial Reports & 60-Second Timers
1. Log in as **Office Staff (Algiers)** (`0600000001` / `staff123`).
2. Navigate to **Reports**.
3. Click the **Exporter** (Export) button.
   * **Verify:** You see the "Préparation de l'Excel..." loading banner.
   * **Verify:** Once Celery succeeds, your browser automatically downloads the report file. 

*(Optional Edge-Case Check)*: To verify the 60-second limit, temporarily kill the Celery container (`docker compose stop celery`). Click Export again. You will see the spinner, but after precisely 60 seconds, it will gracefully stop and show "Délai d'exportation dépassé."

### Scenario E: Network & Tab Synchronization (Token Drift)
1. Open http://localhost:5173 in **Tab 1** and log in.
2. Open http://localhost:5173 in **Tab 2**. You are logged in automatically.
3. In **Tab 1**, open your browser's Developer Tools (F12) -> Application -> Local Storage. Run the following in the DevTools console to force a token refresh simulation:
   ```javascript
   // Clear the current memory tokens to trigger interceptor
   window.dispatchEvent(new Event('offline')) // (Or just manually drop the token via code injection if possible)
   ```
   *Actually, an easier way to test this without hacking the UI:*
   - Keep both tabs open.
   - Wait 2 hours (the `ACCESS_TOKEN_LIFETIME`). 
   - Click a button in **Tab 1**. Axios hits a 401, silently calls `/auth/token/refresh/`, and stores the new memory tokens.
   - Immediately switch to **Tab 2** and click a button.
   * **Verify:** Tab 2 does *not* crash to the login page. Thanks to the `BroadcastChannel`, Tab 2 instantly received Tab 1's newly refreshed tokens and executed its API call flawlessly.

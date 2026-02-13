# SOUIGAT System - Complete Technical & Business Specification (Updated v1.1)
**Internal Financial Management Platform for Intercity Bus & Cargo Operations**

**Version:** 1.1 (Updated Feb 13, 2026)  
**Date:** February 13, 2026  
**Author:** System Architect & Lead Developer  
**Document Type:** Comprehensive System Specification  
**Document Length:** ~9,500 words
**Key Update:** Role-based permissions now strictly by account (`users.role`), independent of physical location. New `officestaff_cargo` role for cargo-only staff (e.g., Oran office).

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Purpose & Business Context](#2-system-purpose--business-context)
3. [Stakeholders & User Roles](#3-stakeholders--user-roles)
4. [System Architecture Overview](#4-system-architecture-overview)
5. [Complete Database Schema](#5-complete-database-schema)
6. [Detailed Workflows & Business Processes](#6-detailed-workflows--business-processes)
7. [End-to-End System Flow](#7-end-to-end-system-flow)
8. [Technical Infrastructure](#8-technical-infrastructure)
9. [Security & Data Integrity](#9-security--data-integrity)
10. [MVP vs Full System Comparison](#10-mvp-vs-full-system-comparison)
11. [Implementation Strategy](#11-implementation-strategy)
12. [Risk Analysis & Mitigation](#12-risk-analysis--mitigation)
13. [Success Metrics & KPIs](#13-success-metrics--kpis)

---

## 1. Executive Summary

### 1.1 Problem Statement

SOUIGAT addresses critical operational challenges for a government-licensed intercity bus transportation company in Ouargla state, Algeria. Current operations across 5 offices use paper tickets, manual expense logs, and Excel, causing revenue leakage, delayed visibility, reconciliation delays, and audit issues.

### 1.2 Solution Overview

**Dual-platform system:**
1. **Android Mobile App** - Conductors create tickets/expenses (offline-first)
2. **Web Portal** - Office staff/admins manage trips, reports, reconciliation
3. **Central Server** - Algeria-hosted API + PostgreSQL DB

**Core Innovation:** Offline-first with automatic sync for poor connectivity.

### 1.3 Expected Business Impact
- **Week 1:** 95% reconciliation time reduction
- **Month 3:** Real-time visibility, audit compliance
- **Year 1:** Scalable to 20+ offices, analytics foundation

### 1.4 Scope Boundaries
**In Scope (MVP):**
- Trip lifecycle, passenger/cargo ticketing, expenses
- Offline sync, office-level reports, Excel export
**Out of Scope:** GPS, online payments, customer apps

---

## 2. System Purpose & Business Context

### 2.1 Business Domain
- **5 offices:** Ouargla HQ + Algiers, Oran, Constantine, Ghardaia
- **10-12 buses**, 8 conductors, 15-20 office staff
- **Revenue:** 70% passengers, 25% cargo (3 tiers), 5% charters
- **Expenses:** Fuel (40%), meals, maintenance, tolls

### 2.2 Key Update: Role Isolation by Account
Roles now defined **strictly by user account** (`users.role`), not physical location. Office staff in Oran/etc. can have `officestaff_cargo` role for cargo-only operations (receive/send/validate cargos).

---

## 3. Stakeholders & User Roles

**Critical Principle:** Permissions determined by `users.role` + `users.office_id` scoping. Physical location irrelevant.

### 3.1 Conductor (Mobile App)
**Permissions:**
- View/start/end own trips
- Create passenger/cargo tickets, expenses
- View current trip summary only
- **Scoped to assigned trips**

### 3.2 Office Staff - Full (Web Portal)
**Permissions:**
- Create/edit/cancel trips for their office
- View all tickets/expenses for their office
- Office reports, Excel export
- Manual ticket entry fallback

### 3.3 Office Staff - Cargo Only (NEW)
**For cargo-specialized staff (e.g., Oran):**
**Permissions:**
- ✅ View trips for their office (to attach cargo)
- ✅ Create cargo tickets (send cargo)
- ✅ Update cargo: mark paid, set delivery office, validate
- ❌ No passenger tickets
- ❌ No trip creation/editing
- ❌ No user/pricing management
- **Scoped to their office only**

### 3.4 Main Admin (HQ)
**Full system access + cross-office visibility**

---

## 4. System Architecture Overview

```
┌──────────────────────┬──────────────────────┐
│ Android Mobile App   │ Web Portal           │
│ (Conductors)         │ (Office/Admin)       │
│ • Offline SQLite     │ • React/Vue SPA      │
│ • Auto-sync          │ • Role-based UI      │
└──────────┬───────────┘
           │ HTTPS REST API (JWT + RBAC)
           ▼
┌─────────────────────────────────────┐
│ Backend API (Django/Node.js)        │
│ • Role middleware:                  │
│   - conductor → trip tickets/exp    │
│   - officestaff → trips + tickets   │
│   - officestaff_cargo → cargo only  │
│   - admin → all                     │
└──────────┬──────────────────────────┘
           │ PostgreSQL (Algeria VPS)
           ▼
┌─────────────────────────────────────┐
│ Central DB: trips, tickets, users   │
│ + audit_log for admin changes       │
└─────────────────────────────────────┘
```

**Key Decisions:**
- **Native Android** (small APK, best offline)
- **PostgreSQL** (ACID, JSONB audit logs)
- **Monolithic MVP** (scale to microservices later)
- **Algeria VPS** (data sovereignty)

---

## 5. Complete Database Schema

### 5.1 Core Tables (Updated)

#### `offices` (unchanged)
```sql
CREATE TABLE offices (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    location VARCHAR(200),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `buses` (unchanged)
```sql
CREATE TABLE buses (
    id SERIAL PRIMARY KEY,
    matricule VARCHAR(50) NOT NULL UNIQUE,
    internal_number VARCHAR(20),
    capacity INTEGER DEFAULT 45,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `users` (UPDATED - new role)
```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN (
        'conductor', 
        'officestaff', 
        'officestaff_cargo',  -- NEW: cargo-only staff
        'admin'
    )),
    office_id INTEGER NOT NULL REFERENCES offices(id),
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_office ON users(office_id);
```

#### `trips` (unchanged)
```sql
CREATE TABLE trips (
    id SERIAL PRIMARY KEY,
    trip_code VARCHAR(50) NOT NULL UNIQUE,
    bus_id INTEGER NOT NULL REFERENCES buses(id),
    driver_name VARCHAR(100) NOT NULL,
    route_origin VARCHAR(100) NOT NULL,
    route_destination VARCHAR(100) NOT NULL,
    departure_datetime TIMESTAMP NOT NULL,
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    created_by_user_id INTEGER NOT NULL REFERENCES users(id),
    office_id INTEGER NOT NULL REFERENCES offices(id),
    status VARCHAR(20) DEFAULT 'scheduled' CHECK (status IN ('scheduled','cancelled','in_progress','completed')),
    passenger_base_price DECIMAL(10,2) NOT NULL,
    cargo_small_price DECIMAL(10,2) NOT NULL,
    cargo_medium_price DECIMAL(10,2) NOT NULL,
    cargo_large_price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP, completed_at TIMESTAMP,
    cancelled_at TIMESTAMP, cancellation_reason TEXT
);
```

#### `passenger_tickets` (unchanged)
```sql
CREATE TABLE passenger_tickets (
    id SERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    origin VARCHAR(100), destination VARCHAR(100),
    price DECIMAL(10,2) NOT NULL,
    payment_source VARCHAR(20) DEFAULT 'onboard_cash' 
        CHECK (payment_source IN ('onboard_cash','agency_presale')),
    ticket_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP,
    is_manual_entry BOOLEAN DEFAULT FALSE
);
```

#### `cargo_tickets` (unchanged)
```sql
CREATE TABLE cargo_tickets (
    id SERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    origin VARCHAR(100), destination VARCHAR(100),
    cargo_tier VARCHAR(10) CHECK (cargo_tier IN ('small','medium','large')),
    price DECIMAL(10,2) NOT NULL,
    is_paid BOOLEAN DEFAULT FALSE,
    ticket_date DATE NOT NULL,
    delivery_type VARCHAR(20) DEFAULT 'unknown',
    delivery_office_id INTEGER REFERENCES offices(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP
);
```

#### `trip_expenses` (unchanged)
```sql
CREATE TABLE trip_expenses (
    id SERIAL PRIMARY KEY,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    expense_category VARCHAR(20) CHECK (expense_category IN ('fuel','food','other')),
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    expense_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP
);
```

#### `audit_log` (unchanged)
```sql
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    table_name VARCHAR(50) NOT NULL,
    record_id INTEGER NOT NULL,
    old_values JSONB,
    new_values JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `pricing_config` (unchanged)
```sql
CREATE TABLE pricing_config (
    id SERIAL PRIMARY KEY,
    route_origin VARCHAR(100) NOT NULL,
    route_destination VARCHAR(100) NOT NULL,
    passenger_base_price DECIMAL(10,2) NOT NULL,
    cargo_small_price DECIMAL(10,2) NOT NULL,
    cargo_medium_price DECIMAL(10,2) NOT NULL,
    cargo_large_price DECIMAL(10,2) NOT NULL,
    UNIQUE(route_origin, route_destination)
);
```

---

## 6. API Endpoints & RBAC

**Authentication:** JWT with role/office claims

**Key Endpoints with Role Enforcement:**

| Endpoint | Conductor | Office Staff | Office Staff Cargo | Admin |
|----------|-----------|--------------|--------------------|-------|
| `POST /tickets/passenger` | ✅ | ✅ | ❌ | ✅ |
| `POST /tickets/cargo` | ✅ | ✅ | ✅ | ✅ |
| `POST /trips` | ❌ | ✅ | ❌ | ✅ |
| `GET /reports/office` | ❌ | ✅ | ✅ (cargo only) | ✅ |
| `GET /reports/company` | ❌ | ❌ | ❌ | ✅ |

**RBAC Middleware Logic:**
```python
def role_check(request, required_roles, required_office=None):
    user_role = request.user.role
    user_office = request.user.office_id

    if user_role not in required_roles:
        return 403

    if required_office and user_office != required_office:
        return 403
```

---

## 7. Implementation Strategy (MVP Phase 1)

1. **Week 1-2:** Backend API + PostgreSQL schema
2. **Week 3-4:** Android app (offline SQLite + sync)
3. **Week 5:** Web portal (React + role-based UI)
4. **Week 6:** Testing + Algeria VPS deployment
5. **Week 7:** Training + go-live (Ouargla HQ first)

**Tech Stack:**
- Backend: Django REST / Node.js + PostgreSQL
- Mobile: Native Android (Kotlin)
- Web: React/Vue + responsive design
- Hosting: Algeria VPS (Icosnet/AT)

---

## 8. Success Metrics
- **95% reconciliation time reduction**
- **Zero revenue leakage from lost tickets**
- **100% offline data capture**
- **Role isolation verified** (cargo staff can't access passenger data)

---

**Next Steps:** Use this spec for development kickoff. Create sample data for testing new `officestaff_cargo` role.

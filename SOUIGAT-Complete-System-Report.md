# SOUIGAT System - Complete Technical & Business Specification
**Internal Financial Management Platform for Intercity Bus & Cargo Operations**

**Version:** 1.0 Final  
**Date:** February 4, 2026  
**Author:** System Architect & Lead Developer  
**Document Type:** Comprehensive System Specification  
**Document Length:** 8,500+ words

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

SOUIGAT addresses critical operational challenges faced by a government-licensed intercity bus transportation company operating in Ouargla state, Algeria. The company currently manages financial operations across 5 offices using paper-based ticket books, manual expense logs, and fragmented Excel spreadsheets. This creates:

- **Revenue leakage**: Lost or misplaced paper tickets result in untracked revenue
- **Delayed visibility**: Offices report manually at end of day, creating 6-8 hour lag in financial data
- **Poor accountability**: No systematic way to track conductor performance
- **Time waste**: 3+ hours per office for end-of-day reconciliation
- **No analytics**: Historical data trapped in paper archives, preventing data-driven decisions
- **Audit challenges**: Difficult to prove compliance for government regulatory requirements

### 1.2 Solution Overview

SOUIGAT is a dual-platform financial management system comprising:

1. **Android Mobile Application** - Native app for conductors to create passenger/cargo tickets and record expenses in real-time during trips
2. **Web Portal** - Browser-based application for office staff and administrators to create trips, view financial data, and generate reports
3. **Central Server** - Algeria-hosted backend API and PostgreSQL database storing all financial transactions

**Core Innovation**: Offline-first architecture ensures zero data loss even when buses travel through areas without cellular coverage (common on Algerian highways). All transactions are captured locally and automatically synchronized when connectivity returns.

### 1.3 Expected Business Impact

**Immediate Benefits (Week 1):**
- 95% reduction in reconciliation time (from 180 minutes to <10 minutes per office)
- Real-time trip profitability visibility
- Elimination of lost ticket revenue

**Medium-term Benefits (Month 3):**
- Complete audit trail for regulatory compliance
- Route performance analytics enabling pricing optimization
- Conductor accountability metrics

**Long-term Benefits (Year 1):**
- Foundation for expansion from 5 to 20+ offices
- Historical data enabling seasonal demand forecasting
- Integration readiness for accounting software

### 1.4 Scope Boundaries

**In Scope (MVP Phase 1):**
- Trip creation and lifecycle management
- Passenger ticketing (cash and agency pre-sale)
- Cargo ticketing with three size tiers
- Trip expense recording
- Offline mobile operation with automatic sync
- Daily and per-trip financial reporting
- User and bus management
- Excel data export

**Explicitly Out of Scope:**
- Customer-facing booking applications
- GPS tracking and real-time bus location
- Online payment processing (credit cards, mobile money)
- Automated accounting software integration
- Cargo delivery signature capture
- Conductor-to-office messaging
- Multi-language support (Arabic/French/English)

---

## 2. System Purpose & Business Context

### 2.1 Business Domain

The client operates as the official government-licensed provider for intercity bus transportation in Ouargla wilaya (state). They hold exclusive operating licenses for scheduled passenger and cargo transport between Ouargla and major Algerian cities.

**Operational Scale:**
- **Offices**: 5 locations (1 main HQ in Ouargla + 4 branch offices in Algiers, Oran, Constantine, Ghardaia)
- **Fleet**: 10-12 buses identified by government matricule numbers (format: XXXX-YY-ZZ)
- **Personnel**: 8 active conductors, 15-20 office staff, 10-12 drivers
- **Trip Frequency**: 1-2 trips per day system-wide (varies by season)
- **Route Coverage**: 200km to 600km per trip
- **Average Load**: 25-35 passengers per trip, 8-12 cargo shipments per trip

**Revenue Model:**

1. **Passenger Tickets (70% of revenue)**
   - Full route tickets: Origin to final destination
   - Intermediate tickets: Boarding/exiting at stops along route
   - Two payment channels:
     - Cash on bus (majority, ~70%)
     - Agency pre-sold tickets (~30%)

2. **Cargo Transport (25% of revenue)**
   - Three pricing tiers based on physical size:
     - Small: Shoebox-sized packages (800-1,000 DA)
     - Medium: Microwave-sized items (1,500-2,000 DA)
     - Large: Appliance-sized cargo (2,500-3,500 DA)
   - Payment options: Paid on shipment or pay on delivery

3. **Charter Services (5% of revenue, out of scope for MVP)**

**Operating Expenses:**
- Fuel: ~40% of revenue (largest expense category)
- Meals: Driver and conductor meals during trips
- Maintenance: Scheduled and emergency repairs
- Tolls: Highway toll charges
- Miscellaneous: Unexpected operational costs

### 2.2 Regulatory Context

As a government-licensed transportation operator, the company must comply with:

- **Ministry of Transport Regulations**: Monthly revenue reporting required
- **Tax Authority Requirements**: Complete financial records retention for 5+ years
- **Consumer Protection Laws**: Transparent pricing displayed at stations and agencies
- **Receipt Issuance**: Legal obligation to provide proof of payment (currently paper-based)

SOUIGAT's audit logging, Excel export capabilities, and complete transaction history directly support these compliance requirements.

### 2.3 Current Pain Points (Detailed)

**Pain Point 1: Revenue Leakage**
- Paper tickets occasionally lost between bus and office
- No systematic tracking of agency pre-sold tickets
- Estimated 2-3% revenue leakage annually (~200,000 DA loss)

**Pain Point 2: Delayed Financial Visibility**
- Offices only see revenue after conductor returns (6-12 hours after trip completion)
- Director cannot make real-time decisions about route profitability
- No ability to respond to operational issues during trips

**Pain Point 3: Reconciliation Burden**
- Office staff spend 3+ hours nightly counting paper tickets
- Manual Excel entry prone to transcription errors
- Discrepancies require lengthy investigation

**Pain Point 4: Limited Analytics**
- Historical data exists only in paper archives (inaccessible for analysis)
- Cannot identify seasonal patterns or optimal pricing
- No way to compare conductor or route performance

**Pain Point 5: Accountability Gaps**
- No systematic way to track individual conductor performance
- Disputes about ticket counts resolved through time-consuming paper review
- Difficult to identify if revenue shortfalls due to low demand or conductor issues

### 2.4 Business Objectives

**Primary Objectives:**
1. **Financial Accuracy**: Capture 100% of transactions digitally, eliminate revenue leakage
2. **Real-Time Visibility**: Enable HQ to view trip performance as it happens
3. **Operational Efficiency**: Reduce administrative burden from 3 hours to <10 minutes per office
4. **Conductor Accountability**: Create transparent, auditable record of all transactions
5. **Strategic Analytics**: Build historical database for route optimization and pricing strategy

**Secondary Objectives:**
1. Maintain operational resilience (offline capability for poor connectivity areas)
2. Minimize capital investment (use existing conductor phones)
3. Preserve paper backup option for system failure scenarios
4. Simple user experience for staff with minimal technical literacy
5. Scalable architecture supporting growth to 50+ offices and 100+ conductors

---

## 3. Stakeholders & User Roles

### 3.1 Conductor (Bus Assistant) - Mobile App User

**Profile & Context:**
- **Role**: Field personnel working on buses during trips
- **Demographics**: Typically 25-45 years old, secondary education
- **Technical Literacy**: Moderate smartphone familiarity (WhatsApp, Facebook users)
- **Work Environment**: Moving vehicle, variable lighting, intermittent connectivity
- **Devices**: Personal Android phones (budget models: 1-2GB RAM, Android 5.0+)

**Primary Responsibilities:**
1. **Trip Management**: Start and end assigned trips in mobile app
2. **Passenger Ticketing**: 
   - Create tickets for cash-paying passengers boarding bus
   - Record agency pre-sold tickets (passenger shows paper receipt)
   - Handle intermediate stops (passengers boarding/exiting mid-route)
3. **Cargo Handling**:
   - Assess cargo size (visual inspection)
   - Create cargo tickets with appropriate tier pricing
   - Mark payment status (paid now vs pay on delivery)
4. **Expense Recording**:
   - Record fuel purchases at gas stations
   - Log meal expenses for driver and self
   - Document miscellaneous costs (repairs, tolls)
5. **Cash Management**:
   - Collect passenger fares (cash tickets)
   - Collect cargo fees (when paid on shipment)
   - Deliver collected cash to destination office

**Daily Workflow:**
- Arrive at bus station 30-60 minutes before departure
- Open SOUIGAT app, review assigned trip
- Start trip when bus departs
- Create tickets/expenses throughout 3-6 hour journey
- Work offline in dead zones, app auto-syncs when signal returns
- End trip upon arrival at destination
- Hand over physical cash to office staff

**System Permissions:**
- ✅ View assigned trips only (cannot see other conductors' trips)
- ✅ Start/end assigned trips
- ✅ Create passenger tickets (unlimited)
- ✅ Create cargo tickets (unlimited)
- ✅ Record trip expenses (unlimited)
- ✅ View current trip summary (revenue, expenses, profit)
- ❌ Cannot edit or delete tickets after creation
- ❌ Cannot create trips
- ❌ Cannot access web portal
- ❌ Cannot view historical data beyond current trip
- ❌ Cannot view other offices' data

**Pain Points Addressed:**
- Paper ticket management eliminated (no physical books to carry)
- Automatic calculations (no manual math for ticket totals)
- Offline operation ensures no functionality loss in dead zones
- Instant feedback on trip performance (running revenue total)

### 3.2 Office Staff - Web Portal User (Branch Level)

**Profile & Context:**
- **Role**: Administrative personnel at branch offices
- **Demographics**: 30-50 years old, moderate computer literacy
- **Technical Environment**: Office desktops/laptops, stable internet (ADSL/fiber)
- **Work Hours**: Standard business hours (08:00-17:00)

**Primary Responsibilities:**
1. **Trip Planning**:
   - Create trips in system based on director's schedule decisions
   - Assign buses and conductors
   - Set route pricing (system suggests defaults from pricing table)
   - Handle trip cancellations when necessary
2. **Financial Monitoring**:
   - View ongoing and completed trips for their office
   - Review daily revenue and expense summaries
   - Verify cash collected matches system records
   - Identify discrepancies for follow-up
3. **Reporting**:
   - Generate daily financial summaries
   - Export trip data to Excel for local backup
   - Prepare weekly/monthly reports for office manager
4. **Fallback Operations**:
   - Manually enter tickets from paper if conductor's phone fails
   - Handle customer inquiries using ticket search

**Daily Workflow:**
- Morning: Create scheduled trips based on director's plan
- Throughout day: Monitor trip status, answer customer questions
- Evening: Review completed trips, verify cash deliveries, generate daily report
- Export data to Excel for office records

**System Permissions:**
- ✅ Create trips for their assigned office
- ✅ Edit trips before conductor starts them
- ✅ Cancel trips with documented reason
- ✅ View all trips for their office (past and present)
- ✅ View all tickets (passenger and cargo) for their office
- ✅ View all expenses for their office
- ✅ Generate office-level reports (daily, weekly, monthly)
- ✅ Export office data to Excel
- ✅ Manually enter tickets from paper (emergency fallback)
- ❌ Cannot view other offices' data
- ❌ Cannot edit or delete tickets (only admin)
- ❌ Cannot manage users or system settings
- ❌ Cannot access audit logs
- ❌ Cannot modify pricing configuration

**Pain Points Addressed:**
- Trip creation streamlined (3-5 minutes vs 15-20 minutes manual)
- Instant visibility into ongoing trips (no waiting for conductor return)
- Automated reconciliation (system calculates totals automatically)
- Excel export preserves existing workflow habits

### 3.3 Main Admin (Headquarters) - Full System Access

**Profile & Context:**
- **Role**: Senior management or IT-savvy administrative staff at HQ
- **Typical Users**: Director, financial controller, operations manager
- **Technical Environment**: Office computers, reliable internet, often newer hardware
- **Responsibilities**: Strategic oversight, system configuration, dispute resolution

**Primary Responsibilities:**
1. **System-Wide Monitoring**:
   - View financial performance across all offices
   - Compare route profitability
   - Analyze conductor performance metrics
   - Monitor system health and sync status
2. **Configuration Management**:
   - Set default pricing for routes
   - Add new buses to system (matricule registration)
   - Create user accounts for new conductors and office staff
   - Deactivate users who leave company
3. **Data Correction**:
   - Edit tickets when legitimate errors occur (with audit trail)
   - Delete duplicate entries
   - Correct expense records
   - Handle data quality issues
4. **Strategic Reporting**:
   - Generate company-wide financial reports
   - Export monthly data for accounting department
   - Analyze trends for pricing optimization
   - Prepare executive dashboards for director

**Daily Workflow:**
- Morning: Review overnight sync logs, check for data issues
- Monitor real-time system-wide dashboard
- Handle escalated issues (ticket disputes, data corrections)
- Weekly: Review route performance, adjust pricing as needed
- Monthly: Export data for accounting, generate executive reports

**System Permissions:**
- ✅ ALL permissions of Office Staff across ALL offices
- ✅ View system-wide data (all offices, all conductors, all trips)
- ✅ Edit tickets (passenger and cargo) with full audit trail
- ✅ Delete tickets (rare, logged in audit_log)
- ✅ Edit and delete expenses
- ✅ Manage users: Create, edit, deactivate conductors and office staff
- ✅ Manage buses: Add matricules, edit details, mark inactive
- ✅ Configure route pricing: Set defaults for passenger and cargo
- ✅ View complete audit logs (all changes by all admins)
- ✅ Trigger manual database backups
- ✅ Export system-wide data to Excel
- ✅ Access system health metrics and logs

**Pain Points Addressed:**
- Single source of truth for company-wide performance
- Transparent audit trail for all data modifications
- Ability to correct legitimate errors without data loss
- Strategic analytics enabling data-driven decisions

### 3.4 Secondary Stakeholders

**Director (Decision Maker)**
- Does not directly use system
- Reviews executive reports generated by admin
- Makes strategic decisions based on system analytics
- Approves major configuration changes (pricing, expansion)

**Drivers**
- Not system users
- Indirectly benefit from reduced conductor workload
- May receive meal expense allocations tracked in system

**Travel Agencies (External)**
- Sell tickets outside SOUIGAT system
- Conductor records their pre-sold tickets as "agency_presale"
- Future integration possible (Phase 2+)

**Accounting Department**
- Receives monthly Excel exports from admin
- Uses data for tax filing and financial statements
- Future direct integration possible

**Passengers & Cargo Senders**
- Not system users
- Benefit from more accurate ticketing
- Receive ticket numbers verbally from conductor

---

## 4. System Architecture Overview

### 4.1 High-Level Architecture

SOUIGAT follows a **three-tier client-server architecture** with offline-first mobile capability:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Tier                    │
├─────────────────────────────────────────────────────────┤
│  Android Mobile App          │  Web Portal              │
│  (Kotlin/Java Native)        │  (HTML5/CSS3/JavaScript) │
│  - Conductor Interface       │  - Office Staff UI       │
│  - Offline SQLite Storage    │  - Admin Interface       │
│  - Background Sync Service   │  - Responsive Design     │
└──────────────┬───────────────┴──────────────┬───────────┘
               │                              │
               │  HTTPS/REST API              │  HTTPS/REST API
               │  (JSON Payloads)             │  (JSON Payloads)
               │                              │
┌──────────────▼──────────────────────────────▼───────────┐
│                   Business Logic Tier                    │
├─────────────────────────────────────────────────────────┤
│           RESTful API Backend                           │
│           (Django Python / Node.js Express)             │
│  - JWT Authentication                                   │
│  - Role-Based Access Control (RBAC)                     │
│  - Business Rule Validation                             │
│  - Sync Conflict Resolution                             │
│  - Report Generation Engine                             │
└──────────────┬──────────────────────────────────────────┘
               │
               │  SQL Queries
               │  (PostgreSQL Protocol)
               │
┌──────────────▼──────────────────────────────────────────┐
│                      Data Tier                          │
├─────────────────────────────────────────────────────────┤
│  PostgreSQL Database (Primary Storage)                  │
│  - Financial Transactions                               │
│  - User Accounts & Permissions                          │
│  - Audit Logs                                           │
│  - Configuration Data                                   │
│                                                         │
│  File Storage (Backups & Exports)                       │
│  - Daily Database Dumps                                 │
│  - Excel Export Files                                   │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Mobile App Architecture (Offline-First)

**Core Design Principle**: App must function fully without internet connectivity.

**Local Storage Layer (SQLite Embedded Database):**
```
passenger_tickets_local
├── ticket_number (PRIMARY KEY)
├── trip_id
├── conductor_id
├── origin
├── destination
├── price
├── payment_source
├── ticket_date
├── synced_at (NULL = pending sync)
└── created_at

cargo_tickets_local (similar structure)
trip_expenses_local (similar structure)
```

**Synchronization Service:**
- **Trigger**: Background service runs every 60 seconds
- **Connectivity Check**: `navigator.connection.type` or Android `ConnectivityManager`
- **If Online**:
  1. Query local DB: `SELECT * FROM *_local WHERE synced_at IS NULL`
  2. Batch POST to API (max 50 records per request)
  3. On success: Update `synced_at = server_timestamp`
  4. On failure: Retry with exponential backoff (60s, 120s, 240s, 480s)
- **If Offline**: No action, continues polling

**Conflict Resolution Strategy**:
- **Primary approach**: Last-Write-Wins (LWW)
- **Rationale**: Conductors cannot edit tickets after creation (create-only), so conflicts are rare
- **Edge case**: If admin edits ticket while conductor's delayed sync uploads same ticket, server timestamp prevails

### 4.3 Web Portal Architecture

**Single-Page Application (SPA) Pattern:**
- Frontend: React.js or Vue.js (or vanilla JavaScript for simpler MVP)
- Backend API calls: RESTful JSON endpoints
- Session management: JWT tokens stored in browser localStorage
- No client-side caching of financial data (always fetch fresh from server for accuracy)

**Responsive Design:**
- Desktop-first (primary use case: office computers)
- Tablet-compatible (for admin reviewing reports on iPad)
- Mobile-friendly (emergency access from phone browser)

### 4.4 Key Architectural Decisions

**Decision 1: Native Android vs Cross-Platform**

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| Native Android | Smallest APK (5-8MB), best performance on low-end devices, full offline SQLite support | Platform-specific code, no iOS support | ✅ **CHOSEN** |
| Flutter | Single codebase, good performance | 15-20MB APK overhead, less mature offline story | ❌ Rejected |
| React Native | JavaScript familiarity | Performance issues on 1GB RAM devices | ❌ Rejected |

**Rationale**: Target users have low-end Android devices (1GB RAM common). Native Android produces smallest APK and smoothest offline experience.

**Decision 2: Algeria-Based Hosting vs International Cloud**

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| Algeria VPS (Icosnet, AT) | Lower latency, data sovereignty, cost-effective (10,000 DA/month), local support | Limited scalability | ✅ **CHOSEN** |
| AWS/Azure | Global infrastructure, advanced features | 3-5x cost, data sovereignty concerns, latency | ❌ Rejected |

**Rationale**: Government-licensed company prefers data hosted in Algeria. Projected load (8 conductors, 5 offices) easily handled by VPS. Can migrate later if scale requires.

**Decision 3: PostgreSQL vs MySQL vs MongoDB**

| Database | Pros | Cons | Decision |
|----------|------|------|----------|
| PostgreSQL | Superior JSON support (audit logs), excellent reporting, ACID compliance | Slightly more complex setup | ✅ **CHOSEN** |
| MySQL | Simpler, wider hosting support | Weaker JSON support, less robust for complex queries | ❌ Rejected |
| MongoDB | Flexible schema | No transactions (critical for financial data), harder to generate reports | ❌ Rejected |

**Rationale**: Financial data requires strong ACID guarantees. Complex reporting queries (JOIN across trips, tickets, expenses) perform better on relational DB. PostgreSQL's JSONB type perfect for flexible audit logs.

**Decision 4: Monolithic vs Microservices**

| Architecture | Pros | Cons | Decision |
|--------------|------|------|----------|
| Monolithic | Simpler deployment, lower cost, easier to maintain for small team | Harder to scale specific components | ✅ **CHOSEN for MVP** |
| Microservices | Independent scaling, technology flexibility | Increased complexity, higher hosting cost | ⏳ Phase 2 consideration |

**Rationale**: Current scale (8 conductors, 5 offices, ~500 trips/year) doesn't justify microservices complexity. Single Django/Node app sufficient. Can refactor later if needed.

### 4.5 API Design Principles

**RESTful Endpoints:**
```
POST   /api/auth/login                    - User authentication
GET    /api/trips/my-trips                - Conductor's assigned trips
PATCH  /api/trips/{id}/start              - Start trip
PATCH  /api/trips/{id}/complete           - End trip
POST   /api/tickets/passenger             - Create passenger ticket
POST   /api/tickets/cargo                 - Create cargo ticket
POST   /api/expenses                      - Record expense
GET    /api/reports/daily?office_id={id}  - Daily summary
GET    /api/reports/trip/{trip_id}        - Per-trip report
POST   /api/export/excel                  - Generate Excel export
```

**Authentication:**
- JWT (JSON Web Tokens) with 24-hour expiration
- Refresh token mechanism for mobile app (7-day expiration)
- Role claims embedded in token (`role: 'conductor' | 'office_staff' | 'admin'`)

**Authorization (Role-Based Access Control):**
- Middleware checks JWT claims on every API request
- Example: `GET /api/trips` 
  - If `role = 'conductor'`: Return only trips where `conductor_id = user_id`
  - If `role = 'office_staff'`: Return trips where `office_id = user_office_id`
  - If `role = 'admin'`: Return all trips (no filter)

**Error Handling:**
```json
{
  "success": false,
  "error_code": "DUPLICATE_TICKET",
  "message": "Ticket number PT-20260204-0042 already exists",
  "details": {
    "existing_ticket_id": 1234,
    "created_at": "2026-02-04T10:15:32Z"
  }
}
```

---

## 5. Complete Database Schema

### 5.1 Entity-Relationship Overview

**Core Entities (9 tables):**
1. `offices` - Branch locations and headquarters
2. `buses` - Vehicle fleet with government matricules
3. `users` - Conductors, office staff, and admins
4. `trips` - Scheduled bus routes with pricing
5. `passenger_tickets` - Individual passenger ticket records
6. `cargo_tickets` - Cargo shipment records
7. `trip_expenses` - Operational expenses per trip
8. `audit_log` - Immutable log of admin data modifications
9. `pricing_config` - Default price suggestions per route

**Relationships Summary:**
- `offices` (1) ─────< `users` (M)
- `offices` (1) ─────< `trips` (M)
- `buses` (1) ───────< `trips` (M)
- `users` (1) ───────< `trips` (M) [as conductor]
- `users` (1) ───────< `trips` (M) [as creator]
- `trips` (1) ───────< `passenger_tickets` (M)
- `trips` (1) ───────< `cargo_tickets` (M)
- `trips` (1) ───────< `trip_expenses` (M)

### 5.2 Detailed Table Definitions

#### Table: `offices`
**Purpose**: Store branch office and headquarters information.

```sql
CREATE TABLE offices (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    location VARCHAR(200),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Example data:
INSERT INTO offices (name, location, phone, is_active) VALUES
('Ouargla Main Office', 'Route de Ghardaia, Ouargla 30000', '029123456', TRUE),
('Algiers Branch', 'Place des Martyrs, Algiers 16000', '021654321', TRUE),
('Oran Branch', 'Boulevard de la Revolution, Oran 31000', '041987654', TRUE);
```

**Business Rules:**
- Office names must be unique system-wide
- Cannot delete offices (set `is_active = FALSE` instead for audit trail)
- Every user and trip must belong to an office

**Estimated Row Count**: ~10-15 rows (current 5 offices + future expansion)

---

#### Table: `buses`
**Purpose**: Predefined list of buses identified by government matricules.

```sql
CREATE TABLE buses (
    id SERIAL PRIMARY KEY,
    matricule VARCHAR(50) NOT NULL UNIQUE,
    internal_number VARCHAR(20),
    capacity INTEGER DEFAULT 45,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Example data:
INSERT INTO buses (matricule, internal_number, capacity, notes) VALUES
('4567-23-16', 'Bus #5', 45, 'New Mercedes 2024 model'),
('3421-14-05', 'Bus #7', 50, 'Older Volvo, good condition');
```

**Business Rules:**
- Matricule format follows Algerian vehicle registration (XXXX-YY-ZZ)
- `internal_number` is optional display name for staff convenience
- Buses cannot be deleted (set `is_active = FALSE` when retired)
- Capacity is informational only (not enforced in MVP)

**Estimated Row Count**: ~15-20 rows (current 10-12 buses + retired vehicles)

---

#### Table: `users`
**Purpose**: All system users including conductors, office staff, and admins.

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('conductor', 'office_staff', 'admin')),
    office_id INTEGER NOT NULL REFERENCES offices(id),
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_office ON users(office_id);

-- Example data:
INSERT INTO users (username, password_hash, role, office_id, full_name, phone) VALUES
('youssef', '$2b$12$KpGxYqq...', 'conductor', 1, 'Youssef Chikhi', '0661234567'),
('fatima.admin', '$2b$12$Abc123...', 'admin', 1, 'Fatima Benali', '0770123456'),
('ahmed.office', '$2b$12$Xyz789...', 'office_staff', 2, 'Ahmed Mansour', '0551987654');
```

**Business Rules:**
- Usernames must be unique and lowercase (enforced at application layer)
- Passwords hashed using bcrypt (salt rounds: 12, industry standard)
- Role determines system permissions (enforced at API middleware)
- Conductors belong to single home office (can work trips from other offices)
- Deactivated users (`is_active = FALSE`) retain data integrity
- `last_login` updated on successful authentication (for security monitoring)

**Security Considerations:**
- Password complexity: Minimum 8 characters, at least one number (enforced at API)
- Failed login attempts: Lock account after 5 failures within 15 minutes
- Password reset: Admin-initiated (conductor calls office, staff resets)

**Estimated Row Count**: ~30-50 rows (8 conductors + 15 office staff + 3 admins + future hires)

---

#### Table: `trips`
**Purpose**: Core entity linking buses, conductors, routes, and pricing.

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
    status VARCHAR(20) NOT NULL DEFAULT 'scheduled' 
        CHECK (status IN ('scheduled', 'cancelled', 'in_progress', 'completed')),
    
    -- Pricing (stored per-trip for historical accuracy)
    passenger_base_price DECIMAL(10,2) NOT NULL CHECK (passenger_base_price > 0),
    cargo_small_price DECIMAL(10,2) NOT NULL CHECK (cargo_small_price > 0),
    cargo_medium_price DECIMAL(10,2) NOT NULL CHECK (cargo_medium_price > 0),
    cargo_large_price DECIMAL(10,2) NOT NULL CHECK (cargo_large_price > 0),
    
    -- Timestamps tracking trip lifecycle
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT
);

CREATE INDEX idx_trips_conductor ON trips(conductor_id);
CREATE INDEX idx_trips_office ON trips(office_id);
CREATE INDEX idx_trips_status ON trips(status);
CREATE INDEX idx_trips_departure ON trips(departure_datetime);
CREATE INDEX idx_trips_route ON trips(route_origin, route_destination);

-- Example trip:
INSERT INTO trips (
    trip_code, bus_id, driver_name, route_origin, route_destination, 
    departure_datetime, conductor_id, created_by_user_id, office_id, 
    passenger_base_price, cargo_small_price, cargo_medium_price, cargo_large_price
) VALUES (
    'OUA-ALG-20260205-0900', 1, 'Ahmed Benali', 'Ouargla', 'Algiers',
    '2026-02-05 09:00:00', 1, 2, 1,
    2200.00, 800.00, 1500.00, 2500.00
);
```

**Business Rules:**
- `trip_code` auto-generated: `{origin_abbrev}-{dest_abbrev}-{YYYYMMDD}-{HHMM}`
  - Example: `OUA-ALG-20260205-0900` (Ouargla to Algiers, Feb 5 2026, 09:00)
- `departure_datetime` must be future when creating (validation at API layer)
- Same bus cannot have overlapping trips (validation query checks +/- 6 hours)
- Status transitions: `scheduled → in_progress → completed` or `scheduled → cancelled`
- Conductor cannot start trip more than 2 hours before scheduled departure
- Cancelled trips retain all data (including any tickets created before cancellation)
- Pricing stored per-trip (not referenced from pricing_config) to capture historical pricing

**Status Lifecycle:**
```
scheduled → in_progress → completed
    ↓
cancelled (terminal state)
```

**Validation Example (Prevent Double-Booking):**
```sql
-- Check if bus is available at selected time
SELECT COUNT(*) FROM trips
WHERE bus_id = {selected_bus}
  AND departure_datetime BETWEEN 
      ('{selected_time}' - INTERVAL '2 hours') 
      AND ('{selected_time}' + INTERVAL '6 hours')
  AND status IN ('scheduled', 'in_progress');
-- If COUNT > 0, reject trip creation
```

**Estimated Row Count**: ~500-600 rows per year (1-2 trips/day × 365 days)

---

#### Table: `passenger_tickets`
**Purpose**: Individual passenger ticket records.

```sql
CREATE TABLE passenger_tickets (
    id SERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    origin VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL CHECK (price > 0),
    payment_source VARCHAR(20) NOT NULL DEFAULT 'onboard_cash'
        CHECK (payment_source IN ('onboard_cash', 'agency_presale')),
    ticket_date DATE NOT NULL,
    
    -- Sync and audit metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP,
    is_manual_entry BOOLEAN DEFAULT FALSE,
    manual_entry_by_user_id INTEGER REFERENCES users(id)
);

CREATE INDEX idx_passenger_tickets_trip ON passenger_tickets(trip_id);
CREATE INDEX idx_passenger_tickets_conductor ON passenger_tickets(conductor_id);
CREATE INDEX idx_passenger_tickets_date ON passenger_tickets(ticket_date);
CREATE INDEX idx_passenger_tickets_payment ON passenger_tickets(payment_source);

-- Ticket number auto-generation format:
-- PT-{YYYYMMDD}-{sequence}
-- Example: PT-20260205-0001, PT-20260205-0002, ...
-- Sequence resets daily at midnight
```

**Business Rules:**
- `ticket_number` must be globally unique (enforced by database constraint)
- Ticket number generation: `PT-{DATE}-{sequence}` where sequence resets daily
- Origin/destination can differ from trip origin/destination (intermediate stops)
- Price can be less than trip base price (children, discounts, intermediate stops)
- `payment_source` distinguishes:
  - `onboard_cash`: Conductor collected cash from passenger
  - `agency_presale`: Passenger showed paper ticket bought at external agency
- `synced_at NULL` indicates ticket created offline, pending server sync
- `is_manual_entry = TRUE` when office staff enters from paper (phone failure fallback)
- Manual entries require `manual_entry_by_user_id` (audit who entered)

**Critical for Financial Reporting:**
- Revenue = SUM(price) for all tickets
- Cash collected by conductor = SUM(price WHERE payment_source = 'onboard_cash')
- Agency revenue = SUM(price WHERE payment_source = 'agency_presale')

**Estimated Row Count**: ~15,000-20,000 rows per year (30 tickets/trip × 500 trips)

---

#### Table: `cargo_tickets`
**Purpose**: Cargo shipment records.

```sql
CREATE TABLE cargo_tickets (
    id SERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    origin VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    cargo_tier VARCHAR(10) NOT NULL CHECK (cargo_tier IN ('small', 'medium', 'large')),
    price DECIMAL(10,2) NOT NULL CHECK (price > 0),
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_date DATE NOT NULL,
    
    -- Optional delivery tracking (MVP: not enforced)
    delivery_type VARCHAR(20) DEFAULT 'unknown'
        CHECK (delivery_type IN ('to_office', 'direct_delivery', 'unknown')),
    delivery_office_id INTEGER REFERENCES offices(id),
    
    -- Sync and audit metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP,
    is_manual_entry BOOLEAN DEFAULT FALSE,
    manual_entry_by_user_id INTEGER REFERENCES users(id)
);

CREATE INDEX idx_cargo_tickets_trip ON cargo_tickets(trip_id);
CREATE INDEX idx_cargo_tickets_conductor ON cargo_tickets(conductor_id);
CREATE INDEX idx_cargo_tickets_date ON cargo_tickets(ticket_date);
CREATE INDEX idx_cargo_tickets_tier ON cargo_tickets(cargo_tier);
CREATE INDEX idx_cargo_tickets_paid ON cargo_tickets(is_paid);

-- Ticket number format: CT-{YYYYMMDD}-{sequence}
-- Example: CT-20260205-0001
```

**Business Rules:**
- Cargo tiers based on physical size (conductor visual assessment):
  - **Small**: Shoebox-sized packages, purses, small boxes
  - **Medium**: Microwave-sized items, medium boxes
  - **Large**: Appliance-sized cargo, large boxes, furniture
- `is_paid` distinguishes:
  - `TRUE`: Sender paid conductor at shipment time (cash collected)
  - `FALSE`: Pay on delivery (conductor responsible to collect from receiver)
- `delivery_type` is informational only in MVP (not used for business logic)
- `delivery_office_id` populated when cargo delivered to office (receiver picks up there)
- Cargo pricing NOT distance-based (flat per tier, unlike passenger tickets)

**Removed Fields (Per Client Request):**
- ~~`receiver_name`~~: Conductor verbally confirms recipient identity (not stored)
- ~~`receiver_phone`~~: Not tracked in MVP; conductor's responsibility
- ~~`receiver_signature`~~: Delivery confirmation not captured (Phase 2 feature)

**Estimated Row Count**: ~5,000-6,000 rows per year (10 tickets/trip × 500 trips)

---

#### Table: `trip_expenses`
**Purpose**: Operational expenses recorded during trips.

```sql
CREATE TABLE trip_expenses (
    id SERIAL PRIMARY KEY,
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    conductor_id INTEGER NOT NULL REFERENCES users(id),
    expense_category VARCHAR(20) NOT NULL CHECK (expense_category IN ('fuel', 'food', 'other')),
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    description TEXT,
    expense_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP
);

CREATE INDEX idx_expenses_trip ON trip_expenses(trip_id);
CREATE INDEX idx_expenses_category ON trip_expenses(expense_category);
CREATE INDEX idx_expenses_date ON trip_expenses(expense_date);

-- Business rule constraint (enforced at API layer):
-- IF expense_category = 'other' THEN description IS NOT NULL
```

**Business Rules:**
- All expenses must be linked to a trip (no standalone expenses)
- `expense_category` options:
  - `fuel`: Gas station fuel purchases
  - `food`: Meals for driver and conductor
  - `other`: Miscellaneous (tolls, emergency repairs, parking fees)
- `expense_category = 'other'` requires `description` (enforced at API, not DB constraint)
- Conductors have full discretion on expense amounts (validated by office later)
- Negative amounts not allowed (refunds handled differently in accounting)
- Multiple expenses per category allowed per trip (e.g., two fuel stops)

**Reimbursement Process (Outside System):**
- Conductor pays expenses from personal cash during trip
- Office reimburses conductor at trip end (physical cash handover)
- System tracks expenses for profit calculation, not reimbursement status

**Estimated Row Count**: ~1,500-2,000 rows per year (3 expenses/trip × 500 trips)

---

#### Table: `audit_log`
**Purpose**: Immutable log of all admin modifications to financial data.

```sql
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL CHECK (action IN (
        'edit_ticket', 'delete_ticket', 'edit_cargo', 'delete_cargo',
        'edit_expense', 'delete_expense', 'edit_trip', 'cancel_trip',
        'edit_user', 'deactivate_user', 'edit_pricing'
    )),
    table_name VARCHAR(50) NOT NULL,
    record_id INTEGER NOT NULL,
    old_values JSONB,
    new_values JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT
);

CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_table_record ON audit_log(table_name, record_id);

-- Example audit entry:
INSERT INTO audit_log (user_id, action, table_name, record_id, old_values, new_values, ip_address) VALUES
(2, 'edit_ticket', 'passenger_tickets', 123, 
 '{"price": 2000, "origin": "Ouargla", "destination": "Ghardaia"}',
 '{"price": 2200, "origin": "Ouargla", "destination": "Algiers"}',
 '192.168.1.100');
```

**Business Rules:**
- Audit log is **append-only** (no UPDATE or DELETE operations allowed)
- Triggered automatically by admin actions (application-level, not DB trigger)
- `old_values` and `new_values` store complete record state as JSONB
- Only admin role actions logged (conductor ticket creation NOT audited)
- Retention: Permanent (required for financial audits and regulatory compliance)
- Used for: Dispute resolution, regulatory audits, fraud detection

**What Gets Audited:**
- Admin editing ticket prices or details
- Admin deleting duplicate tickets
- Admin correcting expense amounts
- Admin cancelling trips
- Admin deactivating users
- Admin changing route pricing

**What Does NOT Get Audited:**
- Normal conductor ticket creation (too high volume)
- Office staff viewing reports (read-only operations)
- User login/logout events (separate security log in Phase 2)

**Estimated Row Count**: ~200-300 rows per year (admin corrections are rare)

---

#### Table: `pricing_config`
**Purpose**: Default price suggestions per route.

```sql
CREATE TABLE pricing_config (
    id SERIAL PRIMARY KEY,
    route_origin VARCHAR(100) NOT NULL,
    route_destination VARCHAR(100) NOT NULL,
    passenger_base_price DECIMAL(10,2) NOT NULL CHECK (passenger_base_price > 0),
    cargo_small_price DECIMAL(10,2) NOT NULL CHECK (cargo_small_price > 0),
    cargo_medium_price DECIMAL(10,2) NOT NULL CHECK (cargo_medium_price > 0),
    cargo_large_price DECIMAL(10,2) NOT NULL CHECK (cargo_large_price > 0),
    effective_from DATE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(route_origin, route_destination, effective_from)
);

CREATE INDEX idx_pricing_route ON pricing_config(route_origin, route_destination);
CREATE INDEX idx_pricing_active ON pricing_config(is_active);

-- Example pricing data:
INSERT INTO pricing_config (route_origin, route_destination, passenger_base_price, 
    cargo_small_price, cargo_medium_price, cargo_large_price, effective_from) VALUES
('Ouargla', 'Algiers', 2200.00, 800.00, 1500.00, 2500.00, '2026-01-01'),
('Ouargla', 'Oran', 2800.00, 1000.00, 2000.00, 3000.00, '2026-01-01'),
('Algiers', 'Constantine', 1800.00, 700.00, 1300.00, 2200.00, '2026-01-01');
```

**Business Rules:**
- **Not enforced**: Pricing is suggestion only; admin/office can override when creating trip
- Multiple versions per route supported via `effective_from` (historical pricing)
- Active pricing (`is_active = TRUE`, most recent `effective_from`) used for new trip creation
- Pricing assumed symmetric (Ouargla→Algiers same price as Algiers→Ouargla)
- Admin can update prices without affecting existing trips (trips store pricing snapshot)
- When creating trip, system queries:
  ```sql
  SELECT * FROM pricing_config
  WHERE route_origin = '{selected_origin}'
    AND route_destination = '{selected_destination}'
    AND is_active = TRUE
  ORDER BY effective_from DESC
  LIMIT 1
  ```

**Price Update Workflow:**
1. Admin decides to increase Ouargla→Oran passenger price from 2,800 to 3,000 DA
2. Admin navigates to Pricing Configuration page
3. Selects route, enters new prices, sets `effective_from = tomorrow`
4. System creates new row with `effective_from = 2026-02-06`
5. Marks previous row `is_active = FALSE` (keeps for historical reference)
6. Future trips auto-fill 3,000 DA; existing trips unaffected

**Estimated Row Count**: ~20-30 rows (5 routes × 4-6 pricing updates per year)

---

### 5.3 Database Size Estimates

**Year 1 Projections (500 trips):**
- offices: 10 rows × 500 bytes = 5 KB
- buses: 15 rows × 500 bytes = 7.5 KB
- users: 30 rows × 800 bytes = 24 KB
- trips: 500 rows × 1.5 KB = 750 KB
- passenger_tickets: 15,000 rows × 500 bytes = 7.5 MB
- cargo_tickets: 5,000 rows × 500 bytes = 2.5 MB
- trip_expenses: 1,500 rows × 400 bytes = 600 KB
- audit_log: 200 rows × 2 KB = 400 KB
- pricing_config: 20 rows × 500 bytes = 10 KB

**Total Database Size (Year 1)**: ~11.8 MB (data only)
**With Indexes**: ~18-20 MB
**With Daily Backups (365 days retained)**: ~7.3 GB

**5-Year Projections (2,500 trips):**
- passenger_tickets: 75,000 rows = 37.5 MB
- cargo_tickets: 25,000 rows = 12.5 MB
- trip_expenses: 7,500 rows = 3 MB
- Other tables: ~2 MB
- **Total: ~55 MB** (easily manageable)

---

## 6. Detailed Workflows & Business Processes

### 6.1 Trip Creation Workflow

**Trigger**: Director decides a trip needs to be scheduled.

**Actors**: Office Staff or Admin (web portal)

**Pre-conditions**:
- Bus must exist in system (matricule registered)
- Conductor must exist and be active
- Route pricing should exist in pricing_config (optional, can enter manually)

**Detailed Steps**:

**Step 1: Director Communication (Outside System)**
- Time: ~18:00 day before trip
- Director calls office: *"Send Bus 4567-23-16 to Algiers tomorrow at 09:00, driver Ahmed Benali, assign conductor Youssef"*
- Office staff notes details (paper notepad or verbally)

**Step 2: Office Staff Login**
- Navigate to: `https://souigat.company.dz`
- Enter credentials: username `ahmed.office`, password
- System validates against `users` table:
  ```sql
  SELECT * FROM users 
  WHERE username = 'ahmed.office' 
    AND is_active = TRUE
  ```
- On success: Generate JWT token with claims `{user_id: 3, role: 'office_staff', office_id: 1}`
- Redirect to dashboard

**Step 3: Navigate to Trip Creation**
- Dashboard shows: "Today's Trips: 2 completed, 0 active"
- Click button: **[+ Create New Trip]**
- System loads trip creation form

**Step 4: Fill Trip Details**

**4a. Bus Selection**
- Dropdown populated from: `SELECT * FROM buses WHERE is_active = TRUE ORDER BY internal_number`
- Options displayed: "4567-23-16 (Bus #5)", "3421-14-05 (Bus #7)", etc.
- Staff selects: "4567-23-16 (Bus #5)"

**4b. Driver Name**
- Free text input (driver not a system user, just metadata)
- Staff enters: "Ahmed Benali"

**4c. Route Selection**
- **Origin**: Dropdown or autocomplete
  - Options: Ouargla, Algiers, Oran, Constantine, Ghardaia, Biskra, Annaba
  - Staff selects: "Ouargla"
- **Destination**: Same dropdown
  - Staff selects: "Algiers"

**4d. Departure DateTime**
- Date picker: Staff selects Feb 5, 2026
- Time picker (15-minute intervals): Staff selects 09:00
- Combined: `departure_datetime = '2026-02-05 09:00:00'`

**4e. Conductor Assignment**
- Dropdown populated from:
  ```sql
  SELECT * FROM users 
  WHERE role = 'conductor' 
    AND is_active = TRUE 
    AND office_id = {current_office_id}
  ORDER BY full_name
  ```
- Options: "Youssef Chikhi", "Karim Messaoudi", etc.
- Staff selects: "Youssef Chikhi"

**Step 5: Auto-Fill Pricing**
- System queries pricing_config:
  ```sql
  SELECT * FROM pricing_config
  WHERE route_origin = 'Ouargla'
    AND route_destination = 'Algiers'
    AND is_active = TRUE
  ORDER BY effective_from DESC
  LIMIT 1
  ```
- If found (example result):
  - Passenger: 2,200 DA
  - Cargo Small: 800 DA
  - Cargo Medium: 1,500 DA
  - Cargo Large: 2,500 DA
- Form fields pre-filled (editable)
- If NOT found: Fields blank, staff must enter manually

**Step 6: Staff Adjusts Pricing (Optional)**
- Example scenario: High season, increase passenger to 2,400 DA
- Staff edits passenger field: `2200` → `2400`
- Note: This edit stored in `trips` table only (doesn't update `pricing_config`)

**Step 7: Validation & Submission**
- Staff clicks **[Create Trip]** button
- Client-side validation:
  - All required fields filled? ✓
  - Departure time is future? ✓
  - Prices are positive numbers? ✓
- POST request to API: `/api/trips/create`

**Step 8: Server-Side Validation**

**8a. Bus Availability Check**
```sql
SELECT COUNT(*) FROM trips
WHERE bus_id = {selected_bus_id}
  AND departure_datetime BETWEEN 
      '{selected_datetime}' - INTERVAL '2 hours'
      AND '{selected_datetime}' + INTERVAL '6 hours'
  AND status IN ('scheduled', 'in_progress');
```
- If COUNT > 0: Return error `"Bus 4567-23-16 already assigned to trip OUA-ORA-20260205-0730"`
- If COUNT = 0: Continue

**8b. Conductor Availability Check**
```sql
SELECT COUNT(*) FROM trips
WHERE conductor_id = {selected_conductor_id}
  AND departure_datetime BETWEEN 
      '{selected_datetime}' - INTERVAL '2 hours'
      AND '{selected_datetime}' + INTERVAL '6 hours'
  AND status IN ('scheduled', 'in_progress');
```
- If COUNT > 0: Return error `"Conductor Youssef already assigned to another trip"`
- If COUNT = 0: Continue

**8c. Future Time Check**
```sql
-- Server time must be less than departure_datetime
IF '{departure_datetime}' <= NOW() THEN
  RETURN ERROR "Departure time must be in the future"
END IF
```

**Step 9: Generate Trip Code**
- Format: `{origin_abbrev}-{dest_abbrev}-{YYYYMMDD}-{HHMM}`
- Example: `OUA-ALG-20260205-0900`
- Origin abbreviations (first 3 letters): OUA (Ouargla), ALG (Algiers), ORA (Oran), etc.

**Step 10: Insert Trip Record**
```sql
INSERT INTO trips (
    trip_code, bus_id, driver_name, route_origin, route_destination,
    departure_datetime, conductor_id, created_by_user_id, office_id, status,
    passenger_base_price, cargo_small_price, cargo_medium_price, cargo_large_price,
    created_at
) VALUES (
    'OUA-ALG-20260205-0900', 1, 'Ahmed Benali', 'Ouargla', 'Algiers',
    '2026-02-05 09:00:00', 1, 3, 1, 'scheduled',
    2200.00, 800.00, 1500.00, 2500.00,
    CURRENT_TIMESTAMP
)
RETURNING id;
```

**Step 11: Success Response**
- API returns: `{success: true, trip_id: 456, trip_code: 'OUA-ALG-20260205-0900'}`
- UI displays success message: *"Trip OUA-ALG-20260205-0900 created successfully!"*
- Form clears, returns to dashboard

**Step 12: Conductor Notification (Optional Phase 2)**
- System could send SMS to conductor: *"You are assigned to trip Ouargla→Algiers on Feb 5 at 09:00, Bus 4567-23-16"*
- MVP: Conductor informed verbally or via WhatsApp (outside system)

**Post-conditions**:
- Trip record exists with status = 'scheduled'
- Trip visible in office staff's trip list
- Trip visible in conductor's "My Trips" on mobile app (when he opens it)
- Bus and conductor marked unavailable for overlapping time slots

**Error Handling Examples**:

**Error 1: Bus Double-Booked**
```json
{
  "success": false,
  "error_code": "BUS_UNAVAILABLE",
  "message": "Bus 4567-23-16 is already assigned to trip OUA-ORA-20260205-0730",
  "conflicting_trip": {
    "trip_code": "OUA-ORA-20260205-0730",
    "departure": "2026-02-05T07:30:00Z"
  }
}
```

**Error 2: Past Departure Time**
```json
{
  "success": false,
  "error_code": "INVALID_DATETIME",
  "message": "Departure time must be in the future",
  "server_time": "2026-02-05T09:30:00Z",
  "selected_time": "2026-02-05T09:00:00Z"
}
```

**Edge Cases**:

**Edge Case 1: Same-Day Trip Creation**
- Allowed if departure time is >1 hour in future
- Validation: `departure_datetime >= NOW() + INTERVAL '1 hour'`

**Edge Case 2: Weekend/Holiday Pricing**
- MVP: No automatic adjustment (admin manually sets higher prices in trip creation)
- Phase 2: Could implement pricing rules based on date

**Edge Case 3: Cancelled Trip Re-Creation**
- Allowed to create new trip with same route/time after cancelling previous
- New trip gets new trip_code (different sequence number or timestamp)

---

### 6.2 Conductor Trip Lifecycle Workflow

**Trigger**: Conductor assigned to trip, trip day arrives.

**Actors**: Conductor (mobile app user)

**Pre-conditions**:
- Trip exists with status = 'scheduled'
- conductor_id matches logged-in user
- Mobile app installed and authenticated

**Detailed Steps**:

**PHASE 1: PRE-TRIP (Morning of Trip)**

**Step 1: Conductor Arrives at Bus Station**
- Time: ~08:30 (30 minutes before 09:00 departure)
- Physical action: Conductor boards assigned bus, greets driver

**Step 2: Open Mobile App**
- Tap SOUIGAT icon on Android phone
- App checks authentication status:
  - If JWT token valid (not expired): Auto-login
  - If token expired or first launch: Show login screen

**Step 3: Login (If Required)**
- Enter username: `youssef`
- Enter password: `********`
- App sends POST request: `/api/auth/login`
- Server validates:
  ```sql
  SELECT id, username, role, office_id, full_name
  FROM users
  WHERE username = 'youssef'
    AND password_hash = crypt('{provided_password}', password_hash)
    AND is_active = TRUE
  ```
- On success: Server generates JWT token (24-hour expiration)
- App stores token in Android `SharedPreferences` (secure storage)
- Update `users.last_login = CURRENT_TIMESTAMP`

**Step 4: View Assigned Trips**
- App loads "My Trips" screen
- API call: `GET /api/trips/my-trips?date=2026-02-05&status=scheduled`
- Authorization: JWT token includes `user_id=1, role='conductor'`
- Server query:
  ```sql
  SELECT t.*, b.matricule, b.internal_number
  FROM trips t
  JOIN buses b ON t.bus_id = b.id
  WHERE t.conductor_id = 1
    AND DATE(t.departure_datetime) = '2026-02-05'
    AND t.status = 'scheduled'
  ORDER BY t.departure_datetime
  ```
- App displays trip card:
  ```
  ┌─────────────────────────────────────┐
  │ Trip: Ouargla → Algiers             │
  │ Code: OUA-ALG-20260205-0900         │
  │ Bus: 4567-23-16 (Bus #5)            │
  │ Driver: Ahmed Benali                │
  │ Departure: 09:00                    │
  │ Status: ● Scheduled                 │
  │                                     │
  │        [START TRIP] ✅              │
  └─────────────────────────────────────┘
  ```

**Step 5: Start Trip**
- Conductor taps **[START TRIP]** button
- Confirmation dialog:
  ```
  Start Trip OUA-ALG-20260205-0900?
  
  This will activate ticketing and expense recording.
  
  [Cancel]  [Confirm]
  ```
- Conductor taps **[Confirm]**
- API call: `PATCH /api/trips/456/start`
- Server updates:
  ```sql
  UPDATE trips
  SET status = 'in_progress',
      started_at = CURRENT_TIMESTAMP
  WHERE id = 456
    AND conductor_id = 1
    AND status = 'scheduled'
  RETURNING id, trip_code, status, started_at;
  ```
- Success response: `{success: true, started_at: '2026-02-05T08:42:15Z'}`
- App transitions to "Trip Dashboard" screen showing:
  ```
  ┌─────────────────────────────────────┐
  │ OUA-ALG-20260205-0900               │
  │ Started: 08:42                      │
  │                                     │
  │ Revenue Today:     0 DA             │
  │ Expenses Today:    0 DA             │
  │ Net Profit:        0 DA             │
  │                                     │
  │ [+ Passenger] [+ Cargo] [+ Expense] │
  └─────────────────────────────────────┘
  ```

**PHASE 2: DURING TRIP (Passenger Ticketing)**

**Scenario A: Cash Passenger (No Pre-Bought Ticket)**

**Step 6a: Passenger Boards Without Ticket**
- Time: 08:50 (before departure)
- Physical action: Passenger enters bus, sits down
- Conductor approaches: *"Bonjour, destination?"*
- Passenger responds: *"Algiers"* (full route)

**Step 7a: Conductor Creates Passenger Ticket**
- Tap **[+ Passenger]** button
- Form loads with pre-filled defaults:
  ```
  ┌─────────────────────────────────────┐
  │ New Passenger Ticket                │
  ├─────────────────────────────────────┤
  │ Origin:                             │
  │ [Ouargla                        ▼]  │
  │                                     │
  │ Destination:                        │
  │ [Algiers                        ▼]  │
  │                                     │
  │ Price (DA):                         │
  │ [2,200                            ] │
  │                                     │
  │ Payment Method:                     │
  │ ● Cash (Paid on Board)              │
  │ ○ Agency Pre-Sale                   │
  │                                     │
  │ [Cancel]         [Save Ticket] ✅   │
  └─────────────────────────────────────┘
  ```
- Pre-filled values come from trip record:
  - Origin: `trip.route_origin` → "Ouargla"
  - Destination: `trip.route_destination` → "Algiers"
  - Price: `trip.passenger_base_price` → 2,200 DA
  - Payment: Default "Cash"

**Step 8a: Conductor Verifies/Adjusts (If Needed)**
- For full-route passenger: No changes needed
- For intermediate stop passenger: Would change destination to "Ghardaia", adjust price to 1,200 DA
- For child/discount: Would reduce price to 1,500 DA

**Step 9a: Save Ticket**
- Conductor taps **[Save Ticket]**
- App generates ticket number:
  ```javascript
  // Get today's date
  const today = '20260205';
  
  // Query local DB for today's max sequence
  const maxSeq = SELECT MAX(CAST(SUBSTR(ticket_number, 13) AS INTEGER))
                 FROM passenger_tickets_local
                 WHERE ticket_date = '2026-02-05';
  
  // Increment sequence (or start at 1)
  const newSeq = (maxSeq || 0) + 1;
  const paddedSeq = String(newSeq).padStart(4, '0'); // "0001"
  
  // Generate ticket number
  const ticketNumber = `PT-${today}-${paddedSeq}`; // "PT-20260205-0001"
  ```

**Step 10a: Save to Local Database**
```sql
-- Mobile app SQLite
INSERT INTO passenger_tickets_local (
    ticket_number, trip_id, conductor_id, origin, destination,
    price, payment_source, ticket_date, created_at, synced_at
) VALUES (
    'PT-20260205-0001', 456, 1, 'Ouargla', 'Algiers',
    2200.00, 'onboard_cash', '2026-02-05', CURRENT_TIMESTAMP, NULL
);
```
- Note: `synced_at = NULL` indicates pending server sync

**Step 11a: Immediate Sync (If Online)**
- App checks connectivity: `NetworkInfo.isConnected()`
- If connected (3G/4G available):
  ```
  POST /api/tickets/passenger
  {
      "ticket_number": "PT-20260205-0001",
      "trip_id": 456,
      "origin": "Ouargla",
      "destination": "Algiers",
      "price": 2200,
      "payment_source": "onboard_cash",
      "ticket_date": "2026-02-05"
  }
  ```
- Server validation:
  - Ticket number unique? `SELECT COUNT(*) FROM passenger_tickets WHERE ticket_number = 'PT-20260205-0001'` → Must be 0
  - Trip exists and in progress? `SELECT status FROM trips WHERE id = 456` → Must be 'in_progress'
  - Conductor authorized? `SELECT conductor_id FROM trips WHERE id = 456` → Must equal JWT user_id
  - Price positive? → Must be > 0
- Server inserts:
  ```sql
  INSERT INTO passenger_tickets (
      ticket_number, trip_id, conductor_id, origin, destination,
      price, payment_source, ticket_date, created_at, synced_at
  ) VALUES (
      'PT-20260205-0001', 456, 1, 'Ouargla', 'Algiers',
      2200.00, 'onboard_cash', '2026-02-05', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  );
  ```
- Server response: `{success: true, ticket_id: 7891, synced_at: '2026-02-05T08:55:12Z'}`
- App updates local record:
  ```sql
  UPDATE passenger_tickets_local
  SET synced_at = '2026-02-05T08:55:12Z'
  WHERE ticket_number = 'PT-20260205-0001';
  ```

**Step 12a: UI Feedback**
- Success toast: *"Ticket PT-20260205-0001 created ✓"*
- Trip dashboard updates:
  ```
  Revenue Today:     2,200 DA  (↑ from 0)
  Passengers:        1 ticket
  ```
- Conductor verbally tells passenger: *"Your ticket number is PT-20260205-0001"*
- Note: No physical ticket printed in MVP (conductor may write number on paper as backup)

---

**Scenario B: Agency Pre-Sale Passenger**

**Step 6b: Passenger Shows Paper Ticket**
- Passenger boards holding paper receipt from travel agency
- Paper shows: *"Ouargla → Algiers, 2,200 DA, Paid Feb 4, 2026, Agency: Voyages El-Baraka"*
- Conductor visually inspects paper (checks for authenticity indicators)

**Step 7b: Conductor Creates Agency Pre-Sale Ticket**
- Tap **[+ Passenger]** button (same form as Scenario A)
- Fill identical details:
  - Origin: Ouargla
  - Destination: Algiers
  - Price: 2,200 DA
- **KEY DIFFERENCE**: Select payment method: **○ Agency Pre-Sale**
- Tap **[Save Ticket]**

**Step 8b: Ticket Saved with Agency Flag**
- Ticket number generated: `PT-20260205-0002`
- Saved to local DB with `payment_source = 'agency_presale'`
- Syncs to server (same process as Scenario A)
- **Financial Impact**: Revenue recorded (2,200 DA), but conductor does NOT collect cash
  - Total revenue: 4,400 DA (ticket 0001 + 0002)
  - Cash collected: 2,200 DA (only ticket 0001)
  - Agency pre-sale: 2,200 DA (ticket 0002, money already received by agency)

---

**PHASE 3: DURING TRIP (Cargo Ticketing)**

**Step 13: Cargo Presented for Loading**
- Time: 09:05 (after passenger boarding complete)
- Physical action: Sender arrives with package
- Conductor asks: *"Destination? Size?"*
- Sender responds: *"Algiers, it's a medium box"*

**Step 14: Conductor Assesses Cargo Size**
- Visual inspection: Package is ~50cm × 40cm × 30cm (microwave-sized)
- Conductor determines: **Medium** tier
- Asks: *"Pay now or on delivery?"*
- Sender: *"Pay now"* (hands 1,500 DA cash)

**Step 15: Create Cargo Ticket**
- Tap **[+ Cargo]** button
- Form loads:
  ```
  ┌─────────────────────────────────────┐
  │ New Cargo Ticket                    │
  ├─────────────────────────────────────┤
  │ Origin: [Ouargla]                   │
  │ Destination: [Algiers]              │
  │                                     │
  │ Cargo Size:                         │
  │ ┌────────┐ ┌────────┐ ┌────────┐   │
  │ │ SMALL  │ │ MEDIUM │ │ LARGE  │   │
  │ │   📦   │ │ 📦 📦  │ │📦📦📦  │   │
  │ │ 800 DA │ │1500 DA │ │2500 DA │   │
  │ └────────┘ └────────┘ └────────┘   │
  │                                     │
  │ Payment Status:                     │
  │ ● Paid Now                          │
  │ ○ Pay on Delivery                   │
  │                                     │
  │ [Cancel]         [Save Ticket] ✅   │
  └─────────────────────────────────────┘
  ```

**Step 16: Select Cargo Tier**
- Conductor taps **[MEDIUM]** button
- Price auto-fills: 1,500 DA (from `trip.cargo_medium_price`)
- Payment status: "Paid Now" already selected (default)

**Step 17: Save Cargo Ticket**
- Conductor taps **[Save Ticket]**
- App generates ticket number: `CT-20260205-0001` (cargo sequence separate from passenger)
- Saves to local DB:
  ```sql
  INSERT INTO cargo_tickets_local (
      ticket_number, trip_id, conductor_id, origin, destination,
      cargo_tier, price, is_paid, ticket_date, synced_at
  ) VALUES (
      'CT-20260205-0001', 456, 1, 'Ouargla', 'Algiers',
      'medium', 1500.00, TRUE, '2026-02-05', NULL
  );
  ```
- Syncs to server (if online)
- Success feedback: *"Cargo ticket CT-20260205-0001 created ✓"*
- Dashboard updates:
  ```
  Revenue Today:     5,900 DA  (2,200 + 2,200 + 1,500)
  Passengers:        2 tickets
  Cargo:             1 ticket
  ```
- Conductor tells sender: *"Your cargo number is CT-20260205-0001. Show this to receiver in Algiers."*

---

**PHASE 4: DURING TRIP (Expenses)**

**Step 18: Bus Stops for Fuel**
- Time: 11:00 (2 hours into 6-hour trip)
- Location: Ghardaia fuel station (intermediate city)
- Driver fills tank: 5,000 DA
- Driver hands receipt to conductor

**Step 19: Record Expense**
- Conductor taps **[+ Expense]** button
- Form loads:
  ```
  ┌─────────────────────────────────────┐
  │ Record Expense                      │
  ├─────────────────────────────────────┤
  │ Category:                           │
  │ ● Fuel                              │
  │ ○ Food                              │
  │ ○ Other                             │
  │                                     │
  │ Amount (DA):                        │
  │ [5,000                            ] │
  │                                     │
  │ Description (optional):             │
  │ [                                 ] │
  │                                     │
  │ [Cancel]        [Save Expense] ✅   │
  └─────────────────────────────────────┘
  ```

**Step 20: Fill Expense Details**
- Category: **Fuel** (already selected)
- Amount: 5,000 DA
- Description: (leave empty, optional for fuel/food)
- Tap **[Save Expense]**

**Step 21: Save Expense**
- App generates expense ID (auto-increment in local DB)
- Saves to local DB:
  ```sql
  INSERT INTO trip_expenses_local (
      trip_id, conductor_id, expense_category, amount, 
      expense_date, synced_at
  ) VALUES (
      456, 1, 'fuel', 5000.00, '2026-02-05', NULL
  );
  ```
- Syncs to server (if online)
- Dashboard updates:
  ```
  Revenue Today:     5,900 DA
  Expenses Today:    5,000 DA  (↑ from 0)
  Net Profit:        900 DA     (5,900 - 5,000)
  ```

**Step 22: Additional Expenses**
- 13:30 - Lunch stop: Record expense (Food, 1,500 DA)
- 14:15 - Emergency tire repair: Record expense (Other, 1,000 DA, description: "Flat tire repair")
- Dashboard now shows:
  ```
  Revenue Today:     5,900 DA
  Expenses Today:    7,500 DA  (5,000 + 1,500 + 1,000)
  Net Profit:       -1,600 DA  (negative, but more tickets may come)
  ```

---

**PHASE 5: OFFLINE OPERATION (Example)**

**Step 23: Enter Dead Zone**
- Time: 10:00 (1 hour into trip)
- Location: Mountain pass between Ghardaia and Djelfa
- Connectivity: No 3G/4G signal
- App detects: `navigator.connection.type === 'none'`
- UI indicator: 🔴 **Offline** (status bar shows red dot)

**Step 24: Continue Creating Tickets Offline**
- 10:05 - Passenger boards at intermediate stop: Create ticket PT-20260205-0015
- 10:12 - Another passenger: Create ticket PT-20260205-0016
- 10:25 - Cargo loaded: Create ticket CT-20260205-0005
- All tickets saved to local SQLite with `synced_at = NULL`
- Background sync service polls every 60 seconds, detects no connectivity, waits

**Step 25: Exit Dead Zone**
- Time: 12:00 (2 hours later)
- Location: Approaching Djelfa city
- Connectivity: 4G signal restored
- App detects: `NetworkInfo.isConnected() === true`
- UI indicator: 🟢 **Online**

**Step 26: Automatic Sync**
- Background sync service triggers immediately upon connectivity
- Query local DB:
  ```sql
  SELECT * FROM passenger_tickets_local WHERE synced_at IS NULL;
  SELECT * FROM cargo_tickets_local WHERE synced_at IS NULL;
  SELECT * FROM trip_expenses_local WHERE synced_at IS NULL;
  ```
- Found: 11 passenger tickets, 2 cargo tickets, 1 expense (all pending)
- Batch POST to API:
  ```
  POST /api/sync/batch
  {
      "passenger_tickets": [...11 tickets...],
      "cargo_tickets": [...2 tickets...],
      "expenses": [...1 expense...]
  }
  ```
- Server processes batch (validates, inserts to PostgreSQL)
- Server response: `{success: true, synced_count: 14, synced_at: '2026-02-05T12:03:45Z'}`
- App updates local records:
  ```sql
  UPDATE passenger_tickets_local 
  SET synced_at = '2026-02-05T12:03:45Z'
  WHERE synced_at IS NULL;
  
  -- (repeat for cargo and expenses)
  ```
- UI toast: *"14 records synced ✓"*

---

**PHASE 6: TRIP COMPLETION**

**Step 27: Arrive at Destination**
- Time: 15:00 (6 hours after departure)
- Location: Algiers bus terminal
- Physical action: All passengers exit, all cargo unloaded
- Conductor verifies bus is empty

**Step 28: End Trip**
- Conductor taps **[END TRIP]** button (prominent red button at bottom of screen)
- Confirmation dialog shows trip summary:
  ```
  ┌─────────────────────────────────────┐
  │ End Trip OUA-ALG-20260205-0900?     │
  ├─────────────────────────────────────┤
  │ Passenger Tickets:   28             │
  │ Cargo Tickets:       9              │
  │ Total Revenue:       72,400 DA      │
  │                                     │
  │ Fuel:                5,000 DA       │
  │ Food:                1,500 DA       │
  │ Other:               1,000 DA       │
  │ Total Expenses:      7,500 DA       │
  │                                     │
  │ NET PROFIT:         64,900 DA ✅    │
  │                                     │
  │ Pending Sync:       0 records       │
  │                                     │
  │ [Cancel]      [Complete Trip] ✅    │
  └─────────────────────────────────────┘
  ```
- Conductor reviews summary, verifies counts
- Taps **[Complete Trip]**

**Step 29: Final Sync Check**
- App queries: `SELECT COUNT(*) FROM *_local WHERE synced_at IS NULL`
- If pending records exist:
  - Attempt immediate sync
  - If sync fails (no connectivity): Show warning *"Trip completed but 3 records pending sync. Will sync automatically when online."*
  - Allow completion anyway (offline-first design)
- If no pending records: Proceed immediately

**Step 30: Update Trip Status**
- API call: `PATCH /api/trips/456/complete`
- Server updates:
  ```sql
  UPDATE trips
  SET status = 'completed',
      completed_at = CURRENT_TIMESTAMP
  WHERE id = 456
    AND conductor_id = 1
    AND status = 'in_progress'
  RETURNING *;
  ```
- Success response: `{success: true, completed_at: '2026-02-05T15:02:30Z'}`
- App shows success screen:
  ```
  ┌─────────────────────────────────────┐
  │             Trip Complete!          │
  │                                     │
  │   OUA-ALG-20260205-0900             │
  │                                     │
  │   Net Profit: 64,900 DA ✅          │
  │                                     │
  │   Thank you, Youssef!               │
  │                                     │
  │         [Back to Home]              │
  └─────────────────────────────────────┘
  ```

**Step 31: Physical Cash Handover (Outside System)**
- Conductor calculates cash collected:
  - Passenger cash tickets: 24 tickets × avg 2,200 DA = 52,800 DA
  - Cargo paid now: 7 tickets × avg 1,500 DA = 10,500 DA
  - **Total cash: 63,300 DA**
- Conductor delivers cash to Algiers branch office
- Office staff counts cash, verifies against system
- System shows expected cash: `SELECT SUM(price) FROM passenger_tickets WHERE trip_id = 456 AND payment_source = 'onboard_cash'` + `SELECT SUM(price) FROM cargo_tickets WHERE trip_id = 456 AND is_paid = TRUE`
- If match: Accept cash
- If discrepancy: Investigate (admin may audit later)

**Post-Conditions**:
- Trip status = 'completed'
- All tickets and expenses in central database
- Office can view final trip financials
- Conductor's "My Trips" screen shows trip as completed (archived)

---

### 6.3 Office Financial Review Workflow

**Trigger**: End of day, office staff reviews completed trips.

**Actors**: Office Staff (web portal)

**Pre-conditions**:
- One or more trips completed for the day
- Internet connection at office

**Detailed Steps**:

**Step 1: Office Staff Login**
- Navigate to `https://souigat.company.dz`
- Enter credentials: `ahmed.office` / password
- Dashboard loads

**Step 2: View Daily Summary Dashboard**
```
═══════════════════════════════════════════════════════
  Ouargla Office - Daily Financial Summary
  Tuesday, February 5, 2026
═══════════════════════════════════════════════════════

┌─────────────────────┐  ┌─────────────────────┐
│ Total Revenue       │  │ Total Expenses      │
│ 148,600 DA          │  │ 18,200 DA           │
└─────────────────────┘  └─────────────────────┘

┌─────────────────────┐  ┌─────────────────────┐
│ Net Profit          │  │ Trips Today         │
│ 130,400 DA ✅       │  │ 2 completed         │
└─────────────────────┘  └─────────────────────┘

Revenue Breakdown:
├─ Passenger Tickets (Cash):       105,200 DA  (48 tickets)
├─ Passenger Tickets (Agency):      22,400 DA  (10 tickets)
└─ Cargo Revenue:                   21,000 DA  (18 shipments)

Recent Trips:
┌──────────────────────────────────────────────────────────┐
│ Trip Code       │ Conductor │ Revenue │ Profit │ Status  │
├──────────────────────────────────────────────────────────┤
│ OUA-ALG-...0900 │ Youssef   │ 72,400  │ 64,900 │ ✅ Done │
│ OUA-ORA-...1400 │ Karim     │ 76,200  │ 65,500 │ ✅ Done │
└──────────────────────────────────────────────────────────┘
```

**Step 3: Drill Down to Trip Details**
- Staff clicks trip code link: "OUA-ALG-...0900"
- Trip detail page loads with SQL query:
  ```sql
  SELECT 
      t.*,
      b.matricule,
      u.full_name AS conductor_name,
      (SELECT COUNT(*) FROM passenger_tickets WHERE trip_id = t.id) AS passenger_count,
      (SELECT COUNT(*) FROM cargo_tickets WHERE trip_id = t.id) AS cargo_count,
      (SELECT SUM(price) FROM passenger_tickets WHERE trip_id = t.id) AS passenger_revenue,
      (SELECT SUM(price) FROM cargo_tickets WHERE trip_id = t.id) AS cargo_revenue,
      (SELECT SUM(amount) FROM trip_expenses WHERE trip_id = t.id) AS total_expenses
  FROM trips t
  JOIN buses b ON t.bus_id = b.id
  JOIN users u ON t.conductor_id = u.id
  WHERE t.id = 456;
  ```
- Page displays comprehensive trip view:
  ```
  ═══════════════════════════════════════════════════════
    Trip: OUA-ALG-20260205-0900
  ═══════════════════════════════════════════════════════
  
  Trip Information:
  ├─ Route: Ouargla → Algiers
  ├─ Bus: 4567-23-16 (Bus #5)
  ├─ Driver: Ahmed Benali
  ├─ Conductor: Youssef Chikhi
  ├─ Departed: Feb 5, 2026 08:42
  └─ Completed: Feb 5, 2026 15:02 (Duration: 6h 20m)
  
  Financial Summary:
  ┌────────────────────────────────────────────┐
  │ Passenger Revenue:     61,600 DA  (28 tix) │
  │ ├─ Cash:              53,200 DA  (24 tix)  │
  │ └─ Agency Pre-Sale:    8,400 DA  ( 4 tix)  │
  │                                            │
  │ Cargo Revenue:        10,800 DA  ( 9 tix)  │
  │ ├─ Paid:              8,400 DA  ( 7 tix)   │
  │ └─ Unpaid:            2,400 DA  ( 2 tix)   │
  │                                            │
  │ TOTAL REVENUE:        72,400 DA            │
  │                                            │
  │ Expenses:              7,500 DA  ( 3 rec)  │
  │ ├─ Fuel:              5,000 DA             │
  │ ├─ Food:              1,500 DA             │
  │ └─ Other:             1,000 DA             │
  │                                            │
  │ NET PROFIT:           64,900 DA ✅         │
  └────────────────────────────────────────────┘
  
  [View All Tickets] [View All Expenses] [Export to Excel]
  ```

**Step 4: Review Passenger Tickets**
- Staff clicks **[View All Tickets]**
- Tickets table loads (server query):
  ```sql
  SELECT 
      ticket_number,
      origin,
      destination,
      price,
      payment_source,
      TO_CHAR(created_at, 'HH24:MI') AS created_time
  FROM passenger_tickets
  WHERE trip_id = 456
  ORDER BY created_at;
  ```
- Table displayed:
  ```
  ┌────────────────┬────────┬─────────┬───────┬──────────┬──────┐
  │ Ticket Number  │ Origin │ Dest    │ Price │ Payment  │ Time │
  ├────────────────┼────────┼─────────┼───────┼──────────┼──────┤
  │ PT-...-0001    │ Ouargla│ Algiers │ 2,200 │ Cash     │ 08:45│
  │ PT-...-0002    │ Ouargla│ Algiers │ 2,200 │ Agency   │ 08:47│
  │ PT-...-0003    │ Ouargla│ Ghardaia│ 1,200 │ Cash     │ 08:50│
  │ ...            │ ...    │ ...     │ ...   │ ...      │ ...  │
  │ PT-...-0028    │ Ghardaia│Algiers │ 1,000 │ Cash     │ 10:15│
  └────────────────┴────────┴─────────┴───────┴──────────┴──────┘
  
  Summary:
  ├─ Total Tickets: 28
  ├─ Cash Tickets: 24 (53,200 DA)
  └─ Agency Tickets: 4 (8,400 DA)
  ```

**Step 5: Verify Payment Sources**
- Staff cross-checks physical cash received from Youssef
- Expected cash (from system):
  - Passenger cash: 53,200 DA
  - Cargo paid: 8,400 DA
  - **Total expected: 61,600 DA**
- Physical cash counted: 61,600 DA ✓ (matches)
- If mismatch:
  - Flag for admin investigation
  - Create note in system (Phase 2 feature: discrepancy tracking)
  - Current MVP: Handle via phone call/WhatsApp to Youssef

**Step 6: Review Cargo Tickets**
- Staff clicks **[View Cargo]** tab
- Cargo table query:
  ```sql
  SELECT 
      ticket_number,
      origin,
      destination,
      cargo_tier,
      price,
      is_paid,
      TO_CHAR(created_at, 'HH24:MI') AS created_time
  FROM cargo_tickets
  WHERE trip_id = 456
  ORDER BY created_at;
  ```
- Table displayed:
  ```
  ┌────────────────┬────────┬─────────┬────────┬───────┬──────┬──────┐
  │ Ticket Number  │ Origin │ Dest    │ Tier   │ Price │ Paid │ Time │
  ├────────────────┼────────┼─────────┼────────┼───────┼──────┼──────┤
  │ CT-...-0001    │ Ouargla│ Algiers │ Medium │ 1,500 │ ✅   │ 09:05│
  │ CT-...-0002    │ Ouargla│ Algiers │ Small  │  800  │ ✅   │ 09:12│
  │ CT-...-0003    │ Ouargla│ Algiers │ Large  │ 2,500 │ ❌   │ 09:18│
  │ ...            │ ...    │ ...     │ ...    │ ...   │ ...  │ ...  │
  └────────────────┴────────┴─────────┴────────┴───────┴──────┴──────┘
  
  Summary:
  ├─ Total Cargo: 9 shipments
  ├─ Paid: 7 (8,400 DA cash collected)
  └─ Unpaid: 2 (2,400 DA, pay on delivery)
  
  ⚠️ Note: Unpaid cargo is Youssef's responsibility to collect
  ```

**Step 7: Review Expenses**
- Staff clicks **[View Expenses]** tab
- Expense table query:
  ```sql
  SELECT 
      id,
      expense_category,
      amount,
      description,
      TO_CHAR(created_at, 'HH24:MI') AS created_time
  FROM trip_expenses
  WHERE trip_id = 456
  ORDER BY created_at;
  ```
- Table displayed:
  ```
  ┌────┬───────────┬────────┬───────────────────┬──────┐
  │ ID │ Category  │ Amount │ Description       │ Time │
  ├────┼───────────┼────────┼───────────────────┼──────┤
  │ 78 │ Fuel      │ 5,000  │                   │ 11:00│
  │ 79 │ Food      │ 1,500  │                   │ 13:30│
  │ 80 │ Other     │ 1,000  │ Flat tire repair  │ 14:15│
  └────┴───────────┴────────┴───────────────────┴──────┘
  
  Total Expenses: 7,500 DA
  ```
- Staff reviews for reasonableness:
  - Fuel: 5,000 DA normal for 600km trip ✓
  - Food: 1,500 DA reasonable for 2 people ✓
  - Other: 1,000 DA for repair, description provided ✓
- If suspicious (e.g., expense of 50,000 DA): Flag for admin review

**Step 8: Export to Excel**
- Staff clicks **[Export Trip to Excel]** button
- Server generates Excel file:
  - Sheet 1: Trip summary (revenue, expenses, profit)
  - Sheet 2: Passenger tickets (all 28 rows)
  - Sheet 3: Cargo tickets (all 9 rows)
  - Sheet 4: Expenses (all 3 rows)
- File downloads: `trip_OUA-ALG-20260205-0900.xlsx`
- Staff saves to office computer: `C:\SOUIGAT_Data\2026\February\trip_OUA-ALG-20260205-0900.xlsx`
- Purpose: Local backup for office records

**Step 9: Generate Daily Report**
- Staff navigates back to Dashboard
- Clicks **[Generate Daily Report]**
- Server query:
  ```sql
  SELECT 
      DATE(t.completed_at) AS report_date,
      COUNT(DISTINCT t.id) AS trip_count,
      SUM((SELECT SUM(price) FROM passenger_tickets WHERE trip_id = t.id)) AS passenger_revenue,
      SUM((SELECT SUM(price) FROM cargo_tickets WHERE trip_id = t.id)) AS cargo_revenue,
      SUM((SELECT SUM(amount) FROM trip_expenses WHERE trip_id = t.id)) AS total_expenses,
      (passenger_revenue + cargo_revenue - total_expenses) AS net_profit
  FROM trips t
  WHERE t.office_id = 1
    AND DATE(t.completed_at) = '2026-02-05'
    AND t.status = 'completed'
  GROUP BY DATE(t.completed_at);
  ```
- Excel file generated: `ouargla_daily_report_20260205.xlsx`
- Contains:
  - Summary: Total revenue, expenses, profit
  - List of all trips
  - Payment source breakdown (cash vs agency)
  - Top conductors by revenue
- Staff saves for monthly compilation

**Step 10: End of Day (Optional Tasks)**
- Review week-to-date totals (Monday-Friday)
- Identify trends: *"Agency pre-sales up 15% this week"*
- Note any issues for Monday morning meeting
- Log out of system

**Post-Conditions**:
- Office has verified day's financial activity
- Excel backups stored locally
- Any discrepancies noted for admin follow-up
- Office manager has data for weekly report to HQ

---

### 6.4 Admin System-Wide Monitoring Workflow

**Trigger**: Admin wants to check overall company performance.

**Actors**: Main Admin (web portal)

**Pre-conditions**:
- Multiple offices have completed trips
- Admin has full system access (`role = 'admin'`)

**Detailed Steps**:

**Step 1: Admin Login**
- Navigate to `https://souigat.company.dz`
- Enter credentials: `fatima.admin` / password
- System detects `role = 'admin'`
- Dashboard loads with system-wide view (no office filter)

**Step 2: System-Wide Dashboard**
```
═══════════════════════════════════════════════════════
  SOUIGAT - Company-Wide Dashboard
  Tuesday, February 5, 2026
═══════════════════════════════════════════════════════

Total Revenue (All Offices): 389,500 DA
Total Expenses (All Offices): 52,300 DA
Net Profit: 337,200 DA ✅

Active Trips: 0
Completed Trips Today: 5
Pending Sync Records: 2 (Conductor Ahmed, offline since 14:30)

Performance by Office:
┌────────────────────────────────────────────────────────┐
│ Office          │ Trips │ Revenue  │ Expenses │ Profit │
├────────────────────────────────────────────────────────┤
│ Ouargla         │   2   │ 148,600  │  18,200  │130,400 │
│ Algiers Branch  │   1   │  95,300  │  12,500  │ 82,800 │
│ Oran Branch     │   2   │ 145,600  │  21,600  │124,000 │
└────────────────────────────────────────────────────────┘

Performance by Conductor:
┌────────────────────────────────────────────────────────┐
│ Conductor    │ Trips │ Avg Revenue │ Avg Profit │ Rank │
├────────────────────────────────────────────────────────┤
│ Youssef      │   1   │   72,400    │   64,900   │  1st │
│ Karim        │   1   │   76,200    │   65,500   │  1st │
│ Ahmed        │   1   │   95,300    │   82,800   │  1st │
│ ...          │  ...  │   ...       │   ...      │  ... │
└────────────────────────────────────────────────────────┘
```

**Step 3: Analyze Route Performance**
- Admin clicks **[Route Analysis]** menu item
- Server runs complex query:
  ```sql
  SELECT 
      route_origin || ' → ' || route_destination AS route,
      COUNT(*) AS trip_count,
      AVG(
          (SELECT SUM(price) FROM passenger_tickets WHERE trip_id = t.id) +
          (SELECT SUM(price) FROM cargo_tickets WHERE trip_id = t.id) -
          (SELECT SUM(amount) FROM trip_expenses WHERE trip_id = t.id)
      ) AS avg_profit_per_trip,
      SUM(...) AS total_profit
  FROM trips t
  WHERE t.status = 'completed'
    AND t.completed_at >= CURRENT_DATE - INTERVAL '30 days'
  GROUP BY route
  ORDER BY avg_profit_per_trip DESC;
  ```
- Report displayed:
  ```
  ═══════════════════════════════════════════════════════
    Most Profitable Routes (Last 30 Days)
  ═══════════════════════════════════════════════════════
  
  ┌───────────────────┬───────┬──────────────┬──────────┐
  │ Route             │ Trips │ Avg Profit   │ Total    │
  ├───────────────────┼───────┼──────────────┼──────────┤
  │ Ouargla → Algiers │  18   │ 64,200 DA    │1,155,600 │
  │ Ouargla → Oran    │  15   │ 58,900 DA    │  883,500 │
  │ Algiers → Constan │  12   │ 52,100 DA    │  625,200 │
  │ Ouargla → Ghardaia│   8   │ 35,400 DA    │  283,200 │
  └───────────────────┴───────┴──────────────┴──────────┘
  
  Insights:
  ✅ Ouargla-Algiers is most profitable (highest demand route)
  ⚠️ Ouargla-Ghardaia has lower profit (shorter distance)
  💡 Consider increasing frequency on Ouargla-Algiers route
  ```

**Step 4: Investigate Conductor Performance**
- Admin notices: Conductor Ahmed has lower average revenue than others
- Clicks **[Conductor Performance]** report
- Server query:
  ```sql
  SELECT 
      u.full_name,
      COUNT(DISTINCT t.id) AS trip_count,
      AVG((SELECT COUNT(*) FROM passenger_tickets WHERE trip_id = t.id)) AS avg_passengers_per_trip,
      AVG((SELECT SUM(price) FROM passenger_tickets WHERE trip_id = t.id)) AS avg_passenger_revenue,
      AVG(...) AS avg_profit
  FROM users u
  JOIN trips t ON u.id = t.conductor_id
  WHERE u.role = 'conductor'
    AND t.status = 'completed'
    AND t.completed_at >= CURRENT_DATE - INTERVAL '30 days'
  GROUP BY u.id, u.full_name
  ORDER BY avg_profit DESC;
  ```
- Report shows:
  ```
  ┌──────────┬───────┬──────────┬──────────┬──────────┐
  │ Conductor│ Trips │ Avg Pass │ Avg Rev  │ Avg Prof │
  ├──────────┼───────┼──────────┼──────────┼──────────┤
  │ Youssef  │  18   │   28     │ 61,200   │ 53,800   │
  │ Karim    │  15   │   27     │ 59,400   │ 52,100   │
  │ Ahmed    │  12   │   22     │ 48,500   │ 41,200   │ ⚠️
  │ ...      │  ...  │   ...    │ ...      │ ...      │
  └──────────┴───────┴──────────┴──────────┴──────────┘
  ```
- Admin investigates Ahmed's trips: Clicks Ahmed's name
- Discovers: Ahmed works less popular routes (Ouargla-Ghardaia mostly)
- Conclusion: Not a performance issue, just route assignment difference
- Action: Consider rotating conductors across routes for fairness

**Step 5: Adjust Route Pricing**
- Admin decides: Ouargla→Oran route has high demand, increase pricing
- Navigates to **[Pricing Configuration]** page
- Current pricing query:
  ```sql
  SELECT * FROM pricing_config
  WHERE route_origin = 'Ouargla'
    AND route_destination = 'Oran'
    AND is_active = TRUE
  ORDER BY effective_from DESC
  LIMIT 1;
  ```
- Current pricing (effective Jan 1, 2026):
  - Passenger: 2,800 DA
  - Cargo Small: 1,000 DA
  - Cargo Medium: 2,000 DA
  - Cargo Large: 3,000 DA

**Step 6: Create New Pricing Version**
- Admin clicks **[Edit Route Pricing]** for Ouargla→Oran
- Form loads with current values
- Admin updates:
  - Passenger: 2,800 → **3,000 DA** (+7% increase)
  - Cargo Small: 1,000 → **1,100 DA**
  - Cargo Medium: 2,000 → **2,200 DA**
  - Cargo Large: 3,000 → **3,300 DA**
- Sets effective date: **Feb 6, 2026** (tomorrow)
- Clicks **[Save New Pricing]**

**Step 7: Server Creates New Pricing Record**
```sql
-- Mark old pricing inactive
UPDATE pricing_config
SET is_active = FALSE
WHERE route_origin = 'Ouargla'
  AND route_destination = 'Oran'
  AND is_active = TRUE;

-- Insert new pricing
INSERT INTO pricing_config (
    route_origin, route_destination,
    passenger_base_price, cargo_small_price, cargo_medium_price, cargo_large_price,
    effective_from, is_active
) VALUES (
    'Ouargla', 'Oran',
    3000.00, 1100.00, 2200.00, 3300.00,
    '2026-02-06', TRUE
);
```
- Success message: *"New pricing effective Feb 6, 2026. Future trips will auto-fill updated prices."*
- **Impact**:
  - Trips created tomorrow+ will default to new prices
  - Existing trips (including today's scheduled trips) unaffected
  - Office staff can still override prices when creating trips

**Step 8: Review Audit Log**
- Admin clicks **[Audit Logs]** menu item
- Server query:
  ```sql
  SELECT 
      a.timestamp,
      u.full_name AS admin_name,
      a.action,
      a.table_name,
      a.record_id,
      a.old_values,
      a.new_values
  FROM audit_log a
  JOIN users u ON a.user_id = u.id
  ORDER BY a.timestamp DESC
  LIMIT 50;
  ```
- Recent changes displayed:
  ```
  ═══════════════════════════════════════════════════════
    Recent System Changes (Audit Log)
  ═══════════════════════════════════════════════════════
  
  Feb 5, 16:30 - Admin Fatima edited pricing
  ├─ Table: pricing_config
  ├─ OLD: {"passenger_base_price": 2800, "cargo_small_price": 1000, ...}
  └─ NEW: {"passenger_base_price": 3000, "cargo_small_price": 1100, ...}
  
  Feb 4, 14:35 - Admin Fatima edited ticket PT-20260203-0045
  ├─ Table: passenger_tickets
  ├─ OLD: {"price": 2000, "destination": "Ghardaia"}
  └─ NEW: {"price": 2200, "destination": "Algiers"}
  
  Feb 3, 09:12 - Admin Fatima deleted expense #234
  ├─ Table: trip_expenses
  ├─ Reason: Duplicate entry (same fuel receipt entered twice)
  └─ OLD: {"amount": 5000, "category": "fuel", "trip_id": 420}
  
  [View All] [Export Audit Log]
  ```
- **Purpose**: Transparency for all data modifications, fraud detection, dispute resolution

**Step 9: Handle Data Correction Request**
- Example scenario: Office Algiers calls admin
- Office: *"Conductor Youssef accidentally entered ticket PT-20260205-0015 with price 2,200 DA, but customer paid 2,000 DA (child discount). Can you fix?"*
- Admin: *"Let me check and correct that."*

**Step 10: Edit Ticket (With Audit Trail)**
- Admin searches for ticket: `PT-20260205-0015`
- Ticket details displayed:
  ```
  Ticket: PT-20260205-0015
  ├─ Trip: OUA-ALG-20260205-0900
  ├─ Conductor: Youssef
  ├─ Origin: Ouargla
  ├─ Destination: Algiers
  ├─ Price: 2,200 DA ← CURRENT
  └─ Payment: Cash
  ```
- Admin clicks **[Edit Ticket]**
- Changes price: 2,200 → **2,000 DA**
- Enters edit reason: *"Child discount, office confirmed"*
- Clicks **[Save Changes]**

**Step 11: System Records Edit**
```sql
-- Update ticket
UPDATE passenger_tickets
SET price = 2000.00
WHERE ticket_number = 'PT-20260205-0015'
RETURNING *;

-- Log audit trail
INSERT INTO audit_log (
    user_id, action, table_name, record_id,
    old_values, new_values, timestamp
) VALUES (
    2, 'edit_ticket', 'passenger_tickets', 7815,
    '{"price": 2200, "origin": "Ouargla", "destination": "Algiers", "payment_source": "onboard_cash"}',
    '{"price": 2000, "origin": "Ouargla", "destination": "Algiers", "payment_source": "onboard_cash"}',
    CURRENT_TIMESTAMP
);
```
- Trip revenue automatically recalculates (database triggers or application logic)
- Success message: *"Ticket PT-20260205-0015 updated. Change logged in audit trail."*

**Step 12: Export Monthly Data for Accounting**
- Admin clicks **[Export Monthly Data]** button
- Selects: **January 2026**
- Server generates comprehensive Excel:
  - Sheet 1: **Monthly Summary**
    - Total trips: 42
    - Total revenue: 5,234,600 DA
    - Total expenses: 687,400 DA
    - Net profit: 4,547,200 DA
  - Sheet 2: **All Trips** (42 rows)
  - Sheet 3: **All Passenger Tickets** (1,260 rows)
  - Sheet 4: **All Cargo Tickets** (420 rows)
  - Sheet 5: **All Expenses** (126 rows)
  - Sheet 6: **By Office Summary** (5 rows)
  - Sheet 7: **By Route Summary** (8 routes)
- File size: ~2-3 MB
- Downloads: `souigat_january_2026_complete.xlsx`
- Admin emails file to accounting department: `comptabilite@company.dz`

**Post-Conditions**:
- Admin has complete visibility into company operations
- Route pricing updated for tomorrow
- Data correction completed with full audit trail
- Monthly data exported for tax filing

---

## 7. End-to-End System Flow

### 7.1 Complete Money Flow Example (One Trip)

**Timeline: 36-Hour Journey from Trip Decision to Financial Reporting**

**DAY 1 (February 4, 2026 - Pre-Trip)**

**18:00 - Director Decision**
- Location: Director's office, Ouargla HQ
- Director calls office staff: *"Send Bus 4567-23-16 to Algiers tomorrow at 09:00. Driver Ahmed Benali. Assign Youssef."*
- Office staff notes on paper: Bus, route, time, driver, conductor

**18:15 - Trip Creation in System**
- Office staff logs into web portal
- Navigates to Create Trip form
- Fills details:
  - Bus: 4567-23-16
  - Route: Ouargla → Algiers (600km)
  - Departure: Feb 5, 09:00
  - Driver: Ahmed Benali
  - Conductor: Youssef
- System auto-fills pricing from pricing_config:
  - Passenger: 2,200 DA
  - Cargo S/M/L: 800/1,500/2,500 DA
- Validates (no conflicts), generates trip code: `OUA-ALG-20260205-0900`
- INSERT into `trips` table, status = 'scheduled'

**20:00 - Travel Agency Pre-Sales (Outside System)**
- Location: "Voyages El-Baraka" agency, downtown Ouargla
- 5 walk-in customers buy tickets for tomorrow's Algiers trip
- Agency prints paper receipts: *"Ouargla → Algiers, 2,200 DA, Paid Feb 4"*
- Agency collects: 5 × 2,200 DA = **11,000 DA** (cash goes to agency, not conductor)
- Agency revenue-share with bus company handled offline (not tracked in SOUIGAT MVP)

**DAY 2 (February 5, 2026 - Trip Day)**

**08:30 - Conductor Arrives**
- Location: Ouargla bus terminal
- Youssef boards bus, greets driver Ahmed
- Opens SOUIGAT mobile app on personal phone

**08:35 - App Login & Trip View**
- App auto-logs in (JWT token still valid)
- Loads "My Trips" screen
- API call: `GET /api/trips/my-trips?date=2026-02-05&status=scheduled`
- Server returns trip OUA-ALG-20260205-0900
- App displays trip card with [START TRIP] button

**08:40 - Start Trip**
- Youssef taps [START TRIP]
- API: `PATCH /api/trips/456/start`
- Database: `UPDATE trips SET status = 'in_progress', started_at = '08:40:15'`
- App transitions to Trip Dashboard (revenue: 0, expenses: 0, profit: 0)

**08:45-09:00 - Passenger Boarding (Mixed Payment Sources)**
- **23 passengers WITHOUT tickets** (pay cash on bus):
  - Each pays 2,200 DA cash to Youssef
  - Youssef creates 23 tickets in app:
    - Ticket numbers: PT-20260205-0001 through PT-20260205-0023
    - origin: Ouargla, destination: Algiers, price: 2,200, payment_source: '**onboard_cash**'
  - Saved to local SQLite, synced to server (bus terminal has 4G)
  - **Cash collected: 23 × 2,200 = 50,600 DA** (physical cash in Youssef's pocket/bag)

- **5 passengers WITH agency paper tickets**:
  - Show paper receipts from Voyages El-Baraka
  - Youssef verifies authenticity (agency logo, stamp)
  - Youssef creates 5 tickets in app:
    - Ticket numbers: PT-20260205-0024 through PT-20260205-0028
    - Same details but payment_source: '**agency_presale**'
  - **Cash collected: 0 DA** (money already went to agency)

- **Total passenger revenue: 61,600 DA** (28 tickets)
- **Cash in Youssef's possession: 50,600 DA**

**09:00-09:05 - Cargo Loading**
- **3 Small cargo shipments**:
  - Each 800 DA, all senders pay now
  - Youssef creates tickets: CT-20260205-0001, CT-20260205-0002, CT-20260205-0003
  - cargo_tier: 'small', is_paid: TRUE
  - **Cash collected: 3 × 800 = 2,400 DA**

- **2 Medium cargo shipments**:
  - Each 1,500 DA
  - Sender #1 pays now: CT-20260205-0004, is_paid: TRUE → **+1,500 DA cash**
  - Sender #2 says "receiver will pay": CT-20260205-0005, is_paid: FALSE → **+0 DA cash**

- **1 Large cargo shipment**:
  - 2,500 DA
  - Sender says "pay on delivery": CT-20260205-0006, is_paid: FALSE → **+0 DA cash**

- **Total cargo revenue: 8,400 DA** (6 shipments)
- **Cash collected from cargo: 3,900 DA** (3 small + 1 medium paid now)
- **Unpaid cargo: 4,500 DA** (1 medium + 1 large, Youssef responsible to collect on delivery)

- **Youssef's total cash at departure: 50,600 + 3,900 = 54,500 DA**

**09:10 - Bus Departs**
- Physical: Bus leaves Ouargla terminal, heads north toward Algiers
- App dashboard shows:
  - Revenue: 70,000 DA (61,600 passenger + 8,400 cargo)
  - Expenses: 0 DA
  - Profit: 70,000 DA
  - Passengers: 28, Cargo: 6

**11:00 - Fuel Stop (Ghardaia, 200km into trip)**
- Driver Ahmed pulls into Naftal fuel station
- Fills tank: 45 liters diesel @ 110 DA/liter = 4,950 DA
- Attendant rounds up: **5,000 DA total**
- Ahmed pays with company cash (reimbursed later by office)
- Ahmed hands receipt to Youssef
- Youssef records expense in app:
  - Tap [+ Expense]
  - Category: Fuel, Amount: 5,000, Description: (empty)
  - Save → Creates expense record, synced to server
- Dashboard updates:
  - Revenue: 70,000 DA
  - Expenses: 5,000 DA
  - Profit: 65,000 DA

**13:30 - Lunch Stop (Djelfa, 400km into trip)**
- Bus stops at roadside restaurant
- Driver and Youssef eat lunch: 2 meals @ 750 DA = **1,500 DA**
- Youssef pays from personal cash (reimbursed by office later)
- Records expense: Category: Food, Amount: 1,500
- Dashboard updates:
  - Expenses: 6,500 DA
  - Profit: 63,500 DA

**14:15 - Emergency Expense (Tire Issue)**
- Driver notices flat tire warning
- Pulls over, tire has slow leak
- Roadside repair: **1,000 DA**
- Youssef records: Category: Other, Amount: 1,000, Description: "Flat tire repair near Djelfa"
- Dashboard updates:
  - Expenses: 7,500 DA
  - Profit: 62,500 DA

**15:00 - Arrive Algiers**
- Bus pulls into Algiers terminal (Place des Martyrs)
- All passengers exit, all cargo unloaded
- Youssef taps [END TRIP] in app

**15:02 - Trip Completion**
- App shows trip summary confirmation dialog:
  ```
  Passenger tickets: 28 (61,600 DA)
  Cargo tickets: 6 (8,400 DA)
  Total revenue: 70,000 DA
  
  Expenses: 3 records (7,500 DA)
  
  Net profit: 62,500 DA
  
  Pending sync: 0 records (all synced)
  ```
- Youssef taps [Complete Trip]
- API: `PATCH /api/trips/456/complete`
- Database: `UPDATE trips SET status = 'completed', completed_at = '15:02:30'`
- App shows: *"Trip complete! Thank you, Youssef."*

**15:15 - Cash Handover (Physical, Outside System)**
- Youssef walks to Algiers branch office
- Meets office staff member Amina
- Youssef counts cash: 54,500 DA (passenger cash 50,600 + cargo cash 3,900)
- Hands over cash to Amina
- Amina counts, verifies against system:
  - Opens web portal, views trip OUA-ALG-20260205-0900
  - Expected cash = SUM(passenger WHERE payment='onboard_cash') + SUM(cargo WHERE is_paid=TRUE)
  - Expected = 50,600 + 3,900 = **54,500 DA** ✓ MATCH
- Amina signs paper receipt: *"Received 54,500 DA from Youssef for trip OUA-ALG-20260205-0900"*
- Youssef keeps copy, returns home

**15:30 - Office Staff Review**
- Amina logs into web portal (already logged in)
- Navigates to trip details: OUA-ALG-20260205-0900
- Reviews complete financial breakdown:
  ```
  Passenger Revenue: 61,600 DA (28 tickets)
  ├─ Cash: 50,600 DA (23 tickets) ← Cash received ✓
  └─ Agency Pre-Sale: 11,000 DA (5 tickets) ← Revenue recorded, no cash
  
  Cargo Revenue: 8,400 DA (6 shipments)
  ├─ Paid: 3,900 DA (4 shipments) ← Cash received ✓
  └─ Unpaid: 4,500 DA (2 shipments) ← Youssef to collect on delivery
  
  Total Revenue: 70,000 DA
  Expenses: 7,500 DA
  Net Profit: 62,500 DA ✅
  ```
- Everything matches expectations
- Amina stores physical cash in office safe (deposits to bank next day)

**16:00 - Export Daily Backup**
- Amina clicks [Export to Excel]
- Downloads: `trip_OUA-ALG-20260205-0900.xlsx`
- Saves to: `\\SERVER\Backups\2026\February\trip_OUA-ALG-20260205-0900.xlsx`
- Local backup complete

**DAY 3 (February 6, 2026 - Next Day Reporting)**

**09:00 - Admin Reviews Yesterday's Performance**
- Location: HQ Ouargla, admin Fatima logs in
- Views company-wide dashboard:
  ```
  Feb 5, 2026 Summary (All Offices):
  ├─ Total Trips: 5 completed
  ├─ Total Revenue: 389,500 DA
  ├─ Total Expenses: 52,300 DA
  └─ Net Profit: 337,200 DA ✅
  
  By Office:
  ├─ Ouargla: 2 trips, 130,400 DA profit
  ├─ Algiers: 1 trip, 82,800 DA profit
  └─ Oran: 2 trips, 124,000 DA profit
  ```
- Fatima reviews route performance:
  - Ouargla→Algiers trip (Youssef): 62,500 DA profit ← Good performance
  - Other trips: Similar profitability
- No issues flagged, operations normal

**09:30 - Monthly Export for Accounting**
- Fatima navigates to [Export Monthly Data]
- Selects: **February 2026** (first week only, partial month)
- Generates Excel with all financial data
- Emails to: `comptabilite@company.dz`
- Accounting department will use for:
  - VAT tax filing (19% Algeria rate)
  - Income tax calculation
  - Financial statements

**Key Money Tracking Summary:**

| Category | Amount | Where Is It? |
|----------|--------|--------------|
| Passenger cash collected | 50,600 DA | Office safe (Algiers branch) |
| Cargo cash collected | 3,900 DA | Office safe (Algiers branch) |
| Agency pre-sale revenue | 11,000 DA | With agency (revenue-share handled offline) |
| Unpaid cargo revenue | 4,500 DA | Youssef will collect from receivers, report later |
| **Total trip revenue** | **70,000 DA** | **Tracked in system** |
| Expenses paid | 7,500 DA | Youssef reimbursed by office (outside system) |
| **Net profit** | **62,500 DA** | **Calculated in system** |

**Financial Reconciliation:**
- System tracked revenue: 70,000 DA ✓
- Physical cash verified: 54,500 DA ✓
- Difference (11,000 + 4,500 = 15,500 DA) accounted for:
  - 11,000 DA: Agency pre-sale (revenue-share process happens monthly)
  - 4,500 DA: Unpaid cargo (Youssef responsible, pay-on-delivery model)
- No discrepancies, audit trail complete

---

### 7.2 Offline Sync Flow (Detailed Example)

**Scenario**: Conductor works route with extended dead zone (no cellular coverage for 2 hours).

**Timeline:**

**09:00 - Trip Starts (Online)**
- Conductor Karim starts trip OUA-ORA-20260206-0900 (Ouargla to Oran, different route)
- Location: Ouargla terminal (4G available)
- Creates 5 passenger tickets: PT-20260206-0001 through PT-20260206-0005
- All tickets immediately synced to server via 4G
- Local SQLite: Tickets saved with `synced_at = '09:05:00'` (timestamp from server)
- Local storage: App may delete synced tickets after 7 days (keeps recent for offline reference)

**10:00 - Enter Dead Zone**
- Bus drives into mountainous region (route goes through Atlas Mountains)
- Location: 100km southwest of Ouargla, approaching M'Zab valley
- Connectivity: 4G drops to 3G, then Edge, then "No Service"
- App detects: `ConnectivityManager.getActiveNetworkInfo() == null`
- UI indicator changes: 🟢 Online → 🔴 Offline
- Background sync service polls every 60 seconds, finds no connection, waits

**10:05 - First Offline Ticket**
- Passenger boards at intermediate village stop
- Karim creates ticket: PT-20260206-0006
  - Origin: Guerrara (intermediate stop)
  - Destination: Oran
  - Price: 2,000 DA (reduced, shorter distance)
  - Payment: Cash
- App saves to local SQLite:
  ```sql
  INSERT INTO passenger_tickets_local (
      ticket_number, trip_id, conductor_id, origin, destination,
      price, payment_source, ticket_date, created_at, synced_at
  ) VALUES (
      'PT-20260206-0006', 457, 2, 'Guerrara', 'Oran',
      2000.00, 'onboard_cash', '2026-02-06', '10:05:32', NULL
  );
  --                                              synced_at = NULL ← PENDING
  ```
- UI toast: *"Ticket saved (offline)"* 🔴
- Trip dashboard shows updated revenue, but with offline indicator

**10:12 - More Offline Activity**
- Passenger #2 boards: Create ticket PT-20260206-0007 → saved locally, synced_at = NULL
- Passenger #3 boards: Create ticket PT-20260206-0008
- Passenger #4 boards: Create ticket PT-20260206-0009
- Passenger #5 boards: Create ticket PT-20260206-0010
- Cargo loaded: Create ticket CT-20260206-0001
- Cargo loaded: Create ticket CT-20260206-0002
- All saved locally, pending sync

**11:00 - Fuel Expense (Still Offline)**
- Bus stops at rural gas station (also no internet)
- Driver fills tank: 4,500 DA
- Karim records expense:
  - Category: Fuel, Amount: 4,500
- Expense saved locally:
  ```sql
  INSERT INTO trip_expenses_local (
      trip_id, conductor_id, expense_category, amount, 
      expense_date, created_at, synced_at
  ) VALUES (
      457, 2, 'fuel', 4500.00, '2026-02-06', '11:02:15', NULL
  );
  ```

**11:30 - Continue Offline Operations**
- 8 more passengers board at various stops: Tickets PT-20260206-0011 through PT-20260206-0018
- 3 more cargo items: CT-20260206-0003, CT-20260206-0004, CT-20260206-0005
- All queued locally

**12:00 - Exit Dead Zone**
- Bus approaches Ghardaia city (population 200k, good infrastructure)
- Location: 5km from Ghardaia city center
- Connectivity: 4G signal restored
- App detects: `ConnectivityManager.getActiveNetworkInfo() != null && isConnected()`
- UI indicator: 🔴 Offline → 🟢 Online
- Background sync service immediately triggers (detects connectivity change)

**12:01 - Automatic Sync Begins**

**12:01:00 - Query Pending Records**
```sql
-- Mobile app local SQLite
SELECT * FROM passenger_tickets_local WHERE synced_at IS NULL;
-- Result: 13 tickets (PT-20260206-0006 through PT-20260206-0018)

SELECT * FROM cargo_tickets_local WHERE synced_at IS NULL;
-- Result: 5 cargo tickets (CT-20260206-0001 through CT-20260206-0005)

SELECT * FROM trip_expenses_local WHERE synced_at IS NULL;
-- Result: 1 expense (fuel 4,500 DA)

-- Total pending: 19 records
```

**12:01:05 - Batch Sync Request**
- App sends single HTTP request with all pending records:
  ```
  POST /api/sync/batch
  Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
  Content-Type: application/json
  
  {
      "trip_id": 457,
      "conductor_id": 2,
      "sync_batch_id": "karim_457_20260206_120105",
      "passenger_tickets": [
          {
              "ticket_number": "PT-20260206-0006",
              "origin": "Guerrara",
              "destination": "Oran",
              "price": 2000.00,
              "payment_source": "onboard_cash",
              "ticket_date": "2026-02-06",
              "created_at": "2026-02-06T10:05:32Z"
          },
          // ... 12 more passenger tickets ...
      ],
      "cargo_tickets": [
          {
              "ticket_number": "CT-20260206-0001",
              "origin": "Guerrara",
              "destination": "Oran",
              "cargo_tier": "small",
              "price": 800.00,
              "is_paid": true,
              "ticket_date": "2026-02-06",
              "created_at": "2026-02-06T10:15:45Z"
          },
          // ... 4 more cargo tickets ...
      ],
      "expenses": [
          {
              "expense_category": "fuel",
              "amount": 4500.00,
              "description": null,
              "expense_date": "2026-02-06",
              "created_at": "2026-02-06T11:02:15Z"
          }
      ]
  }
  ```

**12:01:08 - Server-Side Processing**

**Step 1: Authentication & Authorization**
```sql
-- Verify JWT token, extract user_id = 2
SELECT id, role, office_id FROM users 
WHERE id = 2 AND is_active = TRUE;
-- Result: role='conductor', office_id=1

-- Verify conductor authorized for this trip
SELECT conductor_id FROM trips WHERE id = 457;
-- Result: conductor_id=2 ✓ MATCH
```

**Step 2: Transaction Begins**
```sql
BEGIN TRANSACTION;
```

**Step 3: Insert Passenger Tickets**
```sql
-- Loop through 13 passenger tickets
INSERT INTO passenger_tickets (
    ticket_number, trip_id, conductor_id, origin, destination,
    price, payment_source, ticket_date, created_at, synced_at
) VALUES
    ('PT-20260206-0006', 457, 2, 'Guerrara', 'Oran', 2000.00, 'onboard_cash', '2026-02-06', '2026-02-06T10:05:32Z', CURRENT_TIMESTAMP),
    ('PT-20260206-0007', 457, 2, 'Guerrara', 'Oran', 2000.00, 'onboard_cash', '2026-02-06', '2026-02-06T10:12:18Z', CURRENT_TIMESTAMP),
    -- ... 11 more tickets ...
ON CONFLICT (ticket_number) DO NOTHING;
-- ON CONFLICT handles duplicate if ticket somehow synced twice (idempotency)
```

**Step 4: Insert Cargo Tickets**
```sql
INSERT INTO cargo_tickets (
    ticket_number, trip_id, conductor_id, origin, destination,
    cargo_tier, price, is_paid, ticket_date, created_at, synced_at
) VALUES
    ('CT-20260206-0001', 457, 2, 'Guerrara', 'Oran', 'small', 800.00, TRUE, '2026-02-06', '2026-02-06T10:15:45Z', CURRENT_TIMESTAMP),
    -- ... 4 more cargo tickets ...
ON CONFLICT (ticket_number) DO NOTHING;
```

**Step 5: Insert Expense**
```sql
INSERT INTO trip_expenses (
    trip_id, conductor_id, expense_category, amount, description,
    expense_date, created_at, synced_at
) VALUES
    (457, 2, 'fuel', 4500.00, NULL, '2026-02-06', '2026-02-06T11:02:15Z', CURRENT_TIMESTAMP);
```

**Step 6: Validation Checks**
```sql
-- Verify trip still in progress (not completed or cancelled)
SELECT status FROM trips WHERE id = 457;
-- Result: 'in_progress' ✓

-- Verify no duplicate ticket numbers (should be caught by UNIQUE constraint anyway)
SELECT COUNT(*) FROM passenger_tickets 
WHERE ticket_number IN ('PT-20260206-0006', 'PT-20260206-0007', ...);
-- Result: 13 (newly inserted) ✓
```

**Step 7: Commit Transaction**
```sql
COMMIT;
```

**12:01:12 - Server Response (4 seconds later)**
```json
{
    "success": true,
    "synced_count": 19,
    "synced_at": "2026-02-06T12:01:12Z",
    "breakdown": {
        "passenger_tickets": 13,
        "cargo_tickets": 5,
        "expenses": 1
    },
    "trip_summary": {
        "total_revenue": 38400.00,
        "total_expenses": 4500.00,
        "net_profit": 33900.00
    }
}
```

**12:01:13 - Mobile App Updates Local Records**
```sql
-- Update all passenger tickets as synced
UPDATE passenger_tickets_local
SET synced_at = '2026-02-06T12:01:12Z'
WHERE ticket_number IN (
    'PT-20260206-0006', 'PT-20260206-0007', ..., 'PT-20260206-0018'
);

-- Update cargo tickets
UPDATE cargo_tickets_local
SET synced_at = '2026-02-06T12:01:12Z'
WHERE ticket_number IN (
    'CT-20260206-0001', ..., 'CT-20260206-0005'
);

-- Update expense
UPDATE trip_expenses_local
SET synced_at = '2026-02-06T12:01:12Z'
WHERE trip_id = 457 AND created_at = '2026-02-06T11:02:15Z';
```

**12:01:15 - UI Feedback**
- Background sync completes silently (non-intrusive)
- Toast notification: *"19 records synced ✓"* (auto-dismisses after 3 seconds)
- UI indicator: 🟢 Online (stays
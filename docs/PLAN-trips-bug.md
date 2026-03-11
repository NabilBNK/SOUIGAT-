# 🎼 Orchestration Report: Trips Not Showing on Conductor Screen

### Task
Analyze the provided audit report, perform code discovery, and design the solution for the conductor trips missing from the mobile app.

### Mode
PLAN

### Agents Invoked
| # | Agent | Focus Area | Status |
|---|-------|------------|--------|
| 1 | `project-planner` | Analyze the audit and organize the planning phase. | ✅ |
| 2 | `explorer-agent` | Source code discovery (`grep_search`, `view_file`). | ✅ |
| 3 | `backend-specialist` | Verify backend data models and validation constraints. | ✅ |
| 4 | `mobile-developer` | Verify Android Jetpack Compose structure. | ✅ |
| 5 | `debugger` | Root cause analysis and verifying hypotheses. | ✅ |

### Key Findings
1. **[backend-specialist]**: Bug hypotheses #1, #2, and #5 are **INCORRECT**. The Django backend natively filters `conductor_id` based on the user's token. Furthermore, the `Trip` model and `TripSerializer` enforce a strict `conductor_id` non-null constraint. Trips cannot be created without a conductor. 
2. **[mobile-developer]**: The mobile `TripListViewModel` delegates to `TripRepository`, which correctly hits the filtered backend API. However, the `TripListScreen` component is **never invoked** in the navigation graph!
3. **[debugger]**: The actual root cause is **Bug #3**. In `AppNavGraph.kt`, the Dashboard screen's `onNavigateToTrips` callback incorrectly routes you to `NavRoute.History.route`. Moreover, `NavRoute.Trips` doesn't even exist in `NavRoutes.kt`.

### Deliverables
- [x] PLAN.md created

---

## 🛠️ Implementation Plan (Phase 2)

**Files to modify:**
1. **`mobile/app/src/main/java/com/souigat/mobile/ui/navigation/NavRoutes.kt`**
   - Add `object Trips : NavRoute("trips")`.
   - Add a new `BottomNavItem` for `Trips` to the bottom navigation bar.

2. **`mobile/app/src/main/java/com/souigat/mobile/ui/navigation/AppNavGraph.kt`**
   - Hook up `composable(NavRoute.Trips.route) { TripListScreen(...) }`.
   - Fix `DashboardScreen`: `onNavigateToTrips` must route to `NavRoute.Trips.route`.

3. **Verification**
   - Run `./gradlew assembleDebug` to ensure compilation is successful mapping the unresolved `TripListScreen` references correctly.

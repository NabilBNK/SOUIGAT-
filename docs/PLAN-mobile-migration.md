# SOUIGAT Mobile Frontend Migration (Orchestrator Plan)

This plan corresponds to the `/orchestrate` Phase 1 output for the mobile application.

## Overview
We will translate the newly provided UI screens (HTML/Tailwind exports from Google Stitch) into Jetpack Compose for the native Android App (`/mobile`).

## Domains
- **Frontend/UI:** `mobile-developer` (React Native/Flutter excluded -> Jetpack Compose)
- **Design:** `ui-ux-pro-max` (adapting Tailwind utility mappings to Compose attributes)

## Tasks
1. **Asset Migration:** Import `souigat logo NO background.png` into Android Drawables.
2. **Compose Themes:** Standardize fonts, colors, and layout modifiers from the design files.
3. **Screen Creation:** Build Key Composable templates (`souigat_boot_screen`, `souigat_login_screen`, `souigat_dashboard_screen_1`, etc.).
4. **Layout Optimizaton:** Adapt the structure into `LazyColumn` for scrollable lists, adhering to minimum touch targets (48dp target via `Modifier.sizeIn`), and ensuring Material Design constraints.

## Verification
The `test-engineer` and `mobile-developer` workflows dictate a mandatory local build verification (`gradlew assembleDebug`) and execution of `mobile_audit.py`.

# Employee Management — Native Live Android v3.1.0

This is the Android-native live integration build. It does not render PHP pages in a WebView.

## Architecture
Android native UI -> HTTPS native API -> existing PHP/MySQL authority.

The APK never contains MySQL credentials. The access token is stored locally using Android Keystore-backed AES/GCM; the server stores only token hashes.

## Backend deployment
Upload the Native Live backend patch to the alkeynesprjects.com root so it lands under employee-management/.

Import once:
employee-management/database/migrations/2026-09-25-native-app-api.sql

Native application API:
employee-management/api/native-v1.php

Background GPS endpoint reused:
employee-management/api/mobile-geo-ping.php

## Native login
The same existing Employee Management email/password is used.
- admin/hr/manager accounts route to native Admin UI
- employee accounts route to native Employee UI
- app access token and employee tracking token are separate
- no password is retained by the background location service

## Employee workflows connected
- Home
- Attendance state
- Clock In
- Break Start / End
- Clock Out
- authorised work locations
- native foreground/background GPS service
- Leave balances/history + request
- Timesheets + overtime requests
- Field visits + GPS check-in/out
- Expense history + submission
- Payroll records
- Notifications + mark read
- Profile
- Security/session views

## Admin workflows connected
- Dashboard
- Employees directory
- Attendance
- Native MapLibre Live Map with accepted GPS points/routes
- Organisation records
- Locations
- Shifts
- Holidays
- Leave approvals
- Timesheet/OT review
- Field visits
- Expense approvals
- Payroll records
- Reports summary
- Notifications
- Security
- Settings read view

High-impact setup/payroll mutation screens remain governed by the existing server authority; this build does not duplicate payroll calculation logic inside Android.

## Tracking rules
The Android service submits device GPS only when server rules allow it. The server still validates:
- active user/employee
- active attendance session
- live tracking enabled
- shift-only policy when enabled
- coordinate validity
- maximum GPS accuracy
- movement/ping throttling

Tracking cannot bypass Android Force Stop, location being switched off, revoked permissions, device power-off, or OEM restrictions.

## Test order
1. Deploy backend patch.
2. Import the native app migration once.
3. Install the Native Live test APK.
4. Login as Admin and verify Dashboard, Employees, Attendance and Live Map.
5. Login as Employee on the test device.
6. Clock In with precise location permission.
7. Confirm the persistent tracking notification.
8. Move outdoors and verify accepted movement on Admin Live Map.
9. Test screen-off tracking.
10. Test Break, Clock Out, Leave, Field Visit and Expense flows.

## Test signing
v3.1.0 is a direct-install debug/test-signed build. Final production release must use a dedicated permanent signing key so later APK updates install over the production app.

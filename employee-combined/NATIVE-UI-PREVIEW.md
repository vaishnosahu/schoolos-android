# Employee Management — Native Android v3.2.0 / Admin Operations R1

This build uses Android-native screens. It does not render Employee Management PHP pages through WebView.

## Architecture
Native Android UI -> HTTPS native API -> existing PHP/MySQL business authority.

MySQL credentials are never stored in the APK. Native app access tokens are stored with Android Keystore-backed encryption; the backend stores token hashes.

## Required backend
Deploy the Native Admin Operations R1 backend patch after the existing Native Live v3.1 backend/migration.

No new database migration is required for R1.

## Employee operations
- Native login
- Home / attendance status
- Clock In / Break / Clock Out with GPS validation
- Foreground/background authorised tracking
- Leave request
- Overtime request
- Field Visit GPS check-in/out
- Expense submit
- Payroll view
- Notifications
- Profile and security

## Native Admin Operations R1
Existing web authority is now exposed through audited native controls:
- Attendance correction: Clock In, Clock Out, Work Minutes, Note
- Employee create
- Employee profile edit
- Employee active/inactive/terminated status
- Employee access role (company administrator only)
- Employee password reset with session revocation
- Work location/geofence create using manual or native GPS coordinates
- Extra employee work-location assignment
- Shift create
- Effective-date shift assignment
- Client/site create
- Field visit assignment
- Holiday create
- Leave / overtime / expense approvals from the previous native release
- Attendance/tracking/payroll policy settings update
- Payroll period create
- Payroll Generate / Refresh using the existing server formula
- Payroll period lock
- Sign Out Other Sessions

## Authority locks
- Original attendance punches are never overwritten.
- Native corrections create approved rows in attendance_corrections, matching the existing web workflow.
- Payroll calculations remain on the PHP/server authority; Android never recalculates payroll independently.
- Location/geofence validation remains server-side.
- Managers remain limited by existing employee-scope rules.
- HR/company-admin-only operations remain permission checked by the server even if a client attempts to call an endpoint directly.
- Password reset and inactive/terminated employee state revoke relevant native sessions as well as existing browser sessions.

## Deploy/test order
1. Ensure Native Live v3.1 backend and the 2026-09-25 native app migration were already deployed.
2. Upload the R1 backend patch to the alkeynesprjects.com root.
3. No SQL import is needed for R1.
4. Install/update the v3.2.0 test APK.
5. Admin: test Attendance correction on a disposable/test attendance record and verify original punch remains visible in the web audit workflow.
6. Admin: create/edit a test employee, assign location and shift.
7. Admin: create a client/site and field visit; verify employee receives the assignment.
8. Admin: test Settings carefully and restore the original values if intentionally changed during QA.
9. Payroll: use a test/draft period first. Review records before testing Lock.
10. Security: verify Sign Out Other Sessions preserves the current native session.
11. Employee: re-test Clock In, tracking, Break and Clock Out after R1 deployment.

## Android limitations
The app cannot bypass Android Force Stop, disabled location, revoked permissions, device power-off, or OEM battery restrictions.

## Signing
v3.2.0 is a direct-install debug/test-signed build. A production release should use one permanent release signing key for future in-place upgrades.

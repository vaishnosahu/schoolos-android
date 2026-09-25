# SchoolOS Full Native Android R5

This branch is the modular native Android rebuild of SchoolOS.

## Architecture
- Native Android UI only; no WebView rendering in application source.
- Android -> HTTPS SchoolOS Native API -> existing PHP/business authority -> existing MySQL.
- Database credentials and direct MySQL access are never embedded in the APK.
- Existing school/tenant, role, permission, session and subscription authority remains server-side.

## Native source modules
- MainActivity: authentication, school selection, role context and dashboard shell.
- core/SchoolOSActivity: shared authenticated activity/session/navigation base.
- core/UiKit: reusable native design system.
- core/FileBridge: protected download/open handling.
- features/attendance/AttendanceActivity: register, submit, lock/reopen.
- features/messages/MessagesActivity: conversations, replies, compose, notifications/acknowledgements.
- features/sfh/StudyFromHomeActivity: lessons, materials, assignments, submissions, reviews, protected file upload/download.
- features/results/ResultsActivity: published results and official report documents.
- features/gallery/GalleryActivity: protected gallery albums/media.
- features/transport/TransportActivity: routes/trips and authorized trip actions.
- features/common/ModuleActivity: native role-aware list screens for remaining server-authorized modules.

## Build
Requires JDK 17, Android SDK 35 and Gradle 8.9.
Release build:
  gradle --no-daemon clean assembleRelease

The distributed review APK uses a non-production signing key. Production signing must use the SchoolOS release keystore outside source control.

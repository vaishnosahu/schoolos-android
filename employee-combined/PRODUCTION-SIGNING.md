# Employee Management — Permanent Android Signing

Production package ID is locked to `com.alkeynes.employee.management.nativeui`.

Production baseline:
- versionCode: 3410
- versionName: 3.4.1-production
- minSdk: 26
- targetSdk: 35

Never commit the production keystore or passwords to Git, source ZIPs, PHP hosting, or APK assets. Keep an offline backup of the permanent signer.

GitHub Actions uses four protected repository secrets:
- EMS_ANDROID_KEYSTORE_B64
- EMS_ANDROID_KEYSTORE_PASSWORD
- EMS_ANDROID_KEY_ALIAS
- EMS_ANDROID_KEY_PASSWORD

Existing test APKs use temporary/debug certificates. The first permanent production APK therefore requires one uninstall/reinstall. After that first production install, future APKs signed with the same permanent key can update in place.

# Google Play Console – Answers for Freezr

Last updated: 10 September 2025

These answers are based on the current app code (no Internet permission; no analytics/ads; on-device storage; camera for barcode scanning; local notifications).

## Let us know about the content of your app
- Category: House & Home (or Productivity)
- Contains user generated content visible to other users: No
- Contains user-to-user communication or social features: No
- Contains violent/sexual/gambling/drugs content: No
- Uses location or background location: No
- Uses device admin or accessibility for core features: No

## Set privacy policy
- URL: Use the GitHub URL to `PRIVACY_POLICY.md` after pushing, e.g.
  - https://github.com/timothyosullivan/freezr-android/blob/main/PRIVACY_POLICY.md

## App access
- Is any functionality restricted (login, code, region)? No — all functionality is available without special access.
- Instructions for reviewers: Not required.

## Ads
- Does your app contain ads? No

## Content rating (IARC questionnaire hints)
- Cartoon/fantasy/realistic violence: No
- Sexual content, nudity, suggestive themes: No
- Alcohol/tobacco/drugs: No
- Profanity or crude humor: No
- Gambling: No
- User-generated content shared with others: No
- User-to-user communication: No
- Location sharing: No
- Digital purchases: No
- Misc: Uses camera for barcode scanning only; no uploading; shares optional PDFs via the system share sheet (user-initiated).
- Expected rating: Everyone / 3+ (or equivalent region rating)

## Target audience and content
- Primary target age groups: 13–17 and 18+ (not primarily directed to children)
- Appeal to children? No

## Data safety (Summary)
- Is any data collected? No
- Is any data shared with third parties? No
- Is data processed ephemerally on device? Yes (camera frames for barcode scanning), but not collected or transmitted.
- Data types involved: None collected. All user inputs (food names/dates/notes) remain local on device.
- Security practices: No data transmitted; encryption in transit not applicable.
- Data deletion request: Not applicable (no server-side data).

## Government apps
- Is this a government app? No

## Financial features
- Does the app offer financial services (banking, lending, investing, crypto, payments)? No

## Health
- Is this a health/medical app or medical device? No
- Does it collect health data? No

Notes:
- Manifest permissions: CAMERA, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM.
- No INTERNET permission; dependencies include ML Kit barcode which runs on-device and does not imply analytics.

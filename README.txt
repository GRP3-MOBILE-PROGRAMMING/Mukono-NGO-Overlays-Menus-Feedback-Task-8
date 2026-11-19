MUKONO NGO - ANDROID STUDIO PROJECT (UPDATED)

REFERENCE BASIS
This project follows the supplied Mukono NGO Figma wireframes and the assignment instructions:
- Volunteer QR Scanner
- Beneficiary Profile bottom sheet
- Report Issue bottom sheet
- Warehouse Tablet Map with right-side Delivery Manifest

WHAT IS INCLUDED
- Custom Mukono NGO splash screen (replaces the app's normal launcher screen)
- Volunteer scanner screen with dark QR scan panel, live label, scan frame and manual beneficiary entry
- Beneficiary profile sheet with map preview, Kate N. / BNF-2048 details, household size and dietary restrictions
- Report Issue sheet with beneficiary summary, Wrong Person / Address Changed / No Show choices, details field, Submit and Cancel
- Tablet landscape dashboard with the supplied Figma map crop, four colour-coded zones, twelve numbered delivery pins, legend and right-side manifest
- Zone filter buttons
- Demo reassignment action
- XML layouts and Java activity code

HOW IT BEHAVES
- Launching the app opens the custom Mukono NGO splash screen for about 1.4 seconds, then opens the main screen.
- On a phone, tapping the scanner frame or Enter beneficiary ID opens the beneficiary profile sheet.
- The profile sheet has Confirm Delivery and Report Issue actions.
- Report Issue opens a second sheet. It cannot be dismissed by tapping outside or pressing Back, to reduce accidental loss of a long report. Cancel is provided.
- The tablet layout is selected on screens with at least 600dp width in landscape.
- The tablet map uses the exact map artwork cropped from the supplied Figma screenshot, not a generic replacement. The map is an illustrative static map image, not a live GPS map.
- Zone filters show only the matching manifest zone.
- Reassign currently provides a demo response; it is not connected to a server or volunteer database.

IMPORTANT FUNCTIONAL LIMITATIONS
1. QR scanning is currently a visual prototype. It does not activate the camera or decode real QR codes. Tapping the scanner opens the sample beneficiary.
2. Confirm Delivery and Submit Issue show demo confirmation messages only. No backend/database is connected.
3. The tablet map is a static Figma reference image. Live map panning, GPS location, live route calculation and real marker selection are not implemented.
4. Reassign is a demo action. A real volunteer picker and persistent assignment update require app data/backend requirements.
5. This project was reviewed at source/layout level in this environment. Android Studio/Android SDK are not installed here, so a device build could not be executed in this environment.

OPEN IN ANDROID STUDIO
1. Download and extract MukonoNGO_AndroidStudio.zip.
2. Open Android Studio.
3. Choose File > Open.
4. Select the extracted MukonoNGO_AndroidStudio folder containing settings.gradle.
5. Allow Gradle Sync to finish. Install Android SDK Platform 35 and use JDK 17 if prompted.
6. Run on a phone emulator to see the scanner and bottom sheets.
7. Run on a tablet emulator in landscape to see the warehouse map and right-side manifest.

PACKAGE
com.example.mukonongomap

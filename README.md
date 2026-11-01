# PengWa design-system runtime screenshots

Before UI: app/src matches upstream 015774c9c45050ed54fbf384b3fefd0c25417375.
After UI: feat/apply-design-system, including c7da70c (adaptive landscape navigation).
All images are direct Android emulator screencaps, not edited mockups.

MainActivity images use a synthetic identity (preview friend, UUID 00000000-0000-4000-8000-000000000001) and the app's synthetic Lab contacts.
fixture-* images run the shared UI components in the debug-only UiPreviewActivity with fixed state providers. They do not verify real NFC/BLE hardware.

AOSP Android 16 / API 36 ARM64, Pixel 5 profile; normal 1080x2340 at 440dpi; narrow 880x1760 at 440dpi / 1.5x or 2x font; landscape 1760x880 at 440dpi; tablet 1600x2048 at 320dpi. Animations disabled while capturing to avoid transition frames.
Camera scenes use the emulator's synthetic camera, not a computer webcam. No real user data is present.

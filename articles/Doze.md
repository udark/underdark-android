# Testing your app with Doze

You can test Doze mode by following these steps:

1. Configure a hardware device or virtual device with an Android 6.0 (API level 23) or higher system image.
2. Connect the device to your development machine and install your app.
3. Run your app and leave it active.
4. Shut off the device screen. (The app remains active.)
5. Force the system to cycle through Doze modes by running the following commands:
$ adb shell dumpsys battery unplug
$ adb shell dumpsys deviceidle step
You may need to run the second command more than once. Repeat it until the device state changes to idle.
6. Observe the behavior of your app after you reactivate the device. Make sure the app recovers gracefully when the device exits Doze.

# Testing your app with App Standby

To test the App Standby mode with your app:

1. Configure a hardware device or virtual device with an Android 6.0 (API level 23) or higher system image.
2. Connect the device to your development machine and install your app.
3. Run your app and leave it active.
4. Force the app into App Standby mode by running the following commands:
$ adb shell dumpsys battery unplug
$ adb shell am set-inactive <packageName> true
5. Simulate waking your app using the following commands:
$ adb shell am set-inactive <packageName> false
$ adb shell am get-inactive <packageName>
6. Observe the behavior of your app after waking it. Make sure the app recovers gracefully from standby mode. In particular, you should check if your app’s Notifications and background jobs continue to function as expected.
# Underdark Android
http://underdark.io

Peer-to-peer networking library for Android, with Wi-Fi and Bluetooth support.

This repository contains library sources, examples of using it and also short "Getting Started" guide below.

## License
http://underdark.io/LICENSE.txt

Underdark Android is published under the Underdark License, which is modified Apache 2.0 license with added requirement that applications that use the library must add to their app store description the following line: "Mesh networking by http://underdark.io"

## Demo apps
* Android: https://play.google.com/store/apps/details?id=me.solidarity.app
* iOS: http://itunes.apple.com/app/id956548749

Please note that for Bluetooth connection to work on Android, one of the phones must have Android 5.x AND support Bluetooth LE Peripheral mode (have "yes" in the last column here: http://altbeacon.github.io/android-beacon-library/beacon-transmitter-devices.html).

Video demo: http://www.youtube.com/watch?v=ox4dh0s1XTw

## Author
You can contact me via Telegram at http://telegram.me/virlof or by email at virlof@gmail.com

## Installation
First, add underdark repository in your root or app's build.gradle:
```
repositories {
    maven {
        url 'https://dl.bintray.com/underdark/android/'
    }
}
```
Next, add Underdark library dependency in your apps' build.gradle:
```
dependencies {
    compile 'io.underdark:underdark:1.+'
}
```
## Getting started
Underdark API is very simple — it consists of entry class `Underdark` with method `configureTransport()` — it allows you to create `Transport` instance with desired parameters (like network interface type) and set listener for callbacks.

Full documentation resides in javadoc jar, starting from `io.underdark.Underdark` class.
If you're using Android Studio or IntelliJ IDEA, you can automatically download it like that:
```
apply plugin: 'idea'

idea {
    module {
        downloadJavadoc = true
    }
}
```

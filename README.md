micro:bit Android application
=============================

**Build instructions**

* Install needed tools to build the project:
    
    * [Android SDK](http://developer.android.com/sdk/index.html)
    
    * [Gradle](https://gradle.org/gradle-download/) (Minimum version [8.2+](https://developer.android.com/studio/releases/gradle-plugin.html#updating-gradle))

* Fetch submodules `git submodule update --init --recursive`

* Go to root directory and run `./gradlew :app:assembleDebug`. After the build is finished the apk file can be found at `app/build/outputs/apk/app-debug.apk`

* Or run `./gradlew :app:installDebug` to build and install app on plugged android device

## Release process

1. Release https://github.com/microbit-foundation/android-partial-flashing-lib if it has changed since the last release. We tag that library with the version number of the android app.
2. Ensure the pfLibrary submodule is using the commit corresponding to the new android-partial-flashing-lib tag.
3. Increment the version and version name in `AndroidMainfest.xml`. The version must be incremented since the last Play Console upload.
4. Create a GitHub release with a new tag via the web UI (e.g. v3.0.8).
5. GitHub actions will run for the new tag. Download the signed bundle (.aab file) it produces.
6. [Upload to Google Play Console](https://developer.android.com/studio/publish/upload-bundle). Options for next steps include an [internal release](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en-GB) or [staged roll out](https://support.google.com/googleplay/android-developer/answer/6346149?hl=en-GB#zippy=%2Crelease-a-staged-roll-out-to-specific-countries).

## Library documentation

 * [Android-DFU-Library](https://github.com/NordicSemiconductor/Android-DFU-Library)
 * [android-partial-flashing-lib](https://github.com/microbit-foundation/android-partial-flashing-lib)
 * [android-gif-drawable](https://github.com/koral--/android-gif-drawable)

## Potential pitfalls

If Gradle is unable to find the correct Android SDK, check the SDK install location is correctly set on the path.
You should have a ENV variable `ANDROID_SDK_ROOT` pointing to the SDKs location.

You may need to set JAVA_HOME to a v17 JDK.

## Code of Conduct

Trust, partnership, simplicity and passion are our core values we live and breathe in our daily work life and within our projects. Our open-source projects are no exception. We have an active community which spans the globe and we welcome and encourage participation and contributions to our projects by everyone. We work to foster a positive, open, inclusive and supportive environment and trust that our community respects the micro:bit code of conduct. Please see our [code of conduct](https://microbit.org/safeguarding/) which outlines our expectations for all those that participate in our community and details on how to report any concerns and what would happen should breaches occur.

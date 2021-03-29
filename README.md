micro:bit Android application
=============================

**Build instructions**

* Install needed tools to build the project:
    
    * [Android SDK](http://developer.android.com/sdk/index.html)
    
    * [Gradle](https://gradle.org/gradle-download/) (Minimum version [4.6+](https://developer.android.com/studio/releases/gradle-plugin.html#updating-gradle))

* Fetch submodules `git submodule update --init --recursive`

* Go to root directory and run `gradle assembleDebug`. After build is finished, apk file can be found under `~/app/build/outputs/apk/app-debug.apk`

* Or run `gradle installDebug` to build and install app on plugged android device


## Libraries

 * [Android-DFU-Library](https://github.com/NordicSemiconductor/Android-DFU-Library)
 * [android-partial-flashing-lib](https://github.com/microbit-foundation/android-partial-flashing-lib)
 * [android-gif-drawable](https://github.com/koral--/android-gif-drawable)

## Potential Pitfalls

If Gradle is unable to find the correct Android SDK, check the SDK install location is correctly set on the path.
You should have a ENV variable `ANDROID_SDK_ROOT` pointing to the SDKs location.

## Code of Conduct

Trust, partnership, simplicity and passion are our core values we live and breathe in our daily work life and within our projects. Our open-source projects are no exception. We have an active community which spans the globe and we welcome and encourage participation and contributions to our projects by everyone. We work to foster a positive, open, inclusive and supportive environment and trust that our community respects the micro:bit code of conduct. Please see our [code of conduct](https://microbit.org/safeguarding/) which outlines our expectations for all those that participate in our community and details on how to report any concerns and what would happen should breaches occur.

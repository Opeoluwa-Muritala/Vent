# iOS application host

On macOS with Xcode and XcodeGen installed, run `xcodegen generate` in this
directory, open `Events.xcodeproj`, select a signing team, and run Events.
The project definition embeds the existing VentKit framework during an Xcode
build. Generated Xcode projects and local signing details are not committed.

The launch screen uses Info.plist and asset-catalog entries, without a storyboard.
Camera permission is requested only on entering check-in. The native scanner
stops the session and torch when leaving the screen and supports denied
permission recovery through Settings.

No iOS build has been executed on the Windows development host.

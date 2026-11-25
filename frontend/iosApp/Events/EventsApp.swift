import SwiftUI
import UIKit
import VentKit

@main
struct EventsApp: App {
    init() {
        IosCameraBridge.shared.factory = { callback in
            SessionCameraController { code in _ = callback(code) }
        }
    }

    var body: some Scene {
        WindowGroup {
            EventsView().ignoresSafeArea()
        }
    }
}

struct EventsView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

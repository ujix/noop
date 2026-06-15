import SwiftUI
import Combine

// MARK: - NavRouter
//
// A tiny shared navigation hook so a screen can ask the app shell to switch to another top-level
// destination without knowing how that shell is built. The two shells navigate very differently —
// macOS drives a `NavigationSplitView` sidebar selection (`RootView`), iOS uses a `TabView` whose
// "everything else" screens live behind the More tab (`RootTabView`) — so neither exposes a shared
// `selection` binding LiveView could reach. This object is the small, shared bridge between them.
//
// Usage: a screen calls `router.openDevices()`; the shell observes `requestedDestination` and routes
// itself (macOS sets the sidebar selection to `.devices`; iOS presents `DevicesView`). Each consumer
// clears the request once it's handled so the same tap can fire again later. Injected at both app
// roots (`StrandApp`, `StrandiOSApp`) as an `@EnvironmentObject`.
@MainActor
final class NavRouter: ObservableObject {
    /// A top-level destination a screen can ask the shell to open. Deliberately minimal — only the
    /// destinations something actually links to today live here (the Devices manager so far).
    enum Destination: String, Equatable {
        case devices
    }

    /// The destination a screen has asked the shell to open, or nil once handled. Published so the
    /// active shell (macOS sidebar / iOS tab) reacts and routes itself, then resets this to nil.
    @Published var requestedDestination: Destination?

    /// Ask the shell to open the Devices manager (pair / switch bands). The shell decides how.
    func openDevices() { requestedDestination = .devices }
}

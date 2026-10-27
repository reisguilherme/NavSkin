import SwiftUI

@main
@MainActor
struct NavSkinApp: App {
    @StateObject private var store = AppStore()
    @Environment(\.scenePhase) private var scenePhase
    init() { GameTypography.installGlyphRouting() }
    var body: some Scene {
        WindowGroup {
            NavSkinRootView(store: store)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { store.setForeground(true) }
                    else if phase == .background { store.setForeground(false) }
                }
        }
    }
}

import Foundation
import SwiftUI
import UIKit
import CoreText
import MapLibre
import NavSkinCore

/// The font's actual PostScript name is read from the registered supplied file.
@MainActor
enum GameTypography {
    static let root = (Bundle.main.resourceURL ?? Bundle.main.bundleURL).appendingPathComponent("Resources")
    static let catalog = GameAssetCatalog.load(root: root)
    private static var names: [MapTheme: String] = [:]
    private static var attempted: Set<MapTheme> = []
    private static let glyphDelegate = GameGlyphDelegate(root: root, catalog: catalog)
    private static var routingInstalled = false

    static func installGlyphRouting() {
        guard !routingInstalled else { return }
        routingInstalled = true
        // OnlineFileSource has already selected its HTTP loader before URL transformation.
        // Keep the original HTTPS URL and serve its bundled bytes through URLProtocol.
        GameGlyphURLProtocol.router = makeGlyphRouter(root: root, catalog: catalog)
        let configuration = (MLNNetworkConfiguration.sharedManager.sessionConfiguration.copy() as? URLSessionConfiguration) ?? .default
        configuration.protocolClasses = [GameGlyphURLProtocol.self] + (configuration.protocolClasses ?? [])
        MLNNetworkConfiguration.sharedManager.sessionConfiguration = configuration
        MLNOfflineStorage.shared.delegate = glyphDelegate
    }

    static func fontName(theme: MapTheme) -> String? {
        if attempted.contains(theme) { return names[theme] }
        attempted.insert(theme)
        guard let definition = catalog.themes[theme.rawValue]?.ui else { return nil }
        let url = root.appendingPathComponent(definition.file)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor] ?? []
        let actual = descriptors.first.map { CTFontCopyPostScriptName(CTFontCreateWithFontDescriptor($0, 16, nil)) as String }
        guard let name = actual ?? definition.postScriptName, UIFont(name: name, size: 16) != nil else { return nil }
        names[theme] = name
        return name
    }

    static func font(_ style: Font.TextStyle, theme: MapTheme, weight: Font.Weight? = nil) -> Font {
        guard let name = fontName(theme: theme) else {
            let base = Font.system(style)
            return weight.map { base.weight($0) } ?? base
        }
        let size: CGFloat
        switch style {
        case .largeTitle: size = 34
        case .title: size = 28
        case .title2: size = 22
        case .title3: size = 20
        case .headline, .body: size = 17
        case .callout: size = 16
        case .subheadline: size = 15
        case .footnote: size = 13
        case .caption: size = 12
        case .caption2: size = 11
        @unknown default: size = 17
        }
        let base = Font.custom(name, size: size, relativeTo: style)
        return weight.map { base.weight($0) } ?? base
    }

    static func font(size: CGFloat, theme: MapTheme, weight: Font.Weight? = nil) -> Font {
        guard let name = fontName(theme: theme) else { return .system(size: size, weight: weight ?? .regular) }
        let base = Font.custom(name, size: size)
        return weight.map { base.weight($0) } ?? base
    }
}

private struct GameThemeKey: EnvironmentKey { static let defaultValue = MapTheme.gta }
extension EnvironmentValues {
    var gameTheme: MapTheme {
        get { self[GameThemeKey.self] }
        set { self[GameThemeKey.self] = newValue }
    }
}

private struct GameFontModifier: ViewModifier {
    @Environment(\.gameTheme) private var theme
    let style: Font.TextStyle
    let weight: Font.Weight?
    func body(content: Content) -> some View { content.font(GameTypography.font(style, theme: theme, weight: weight)) }
}

private struct SizedGameFontModifier: ViewModifier {
    @Environment(\.gameTheme) private var theme
    let size: CGFloat
    let weight: Font.Weight?
    func body(content: Content) -> some View { content.font(GameTypography.font(size: size, theme: theme, weight: weight)) }
}

extension View {
    func gameFont(_ style: Font.TextStyle, weight: Font.Weight? = nil) -> some View {
        modifier(GameFontModifier(style: style, weight: weight))
    }
    func gameFont(size: CGFloat, weight: Font.Weight? = nil) -> some View {
        modifier(SizedGameFontModifier(size: size, weight: weight))
    }
    func gameNavigationTitle(_ title: String) -> some View {
        modifier(GameNavigationTitleModifier(title: title))
    }
}

private struct GameNavigationTitleModifier: ViewModifier {
    let title: String
    func body(content: Content) -> some View {
        content.navigationTitle(title).toolbar {
            ToolbarItem(placement: .principal) {
                Text(title).gameFont(.headline).lineLimit(1).minimumScaleFactor(0.75)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }
}

/// Resource transformation does not change source/style/tile/sprite requests.
private final class GameGlyphDelegate: NSObject, MLNOfflineStorageDelegate {
    private let router: GameGlyphRouter
    init(root: URL, catalog: GameAssetCatalog) {
        router = makeGlyphRouter(root: root, catalog: catalog)
        super.init()
    }
    func offlineStorage(_ storage: MLNOfflineStorage, urlForResourceOf kind: MLNResourceKind, with url: URL) -> URL {
        guard kind == .glyphs else { return url }
        let transformed = router.transformGlyphURL(url)
        return transformed.isFileURL ? url : transformed
    }
}

private func makeGlyphRouter(root: URL, catalog: GameAssetCatalog) -> GameGlyphRouter {
    GameGlyphRouter(stacks: catalog.fontStacks) { relative in
        let url = root.appendingPathComponent(relative)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }
}

/// Answers only a declared custom-font request that has a real bundled PBF.
private final class GameGlyphURLProtocol: URLProtocol {
    static var router: GameGlyphRouter?
    private let state = NSLock()
    private var cancelled = false

    override class func canInit(with request: URLRequest) -> Bool {
        guard let url = request.url, request.httpMethod == nil || request.httpMethod == "GET" else { return false }
        return router?.transformGlyphURL(url).isFileURL == true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let file = Self.router?.transformGlyphURL(url), file.isFileURL else {
            client?.urlProtocol(self, didFailWithError: URLError(.resourceUnavailable)); return
        }
        do {
            let bytes = try Data(contentsOf: file, options: .mappedIfSafe)
            guard !isCancelled else { return }
            let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: [
                "Content-Type": "application/x-protobuf", "Content-Length": String(bytes.count),
                "Cache-Control": "public, max-age=31536000, immutable"
            ])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            guard !isCancelled else { return }
            client?.urlProtocol(self, didLoad: bytes)
            guard !isCancelled else { return }
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            if !isCancelled { client?.urlProtocol(self, didFailWithError: error) }
        }
    }

    override func stopLoading() { state.lock(); cancelled = true; state.unlock() }
    private var isCancelled: Bool { state.lock(); defer { state.unlock() }; return cancelled }
}

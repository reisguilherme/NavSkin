import Foundation

/// A view of an authored mesh, baked with a fixed ground anchor. Paths are relative
/// to navigation-3d/; no remote resources or per-frame mesh work are needed on the phone.
public struct NavigationAvatarFrame: Equatable {
    public let path: String
    public let yawIndex: Int
    public let pitchIndex: Int
}

public struct NavigationAvatarCatalog: Decodable {
    public struct Theme: Decodable {
        public let label: String
        public let frames: [[String]]
    }

    public let schemaVersion: Int
    public let frameCount: Int
    public let pitches: [Double]
    public let imageSize: Double
    public let displaySize: Double
    public let anchor: [Double]
    public let themes: [String: Theme]

    public init(data: Data) throws {
        let value = try JSONDecoder().decode(Self.self, from: data)
        guard value.schemaVersion == 1, (4...64).contains(value.frameCount),
              (32...512).contains(value.imageSize), (24...120).contains(value.displaySize),
              value.anchor.count == 2, value.anchor.allSatisfy({ $0.isFinite && (0...1).contains($0) }),
              !value.pitches.isEmpty, value.pitches.count <= 8,
              value.pitches.allSatisfy({ $0.isFinite && (0...90).contains($0) }),
              zip(value.pitches, value.pitches.dropFirst()).allSatisfy({ pair in pair.0 < pair.1 }),
              !value.themes.isEmpty else { throw CocoaError(.fileReadCorruptFile) }
        for (theme, entry) in value.themes {
            guard theme.range(of: "^[a-z0-9_-]{1,32}$", options: .regularExpression) != nil,
                  !entry.label.isEmpty, entry.frames.count == value.pitches.count else {
                throw CocoaError(.fileReadCorruptFile)
            }
            for row in entry.frames {
                guard row.count == value.frameCount, row.allSatisfy({ path in
                    path.range(of: "^\(theme)/p[0-9]+/a[0-9]{2}\\.png$", options: .regularExpression) != nil
                }) else { throw CocoaError(.fileReadCorruptFile) }
            }
        }
        self = value
    }

    public static func load(root: URL) -> NavigationAvatarCatalog? {
        guard let data = try? Data(contentsOf: root.appendingPathComponent("navigation-3d/catalog.json")) else { return nil }
        return try? Self(data: data)
    }

    public static func isEnabled(navigationActive: Bool, vibing: Bool) -> Bool { navigationActive || vibing }

    public var iconScale: Double { displaySize / imageSize }
    /// MapLibre applies iconScale to both image size and this image-pixel offset.
    public var offsetX: Double { (0.5 - anchor[0]) * imageSize }
    public var offsetY: Double { (0.5 - anchor[1]) * imageSize }

    public func frame(theme: String, heading: Double, cameraBearing: Double, pitch: Double) -> NavigationAvatarFrame? {
        guard let entry = themes[theme], heading.isFinite, cameraBearing.isFinite, pitch.isFinite else { return nil }
        // Normalize each operand first so even very large finite inputs cannot overflow.
        let relative = GeoMath.normalizeBearing(GeoMath.normalizeBearing(heading) - GeoMath.normalizeBearing(cameraBearing))
        let yaw = Int((relative * Double(frameCount) / 360).rounded()) % frameCount
        let pitchIndex = pitches.indices.min(by: { abs(pitches[$0] - pitch) < abs(pitches[$1] - pitch) }) ?? 0
        return NavigationAvatarFrame(path: entry.frames[pitchIndex][yaw], yawIndex: yaw, pitchIndex: pitchIndex)
    }
}

import Foundation
import SwiftUI
import UIKit
import NavSkinCore

/// System materials provide one composited blur; no map readback or display-link work.
private struct GlassSurface: ViewModifier {
    let radius: CGFloat
    let palette: ThemePalette
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    func body(content: Content) -> some View {
        content
            .background {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay {
                        RoundedRectangle(cornerRadius: radius, style: .continuous)
                            .fill(palette.surface.opacity(reduceTransparency ? 1 : 0.58))
                    }
            }
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(.white.opacity(0.85), lineWidth: 1))
            .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
    }
}

extension View {
    func glassSurface(radius: CGFloat = 24, palette: ThemePalette) -> some View {
        modifier(GlassSurface(radius: radius, palette: palette))
    }
    func interfaceFont(_ style: Font.TextStyle, weight: Font.Weight? = nil) -> some View {
        font(weight.map { Font.system(style, design: .default).weight($0) } ?? Font.system(style, design: .default))
    }
    func interfaceFont(size: CGFloat, weight: Font.Weight = .regular) -> some View {
        font(.system(size: size, weight: weight))
    }
}

extension MapTheme {
    var glassPalette: ThemePalette {
        switch self {
        case .gta: return ThemePalette(surface: 0xF7FAF8, text: 0x16222B, muted: 0x53635B, accent: 0x008EDF, soft: 0xE4F5FF, border: 0xDCE5E0)
        case .rosa: return ThemePalette(surface: 0xFFF8FC, text: 0x4B213D, muted: 0x79546B, accent: 0xDF258B, soft: 0xFFE7F3, border: 0xEFD7E4)
        case .rdr2: return ThemePalette(surface: 0xFBF8F0, text: 0x31291F, muted: 0x73614E, accent: 0x9B3D2C, soft: 0xF5E7D8, border: 0xE3D9CC)
        case .smw: return ThemePalette(surface: 0xF9FAF8, text: 0x16222B, muted: 0x53635B, accent: 0xBE3A2C, soft: 0xF6E8E5, border: 0xDCE5E0)
        case .cyberpunk: return ThemePalette(surface: 0xF8FAFB, text: 0x16222B, muted: 0x53616B, accent: 0x007B90, soft: 0xE2F3F6, border: 0xDCE3E7)
        case .pipboy: return ThemePalette(surface: 0xF4FAEF, text: 0x1F3721, muted: 0x536E50, accent: 0x397D2F, soft: 0xDFEFD5, border: 0xC9DEC0)
        case .hollow: return ThemePalette(surface: 0xF5F7FC, text: 0x202D44, muted: 0x5B6B85, accent: 0x486DA9, soft: 0xE4EAF8, border: 0xD1DBEC)
        case .crossy: return ThemePalette(surface: 0xFAFBF2, text: 0x2C3C35, muted: 0x657660, accent: 0xBC5425, soft: 0xF6E6D8, border: 0xD7E2CA)
        }
    }
}

@MainActor
struct MapDimensionToggle: View {
    @ObservedObject var store: AppStore
    var body: some View {
        Toggle("Mapa 3D", isOn: Binding(get: { store.map3D }, set: { store.setMap3D($0) }))
            .interfaceFont(.subheadline)
            .frame(minHeight: 44)
            .accessibilityHint(store.snapshot.navigationActive ? "Preferência para suas viagens" : "Preferência ao explorar o mapa")
    }
}

@MainActor
struct CatalogBitmap: View {
    let url: URL?
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let url, let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image).resizable().interpolation(.high).scaledToFit()
            } else {
                Image(systemName: "photo.badge.exclamationmark").resizable().scaledToFit()
                    .foregroundStyle(.secondary).padding(8)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

@MainActor
struct MapControl: View {
    let symbol: String
    let label: String
    let palette: ThemePalette
    var selected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 18, weight: .semibold))
                .frame(width: 46, height: 46)
                .foregroundStyle(selected ? palette.surface : palette.text)
                .background(selected ? palette.accent : palette.surface, in: RoundedRectangle(cornerRadius: 15))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(palette.border, lineWidth: 1))
                .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

@MainActor
struct SheetHeading: View {
    let title: String
    let subtitle: String
    let palette: ThemePalette

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).interfaceFont(.title2, weight: .bold).foregroundStyle(palette.text)
            Text(subtitle).interfaceFont(.subheadline).foregroundStyle(palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

@MainActor
struct MessageCard: View {
    let text: String
    var symbol = "info.circle"
    let palette: ThemePalette

    var body: some View {
        Label(text, systemImage: symbol)
            .interfaceFont(.footnote).fixedSize(horizontal: false, vertical: true)
            .foregroundStyle(palette.text).padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.soft, in: RoundedRectangle(cornerRadius: 14))
    }
}

@MainActor
struct ThemeCarousel: View {
    @ObservedObject var store: AppStore
    let onSelect: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(MapTheme.allCases) { theme in
                    Button {
                        store.setTheme(theme)
                        onSelect()
                    } label: {
                        ThemePreviewCard(store: store, theme: theme)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Usar tema \(theme.title)")
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 4)
        }
    }
}

@MainActor
struct ThemePreviewCard: View {
    @ObservedObject var store: AppStore
    let theme: MapTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let image = UIImage(contentsOfFile: store.resources.previewURL(theme: theme).path) {
                Image(uiImage: image).resizable().scaledToFill().frame(width: 216, height: 108).clipped()
            } else {
                Rectangle().fill(theme.palette.soft).frame(width: 216, height: 108)
                    .overlay(Image(systemName: "map").font(.largeTitle).foregroundStyle(theme.palette.muted))
            }
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(theme.title).gameFont(.headline).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    if store.theme == theme { Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.palette.accent) }
                }
                Text(theme.subtitle).interfaceFont(.caption).foregroundStyle(theme.palette.muted).lineLimit(1)
            }
            .foregroundStyle(theme.palette.text).padding(12)
        }
        .frame(width: 216).background(theme.palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(store.theme == theme ? theme.palette.accent : theme.palette.border, lineWidth: store.theme == theme ? 2 : 1))
        .shadow(color: .black.opacity(0.14), radius: 8, y: 4)
        .environment(\.gameTheme, theme)
    }
}

enum NavSkinFormat {
    static func distance(_ meters: Double) -> String {
        guard meters.isFinite, meters >= 0 else { return "—" }
        return meters < 1_000 ? "\(Int(meters.rounded())) m" : String(format: "%.1f km", meters / 1_000)
    }

    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "—" }
        let minutes = Int(min(Double(Int.max / 60), max(1, ceil(seconds / 60))))
        return minutes >= 60 ? "\(minutes / 60) h \(minutes % 60) min" : "\(minutes) min"
    }

    static func arrival(_ milliseconds: Int64) -> String {
        Date(timeIntervalSince1970: Double(milliseconds) / 1_000).formatted(date: .omitted, time: .shortened)
    }

    static func coordinates(_ point: GeoPoint) -> String {
        String(format: "%.5f, %.5f", point.latitude, point.longitude)
    }

    static func maneuverSymbol(_ kind: ManeuverKind) -> String {
        switch kind {
        case .left, .sharpLeft, .slightLeft: return "arrow.turn.up.left"
        case .right, .sharpRight, .slightRight, .exit: return "arrow.turn.up.right"
        case .uturn: return "arrow.uturn.down"
        case .roundabout: return "arrow.triangle.turn.up.right.circle"
        case .arrive: return "flag.checkered"
        case .merge, .fork: return "arrow.triangle.branch"
        default: return "arrow.up"
        }
    }
}

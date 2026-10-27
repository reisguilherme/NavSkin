import Foundation
import SwiftUI
import UIKit
import NavSkinCore

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
            Text(title).gameFont(.title2, weight: .bold).foregroundStyle(palette.text)
            Text(subtitle).gameFont(.subheadline).foregroundStyle(palette.muted)
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
            .gameFont(.footnote).fixedSize(horizontal: false, vertical: true)
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
                    Text(theme.title).gameFont(.headline)
                    Spacer()
                    if store.theme == theme { Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.palette.accent) }
                }
                Text(theme.subtitle).gameFont(.caption).foregroundStyle(theme.palette.muted).lineLimit(1)
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

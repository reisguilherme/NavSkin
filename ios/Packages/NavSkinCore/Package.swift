// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NavSkinCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "NavSkinCore", targets: ["NavSkinCore"])],
    targets: [
        .target(name: "NavSkinCore"),
        .testTarget(name: "NavSkinCoreTests", dependencies: ["NavSkinCore"])
    ],
    swiftLanguageVersions: [.v5]
)

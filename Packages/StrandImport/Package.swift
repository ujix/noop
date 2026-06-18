// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "StrandImport",
    platforms: [.macOS(.v13), .iOS(.v16)],
    products: [.library(name: "StrandImport", targets: ["StrandImport"])],
    dependencies: [
        .package(path: "../WhoopProtocol"),
        .package(path: "../WhoopStore"),
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.0"),
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "6.0.0"),
    ],
    targets: [
        .target(name: "StrandImport", dependencies: [
            "WhoopProtocol", "WhoopStore",
            .product(name: "ZIPFoundation", package: "ZIPFoundation"),
            // Read-only access to a *foreign* SQLite file (the Mi Fitness export);
            // never opens NOOP's own store. Already in the tree via WhoopStore.
            .product(name: "GRDB", package: "GRDB.swift"),
        ]),
        .testTarget(name: "StrandImportTests", dependencies: [
            "StrandImport",
            .product(name: "GRDB", package: "GRDB.swift"),
        ], resources: [
            .copy("Resources"),
        ]),
    ]
)

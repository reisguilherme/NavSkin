import Foundation
import XCTest
@testable import NavSkinCore

final class PlacesStoreTests: XCTestCase {
    private var directory: URL!
    private var file: URL { directory.appendingPathComponent("navigation-places.json") }
    private let point = GeoPoint(latitude: -16.67, longitude: -49.25)

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("NavSkinPlaces-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
    override func tearDownWithError() throws { try FileManager.default.removeItem(at: directory) }

    func testEditsPreserveUUIDCreationTimestampAndUnknownIconsAndDeleteClearsShortcuts() throws {
        var time: Int64 = 1000
        let store = PlacesStore(fileURL: file, now: { time })
        let created = try store.savePlace(name: "Minha casa", point: point, address: "Rua Um", categoryId: "personal", iconId: "home")
        XCTAssertNotNil(UUID(uuidString: created.id))
        try store.setHome(id: created.id)
        try store.setWork(id: created.id)
        time = 9000
        let edited = try store.savePlace(name: " Casa nova ", point: point, iconId: "future-icon", id: created.id)
        XCTAssertEqual(edited.id, created.id)
        XCTAssertEqual(edited.createdAtMs, 1000)
        XCTAssertEqual(edited.updatedAtMs, 9000)
        XCTAssertEqual(edited.name, "Casa nova")
        XCTAssertEqual(edited.asDestination().point, point)
        let restored = PlacesStore(fileURL: file).snapshot
        XCTAssertEqual(restored.savedPlaces, [edited])
        XCTAssertEqual(restored.savedPlaces.first?.iconId, "future-icon")
        XCTAssertEqual(restored.homeId, created.id)
        XCTAssertEqual(restored.workId, created.id)
        try store.deletePlace(id: created.id)
        let removed = PlacesStore(fileURL: file).snapshot
        XCTAssertTrue(removed.savedPlaces.isEmpty)
        XCTAssertNil(removed.homeId)
        XCTAssertNil(removed.workId)
    }

    func testClockRollbackCannotMoveUpdatedTimestampBeforeCommittedEdit() throws {
        var time: Int64 = 1000
        let store = PlacesStore(fileURL: file, now: { time })
        let place = try store.savePlace(name: "Casa", point: point)
        time = -100
        let edited = try store.savePlace(name: "Casa dois", point: point, id: place.id)
        XCTAssertEqual(edited.createdAtMs, 1000)
        XCTAssertEqual(edited.updatedAtMs, 1000)
    }

    func testRecentsDeduplicateByIDOrNearbyNormalizedNameAndRetainTwentyNewest() throws {
        var time: Int64 = 1000
        let store = PlacesStore(fileURL: file, now: { time })
        try store.rememberRecent(Destination(id: "one", name: "Café", point: point, address: "Centro"))
        time += 1
        try store.rememberRecent(Destination(id: "other-provider", name: " café ", point: GeoPoint(latitude: -16.67001, longitude: -49.25001)))
        XCTAssertEqual(store.snapshot.recents.count, 1)
        XCTAssertEqual(store.snapshot.recents.first?.usedAtMs, 1001)
        for index in 0..<25 {
            time += 1
            try store.rememberRecent(Destination(id: "id-\(index)", name: "Destino \(index)", point: GeoPoint(latitude: -16 + Double(index) * 0.01, longitude: -49)))
        }
        let restored = PlacesStore(fileURL: file).snapshot
        XCTAssertEqual(restored.recents.count, 20)
        XCTAssertEqual(restored.recents.first?.destination.id, "id-24")
        XCTAssertEqual(restored.recents.last?.destination.id, "id-5")
    }

    func testResumePersistsOnlyItineraryAndCanBeCleared() throws {
        let store = PlacesStore(fileURL: file)
        let destination = Destination(id: "park", name: "Parque", point: point, address: "Goiânia")
        try store.setResumeDestination(destination)
        XCTAssertEqual(PlacesStore(fileURL: file).snapshot.resumeDestination, destination)
        let source = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        XCTAssertEqual(Set(source.keys), ["schemaVersion", "savedPlaces", "recents", "homeId", "workId", "resumeDestination", "resumeIntermediateStops"])
        try store.setResumeDestination(nil)
        XCTAssertNil(PlacesStore(fileURL: file).snapshot.resumeDestination)
    }

    func testRecentsWithSameIDReplaceDistantEntryButSameNameFarAwayRemainsDistinct() throws {
        let store = PlacesStore(fileURL: file)
        try store.rememberRecent(Destination(id: "one", name: "Café", point: point))
        let far = GeoPoint(latitude: -17, longitude: -49)
        try store.rememberRecent(Destination(id: "one", name: "Café", point: far))
        XCTAssertEqual(store.snapshot.recents.count, 1)
        XCTAssertEqual(store.snapshot.recents.first?.destination.point, far)
        try store.rememberRecent(Destination(id: "two", name: "Café", point: point))
        XCTAssertEqual(store.snapshot.recents.map(\.destination.id), ["two", "one"])
    }

    func testCorruptSourceAndMalformedOptionalStateRemainUntouchedAndBlockMutations() throws {
        let sources = [#"{"schemaVersion":1,"savedPlaces":[{"id":"must-survive"}]}"#,
            #"{"schemaVersion":1,"savedPlaces":[],"recents":"corrupt-array"}"#,
            #"{"schemaVersion":1,"savedPlaces":[],"homeId":"missing"}"#,
            #"{"schemaVersion":2,"savedPlaces":[]}"#]
        for source in sources {
            let bytes = Data(source.utf8)
            try bytes.write(to: file)
            let store = PlacesStore(fileURL: file)
            XCTAssertNotNil(store.snapshot.loadError)
            XCTAssertThrowsError(try store.savePlace(name: "Outro", point: point))
            XCTAssertThrowsError(try store.setResumeDestination(nil))
            XCTAssertEqual(try Data(contentsOf: file), bytes)
        }
    }

    func testVersionZeroMigrationPreservesIdentityCoordinatesAndUnknownIcon() throws {
        try Data(#"{"schemaVersion":0,"savedPlaces":[{"id":"legacy-home","name":"Casa","latitude":-16.67,"longitude":-49.25,"iconId":"old-theme-icon"}],"homeId":"legacy-home"}"#.utf8).write(to: file)
        let store = PlacesStore(fileURL: file)
        XCTAssertNil(store.snapshot.loadError)
        let place = try XCTUnwrap(store.snapshot.savedPlaces.first)
        XCTAssertEqual(place.id, "legacy-home")
        XCTAssertEqual(place.point, point)
        XCTAssertEqual(place.iconId, "old-theme-icon")
        XCTAssertEqual(place.createdAtMs, 0)
        try store.setWork(id: place.id)
        let source = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        XCTAssertEqual(source["schemaVersion"] as? Int, 1)
        XCTAssertEqual(PlacesStore(fileURL: file).snapshot.workId, "legacy-home")
    }

    func testInvalidInputsDoNotChangeCommittedFileOrSnapshot() throws {
        let store = PlacesStore(fileURL: file)
        let original = try store.savePlace(name: "Casa", point: point)
        let committed = try Data(contentsOf: file)
        XCTAssertThrowsError(try store.savePlace(name: " ", point: point))
        XCTAssertThrowsError(try store.savePlace(name: "Outro", point: GeoPoint(latitude: .nan, longitude: 0)))
        XCTAssertThrowsError(try store.savePlace(name: "Outro", point: point, id: "missing"))
        XCTAssertThrowsError(try store.setHome(id: "missing"))
        XCTAssertThrowsError(try store.setWork(id: "missing"))
        XCTAssertEqual(store.snapshot.savedPlaces, [original])
        XCTAssertEqual(try Data(contentsOf: file), committed)
    }

    func testFailedFilesystemWriteDoesNotPublishUncommittedState() throws {
        let blockedParent = directory.appendingPathComponent("blocked")
        let store = PlacesStore(fileURL: blockedParent.appendingPathComponent("places.json"))
        try Data("file instead of directory".utf8).write(to: blockedParent)
        XCTAssertThrowsError(try store.savePlace(name: "Casa", point: point))
        XCTAssertTrue(store.snapshot.savedPlaces.isEmpty)
        XCTAssertEqual(try String(contentsOf: blockedParent, encoding: .utf8), "file instead of directory")
    }
}

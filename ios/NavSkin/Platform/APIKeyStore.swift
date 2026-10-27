import Foundation
import Security

/// A device-local generic password. Access after first unlock permits active locked-screen reroutes.
final class APIKeyStore {
    private let service: String
    private let account: String
    // Preserve the original Keychain service so existing personal keys remain available.
    init(service: String = "br.com.gamemap.goiania.ios.geoapify", account: String = "personal-api-key") {
        self.service = service; self.account = account
    }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service, kSecAttrAccount as String: account,
        kSecAttrSynchronizable as String: false] }
    func read() throws -> String {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return "" }
        guard status == errSecSuccess, let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
            throw KeyError.unavailable
        }
        return value
    }
    func save(_ value: String) throws {
        let key = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if key.isEmpty { try remove(); return }
        let values: [String: Any] = [kSecValueData as String: Data(key.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, values as CFDictionary)
        if status == errSecItemNotFound {
            let insert = query.merging(values) { _, right in right }
            guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw KeyError.unavailable }
        } else if status != errSecSuccess { throw KeyError.unavailable }
    }
    func remove() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeyError.unavailable }
    }
    enum KeyError: LocalizedError {
        case unavailable
        var errorDescription: String? { "Não foi possível acessar a chave no Keychain deste aparelho. Desbloqueie o iPhone e tente novamente." }
    }
}

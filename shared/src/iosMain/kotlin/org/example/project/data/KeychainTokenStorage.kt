package org.example.project.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Refresh token storage backed by the iOS Keychain.
 *
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` is the deliberate choice: the token
 * stays readable for background refreshes after the first unlock, and `ThisDeviceOnly`
 * keeps it out of iCloud Keychain and encrypted backups, so restoring a backup onto a
 * second phone does not carry a live session with it.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainTokenStorage : TokenStorage {

    override suspend fun readRefreshToken(): String? = read(ACCOUNT_REFRESH_TOKEN)

    override suspend fun writeRefreshToken(token: String) = write(ACCOUNT_REFRESH_TOKEN, token)

    override suspend fun clear() {
        delete(ACCOUNT_REFRESH_TOKEN)
    }

    override suspend fun installationId(): String =
        read(ACCOUNT_INSTALLATION_ID) ?: platform.Foundation.NSUUID().UUIDString().also {
            write(ACCOUNT_INSTALLATION_ID, it)
        }

    private fun read(account: String): String? = memScoped {
        val query = baseQuery(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null

        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    private fun write(account: String, value: String) {
        // Keychain has no upsert, so an existing item is removed before the new one is
        // added — SecItemAdd on a duplicate fails rather than replacing.
        delete(account)
        val query = baseQuery(account)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(
            query,
            kSecAttrAccessible,
            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        SecItemAdd(query, null)
        CFRelease(query)
    }

    private fun delete(account: String) {
        val query: CFDictionaryRef = baseQuery(account)
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun baseQuery(account: String): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(account as NSString))
        return query
    }

    private companion object {
        const val SERVICE = "uz.sadora.app"
        const val ACCOUNT_REFRESH_TOKEN = "refresh_token"
        const val ACCOUNT_INSTALLATION_ID = "installation_id"
    }
}

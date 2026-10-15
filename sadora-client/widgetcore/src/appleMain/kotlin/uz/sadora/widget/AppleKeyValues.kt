package uz.sadora.widget

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
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

/** The App Group's shared defaults: the snapshot and each widget's settings. */
class AppGroupKeyValues(suiteName: String) : KeyValues {
    private val defaults = NSUserDefaults(suiteName = suiteName)

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }

    override fun keys(): Set<String> =
        defaults.dictionaryRepresentation().keys.mapNotNull { it as? String }.toSet()
}

/**
 * Keychain items in an access group the app, its widget extension and its watch app all
 * hold, for the companion session's tokens. After first unlock, so a widget can refresh
 * on a locked phone; this device only, so a backup carries no live session.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainKeyValues(private val service: String, private val accessGroup: String?) : KeyValues {
    override fun get(key: String): String? = memScoped {
        val query = query(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    override fun put(key: String, value: String?) {
        val delete = query(key)
        SecItemDelete(delete)
        CFRelease(delete)
        if (value == null) return
        val add = query(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        CFDictionaryAddValue(add, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(add, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(add, null)
        CFRelease(add)
    }

    /** Not enumerable; the tokens are cleared by name. */
    override fun keys(): Set<String> = emptySet()

    private fun query(account: String): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(service as NSString))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(account as NSString))
        accessGroup?.let { CFDictionaryAddValue(query, kSecAttrAccessGroup, CFBridgingRetain(it as NSString)) }
        return query
    }
}

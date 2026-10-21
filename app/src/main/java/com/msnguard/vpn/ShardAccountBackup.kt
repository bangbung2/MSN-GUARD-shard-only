package com.msnguard.vpn

import org.json.JSONObject

/**
 * Export/import SHARD node accounts as a portable JSON file.
 *
 * This file contains proxy credentials from the node list in plain text.
 * Users should keep it private and delete shared copies when no longer needed.
 */
object ShardAccountBackup {
    const val APP_MARKER = "MSN-GUARD-SHARD-ACCOUNTS"
    const val FORMAT = 1

    fun export(context: android.content.Context): String {
        val raw = ShardSubscription.cachedText(context)
        require(raw.isNotBlank()) { "No cached SHARD accounts to export" }
        val count = ShardConfigs.parse(raw).size
        require(count > 0) { "No valid SHARD accounts to export" }
        return JSONObject()
            .put("app", APP_MARKER)
            .put("format", FORMAT)
            .put("createdAt", System.currentTimeMillis())
            .put("count", count)
            .put("configs", raw)
            .toString(2)
    }

    fun importAccounts(context: android.content.Context, json: String): Int {
        require(json.length <= 5_500_000) { "The JSON file is too large" }
        val root = JSONObject(json)
        require(root.optString("app") == APP_MARKER) { "This is not an MSN-GUARD SHARD account file" }
        require(root.optInt("format", -1) == FORMAT) { "Unsupported account backup format" }
        val configs = root.optString("configs", "")
        return ShardSubscription.importCustomText(context, configs)
    }

    fun suggestedFileName(): String = "msn-guard-shard-accounts.json"
}

package com.ben.inly.data.local.prefs
object SyncConstants {
    // Sorting
    const val KEY_SORT_TYPE = "sort_type"
    const val KEY_SORT_ORDER = "sort_order"
    // Desktop State
    const val KEY_LAST_OPENED_STATE = "last_opened_desktop_state"
    // Calendar
    const val KEY_CALENDAR_VIEW_MODE = "calendar_view_mode"
    const val DEFAULT_CALENDAR_VIEW_MODE = "DAY"
    // Appearance
    const val KEY_FONT_SIZE_PREFERENCE = "font_size_preference"
    const val DEFAULT_FONT_SIZE_PREFERENCE = "DEFAULT"
    const val KEY_FONT_STYLE_PREFERENCE = "font_style_preference"
    const val DEFAULT_FONT_STYLE_PREFERENCE = "POPPINS"
    const val KEY_SUBNOTE_OPEN_MODE = "subnote_open_mode"
    const val DEFAULT_SUBNOTE_OPEN_MODE = "SIDE_PANEL"
    // Sync Keys
    const val KEY_SYNC_TIMESTAMP = "last_sync_timestamp"
    const val KEY_SELF_HOST_SYNC_TIMESTAMP = "self_host_last_sync_timestamp"
    const val KEY_SELF_HOST_SUPPORTS_ETAGS = "self_host_supports_etags"
    const val KEY_SELF_HOST_MANIFEST_ETAG = "self_host_manifest_etag"
    const val KEY_SYNC_AUTH_TOKEN = "sync_auth_token"
    const val KEY_SYNC_IP_ADDRESS = "sync_ip_address"
    const val KEY_SYNC_PORT = "sync_port"
    const val KEY_SYNC_ENCRYPTION_KEY = "sync_encryption_key"
    // Defaults
    const val DEFAULT_PORT = 8080
    const val DEFAULT_SORT_TYPE = "LAST_EDITED"
    const val DEFAULT_SORT_ORDER = "DESCENDING"
    // API Routes
    const val ROUTE_FETCH = "/sync/fetch"
    const val ROUTE_PUSH = "/sync/push"
    // HMAC Auth
    const val HEADER_SYNC_TIMESTAMP = "X-Sync-Timestamp"
    const val HEADER_SYNC_SIGNATURE = "X-Sync-Signature"
    const val MAX_REQUEST_AGE_MS = 30_000L
}
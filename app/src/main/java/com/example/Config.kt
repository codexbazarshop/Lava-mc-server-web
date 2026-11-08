package com.example

/**
 * ============================================================================
 * CENTRAL CONFIGURATION FILE FOR HTML-TO-ANDROID WRAPPER
 * ============================================================================
 * Customize this file for any HTML/CSS/JavaScript project.
 * All settings have clear inline comments explaining their behavior.
 * ============================================================================
 */
object Config {

    // ------------------------------------------------------------------------
    // GENERAL APP SETTINGS
    // ------------------------------------------------------------------------

    /**
     * Application display name.
     */
    const val APP_NAME = "Lavamc"

    /**
     * The main entry HTML file located inside `app/src/main/assets/`.
     * Example: "index.html", "home.html", or "subfolder/index.html"
     */
    const val START_PAGE = "index.html"

    /**
     * Set to true to enable debug logs in Logcat (tag: "HtmlAppWrapper").
     * Set to false before publishing to production.
     */
    const val DEBUG_MODE = true

    // ------------------------------------------------------------------------
    // WEBVIEW ENGINE SETTINGS
    // ------------------------------------------------------------------------

    /**
     * Enable or disable JavaScript execution inside the WebView.
     * Default: true (required by modern web applications).
     */
    const val ENABLE_JAVASCRIPT = true

    /**
     * Enable HTML5 DOM Storage (localStorage & sessionStorage).
     * Required for web data persistence, token storage, and client-side caching.
     */
    const val ENABLE_DOM_STORAGE = true

    /**
     * Enable HTML5 Database Storage (IndexedDB / WebSQL).
     */
    const val ENABLE_DATABASE_STORAGE = true

    /**
     * Enable Swipe-to-Refresh (Pull-to-Refresh).
     * Set to true to allow pulling down to reload the current webpage.
     * Set to false for single-page apps (SPAs) or apps with custom scroll logic.
     */
    const val ENABLE_PULL_TO_REFRESH = true

    /**
     * Enable thin Chrome-style horizontal progress bar at the top during page loads.
     */
    const val ENABLE_PROGRESS_BAR = true

    /**
     * Enable hardware acceleration for high-performance rendering, animations, and video.
     */
    const val ENABLE_HARDWARE_ACCELERATION = true

    /**
     * Zoom controls: whether to enable pinch-to-zoom in WebView.
     */
    const val ENABLE_ZOOM_CONTROLS = false

    // ------------------------------------------------------------------------
    // EXTERNAL URL HANDLING
    // ------------------------------------------------------------------------

    /**
     * If true, external web URLs (http/https outside local assets) are opened
     * in the device's default web browser instead of inside this WebView.
     * If false, external links load directly inside the app's WebView.
     */
    const val OPEN_EXTERNAL_URLS_IN_BROWSER = true

    /**
     * Schemes that are always handed off to external Android native apps
     * (e.g. WhatsApp, phone dialer, email client, SMS app, map app).
     */
    val SUPPORTED_SYSTEM_SCHEMES = listOf(
        "tel:", "mailto:", "sms:", "smsto:", "whatsapp:", "intent:", "market:"
    )

    // ------------------------------------------------------------------------
    // HARDWARE & RUNTIME PERMISSIONS
    // ------------------------------------------------------------------------

    /**
     * Enable HTML file chooser (<input type="file">) with Camera & Gallery support.
     */
    const val ENABLE_FILE_CHOOSER = true

    /**
     * Enable HTML5 Geolocation (navigator.geolocation).
     * When true, the app asks user for location permission when requested by web page.
     */
    const val ENABLE_GEOLOCATION = true

    /**
     * Enable Camera capture in file uploads.
     */
    const val ENABLE_CAMERA_UPLOAD = true

    // ------------------------------------------------------------------------
    // SMS INTEGRATION (AUTOMATED PAYMENT PARSING)
    // ------------------------------------------------------------------------

    /**
     * Enable automated SMS listener for payment verification (bKash, Nagad, etc.).
     * When an SMS arrives, it automatically invokes `window.onSms(smsText)` in JavaScript.
     */
    const val ENABLE_SMS_INTEGRATION = true

    /**
     * Whether to show permission prompt for SMS on app first launch.
     * If false, users can still manually paste payment SMS into the web app.
     */
    const val REQUEST_SMS_PERMISSION_ON_START = true

    /**
     * Filters for incoming SMS: only SMS containing one of these keywords will be
     * forwarded to the WebView. If empty, all incoming SMS are forwarded.
     */
    val SMS_FILTER_KEYWORDS = listOf(
        "trxid", "txnid", "bkash", "nagad", "received", "money received", "টাকা", "পেমেন্ট"
    )

    // ------------------------------------------------------------------------
    // GOOGLE ADMOB MONETIZATION
    // ------------------------------------------------------------------------

    /**
     * Replace with your real AdMob Banner Ad Unit ID.
     * Leave empty ("") to completely disable banner ads.
     * Google AdMob Test Banner ID: "ca-app-pub-3940256099942544/6300978111"
     */
    const val ADMOB_BANNER_ID = ""

    /**
     * Replace with your real AdMob Interstitial Ad Unit ID.
     * Leave empty ("") to completely disable interstitial ads.
     * Google AdMob Test Interstitial ID: "ca-app-pub-3940256099942544/1033173712"
     */
    const val ADMOB_INTERSTITIAL_ID = ""

    /**
     * Minimum interval between showing interstitial ads (in seconds).
     * Prevents spamming users with ads on frequent page transitions.
     */
    const val INTERSTITIAL_INTERVAL_SECONDS = 60

    /**
     * Number of internal page navigations before attempting to show an interstitial ad.
     */
    const val INTERSTITIAL_PAGE_THRESHOLD = 5
}

package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"

    // Views
    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineBanner: TextView
    private lateinit var admobBannerContainer: FrameLayout

    // Helpers
    private lateinit var assetLoader: WebViewAssetLoader
    private var admob: Admob? = null
    private var isOnline: Boolean = true

    // File Chooser & Camera Variables
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Geolocation Callback Cache
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null

    // Permission Launchers
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var smsPermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from Splash Theme back to Main Application Theme
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initPermissionLaunchers()
        initNetworkMonitoring()
        initAdmob()
        initWebView()
        initBackNavigation()
        initSmsListener()

        // Request SMS permission on app launch if enabled in Config
        if (Config.ENABLE_SMS_INTEGRATION && Config.REQUEST_SMS_PERMISSION_ON_START) {
            checkAndRequestSmsPermissions()
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        progressBar = findViewById(R.id.progress_bar)
        offlineBanner = findViewById(R.id.offline_banner)
        admobBannerContainer = findViewById(R.id.admob_banner_container)

        // Setup Swipe to Refresh
        swipeRefreshLayout.isEnabled = Config.ENABLE_PULL_TO_REFRESH
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary),
            ContextCompat.getColor(this, R.color.accent)
        )
        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun initPermissionLaunchers() {
        // File Chooser Result Launcher
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (filePathCallback == null) return@registerForActivityResult

            var results: Array<Uri>? = null
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null && (data.data != null || data.clipData != null)) {
                    // Selected from gallery / file manager
                    val singleUri = data.data
                    val clipData = data.clipData
                    results = when {
                        singleUri != null -> arrayOf(singleUri)
                        clipData != null -> {
                            val count = clipData.itemCount
                            Array(count) { i -> clipData.getItemAt(i).uri }
                        }
                        else -> null
                    }
                } else if (cameraImageUri != null) {
                    // Captured from camera
                    results = arrayOf(cameraImageUri!!)
                }
            }

            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

        // Camera Permission Launcher
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openFileChooserInternal()
            } else {
                Toast.makeText(this, "ক্যামেরা পারমিশন দেওয়া হয়নি", Toast.LENGTH_SHORT).show()
                openFileChooserWithoutCamera()
            }
        }

        // Geolocation Permission Launcher
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val granted = fineGranted || coarseGranted

            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

        // SMS Permission Launcher
        smsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
            val readGranted = permissions[Manifest.permission.READ_SMS] == true

            if (receiveGranted || readGranted) {
                Toast.makeText(this, "অটো পেমেন্ট এসএমএস ডিটেকশন সক্রিয় করা হয়েছে", Toast.LENGTH_SHORT).show()
                readSmsFromInbox()
            } else {
                Toast.makeText(this, "এসএমএস অনুমতি দেওয়া হয়নি। আপনি ম্যানুয়ালি মেসেজ পেস্ট করতে পারেন।", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Scans the device SMS inbox for existing bKash and Nagad payment messages.
     * Non-payment SMS messages are completely ignored.
     */
    fun readSmsFromInbox() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestSmsPermissions()
            return
        }

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            val cursor = contentResolver.query(uri, projection, null, null, "date DESC LIMIT 50")
            var count = 0
            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val addressIdx = it.getColumnIndex("address")
                while (it.moveToNext()) {
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) else ""
                    val address = if (addressIdx != -1) it.getString(addressIdx) else ""
                    val isPaymentMsg = address.contains("bkash", true) ||
                            address.contains("nagad", true) ||
                            body.contains("TrxID", true) ||
                            body.contains("TxnID", true) ||
                            body.contains("Money Received", true) ||
                            body.contains("You have received", true) ||
                            body.contains("টাকা পেয়েছেন", true)

                    if (isPaymentMsg && body.isNotBlank()) {
                        dispatchSmsToWebView(body)
                        count++
                    }
                }
            }
            if (count > 0) {
                Toast.makeText(this, "$count টি বিকাশ/নগদ পেমেন্ট এসএমএস ইনবক্স থেকে পাওয়া গেছে", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading inbox SMS: ${e.message}")
        }
    }

    private fun checkAndRequestSmsPermissions() {
        val hasReceive = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        if (!hasReceive || !hasRead) {
            val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            smsPermissionLauncher.launch(perms.toTypedArray())
        } else {
            readSmsFromInbox()
        }
    }

    private fun initSmsListener() {
        // Register listener for SMS received via SmsReceiver
        SmsReceiver.listener = { sender, message ->
            runOnUiThread {
                dispatchSmsToWebView(message)
            }
        }
    }

    /**
     * Injects the received SMS body into the WebView by invoking `window.onSms(...)`.
     */
    fun dispatchSmsToWebView(smsText: String) {
        if (!Config.ENABLE_JAVASCRIPT) return

        try {
            // Using JSONObject.quote safely escapes strings for JavaScript execution
            val escapedSms = JSONObject.quote(smsText)
            val script = "if (typeof window.onSms === 'function') { window.onSms($escapedSms); }"
            webView.evaluateJavascript(script) { result ->
                if (Config.DEBUG_MODE) {
                    Log.d(tag, "Dispatched SMS to WebView onSms: $result")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch SMS to WebView: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        // Setup secure WebViewAssetLoader
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val settings: WebSettings = webView.settings

        // JavaScript
        settings.javaScriptEnabled = Config.ENABLE_JAVASCRIPT
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Storage & Cache
        settings.domStorageEnabled = Config.ENABLE_DOM_STORAGE
        settings.databaseEnabled = Config.ENABLE_DATABASE_STORAGE
        settings.cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK

        // Viewport & Scaling
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = Config.ENABLE_ZOOM_CONTROLS
        settings.displayZoomControls = false

        // Security & File Access
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Geolocation
        settings.setGeolocationEnabled(Config.ENABLE_GEOLOCATION)

        // Hardware acceleration layer
        if (Config.ENABLE_HARDWARE_ACCELERATION) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        // Setup Clients
        webView.webViewClient = createWebViewClient()
        webView.webChromeClient = createWebChromeClient()
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Load the configured start page
        val initialUrl = "https://appassets.androidplatform.net/assets/${Config.START_PAGE}"
        webView.loadUrl(initialUrl)
    }

    /**
     * JavaScript Interface allowing HTML to interact with native Android functions.
     */
    inner class WebAppInterface(private val activity: MainActivity) {
        @android.webkit.JavascriptInterface
        fun scanInbox() {
            activity.runOnUiThread {
                activity.readSmsFromInbox()
            }
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.let { uri ->
                    val response = assetLoader.shouldInterceptRequest(uri)
                    if (response != null) return response
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // 1. Check for system schemes (tel:, mailto:, sms:, whatsapp:, etc.)
                for (scheme in Config.SUPPORTED_SYSTEM_SCHEMES) {
                    if (url.startsWith(scheme, ignoreCase = true)) {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                            true
                        } catch (e: Exception) {
                            Log.e(tag, "No app available to handle $url: ${e.message}")
                            true
                        }
                    }
                }

                // 2. Check for local app assets domain
                if (url.contains("appassets.androidplatform.net")) {
                    return false // Let WebView load it
                }

                // 3. External HTTP/HTTPS links
                if (Config.OPEN_EXTERNAL_URLS_IN_BROWSER) {
                    return try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 5
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.GONE
                }
                admob?.onPageNavigation(this@MainActivity, isOnline)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    swipeRefreshLayout.isRefreshing = false
                    if (Config.ENABLE_PROGRESS_BAR) {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {

            // Progress Bar Handler
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (Config.ENABLE_PROGRESS_BAR) {
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.progress = 100
                        progressBar.visibility = View.GONE
                    }
                }
            }

            // HTML File Chooser (<input type="file">)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (!Config.ENABLE_FILE_CHOOSER) return false

                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                // Check camera permission if camera is allowed
                if (Config.ENABLE_CAMERA_UPLOAD && ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    openFileChooserInternal()
                }

                return true
            }

            // HTML5 Geolocation Prompt
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (!Config.ENABLE_GEOLOCATION) {
                    callback?.invoke(origin, false, false)
                    return
                }

                val hasFine = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val hasCoarse = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    callback?.invoke(origin, true, false)
                } else {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            // JavaScript Dialogs: Alert
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // JavaScript Dialogs: Confirm
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // JavaScript Dialogs: Prompt
            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity).apply {
                    setText(defaultValue)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result?.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }
    }

    private fun openFileChooserInternal() {
        val intentList = mutableListOf<Intent>()

        // 1. Camera Intent (if permitted and available)
        if (Config.ENABLE_CAMERA_UPLOAD && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(tag, "Error creating camera image file", ex)
                    null
                }
                photoFile?.let {
                    cameraImageUri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    intentList.add(takePictureIntent)
                }
            }
        }

        // 2. Document/File Intent
        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            putExtra(Intent.EXTRA_TITLE, "ফাইল বা ছবি নির্বাচন করুন")
            if (intentList.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toTypedArray())
            }
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun openFileChooserWithoutCamera() {
        cameraImageUri = null
        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooserIntent = Intent.createChooser(contentSelectionIntent, "ফাইল নির্বাচন করুন")
        fileChooserLauncher.launch(chooserIntent)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "IMG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun initNetworkMonitoring() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initial connection check
        val activeNet = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNet)
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        offlineBanner.visibility = if (isOnline) View.GONE else View.VISIBLE

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    isOnline = true
                    offlineBanner.visibility = View.GONE
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webView.evaluateJavascript("if (typeof window.setOnlineStatus === 'function') { window.setOnlineStatus(true); }", null)
                    admob?.onNetworkAvailable(admobBannerContainer)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    isOnline = false
                    offlineBanner.visibility = View.VISIBLE
                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    webView.evaluateJavascript("if (typeof window.setOnlineStatus === 'function') { window.setOnlineStatus(false); }", null)
                }
            }
        })
    }

    private fun initAdmob() {
        val hasBanner = Config.ADMOB_BANNER_ID.isNotBlank()
        val hasInterstitial = Config.ADMOB_INTERSTITIAL_ID.isNotBlank()

        if (hasBanner || hasInterstitial) {
            admob = Admob(this)
            admob?.loadBanner(admobBannerContainer, isOnline)
        } else {
            admobBannerContainer.visibility = View.GONE
        }
    }

    private fun initBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        admob?.resume()
    }

    override fun onPause() {
        webView.onPause()
        admob?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        SmsReceiver.listener = null
        admob?.destroy()
        admob = null
        webView.destroy()
        super.onDestroy()
    }
}

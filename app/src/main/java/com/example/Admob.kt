package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMob Management Helper.
 * Handles initialization, banner ads, and interstitial ads safely.
 * If Ad Unit IDs are empty or the device is offline, ads are gracefully disabled.
 */
class Admob(private val context: Context) {

    companion object {
        private const val TAG = "AdmobHelper"
    }

    private var isInitialized = false
    private var adView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime: Long = 0
    private var navigationCount: Int = 0

    init {
        initIfConfigured()
    }

    /**
     * Initializes Google Mobile Ads SDK only if at least one ad unit ID is provided.
     */
    private fun initIfConfigured() {
        val hasBanner = Config.ADMOB_BANNER_ID.isNotBlank()
        val hasInterstitial = Config.ADMOB_INTERSTITIAL_ID.isNotBlank()

        if (!hasBanner && !hasInterstitial) {
            if (Config.DEBUG_MODE) {
                Log.d(TAG, "AdMob IDs are empty. Advertisements are completely disabled.")
            }
            return
        }

        try {
            MobileAds.initialize(context) { status ->
                isInitialized = true
                if (Config.DEBUG_MODE) {
                    Log.d(TAG, "AdMob SDK Initialized successfully: $status")
                }
                if (hasInterstitial) {
                    loadInterstitial()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob SDK initialization failed safely: ${e.message}")
        }
    }

    /**
     * Loads a fixed bottom banner ad inside the provided container.
     */
    fun loadBanner(container: ViewGroup, isOnline: Boolean) {
        val bannerId = Config.ADMOB_BANNER_ID.trim()
        if (bannerId.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        if (!isOnline) {
            if (Config.DEBUG_MODE) {
                Log.d(TAG, "Device is offline. Banner ad postponed.")
            }
            container.visibility = View.GONE
            return
        }

        try {
            if (adView == null) {
                adView = AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = bannerId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            container.visibility = View.VISIBLE
                            if (Config.DEBUG_MODE) {
                                Log.d(TAG, "AdMob banner loaded and visible.")
                            }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)
                            container.visibility = View.GONE
                            if (Config.DEBUG_MODE) {
                                Log.w(TAG, "AdMob banner failed to load: ${error.message} (Code: ${error.code})")
                            }
                        }
                    }
                }
                container.removeAllViews()
                container.addView(adView)
            }

            val adRequest = AdRequest.Builder().build()
            adView?.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load banner safely: ${e.message}")
            container.visibility = View.GONE
        }
    }

    /**
     * Preloads an interstitial ad for future display.
     */
    fun loadInterstitial() {
        val interstitialId = Config.ADMOB_INTERSTITIAL_ID.trim()
        if (interstitialId.isEmpty() || isInterstitialLoading || interstitialAd != null) {
            return
        }

        isInterstitialLoading = true
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                interstitialId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                        if (Config.DEBUG_MODE) {
                            Log.d(TAG, "AdMob Interstitial loaded and ready.")
                        }

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                interstitialAd = null
                                lastInterstitialShownTime = System.currentTimeMillis()
                                loadInterstitial() // Preload next one
                            }

                            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                                interstitialAd = null
                                loadInterstitial()
                            }
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isInterstitialLoading = false
                        if (Config.DEBUG_MODE) {
                            Log.w(TAG, "AdMob Interstitial failed to load: ${error.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.e(TAG, "Failed to request interstitial safely: ${e.message}")
        }
    }

    /**
     * Called when a page transition or user action occurs.
     * Evaluates navigation threshold and time interval before showing an interstitial.
     */
    fun onPageNavigation(activity: Activity, isOnline: Boolean) {
        if (Config.ADMOB_INTERSTITIAL_ID.isBlank() || !isOnline) return

        navigationCount++
        val intervalMs = Config.INTERSTITIAL_INTERVAL_SECONDS * 1000L
        val now = System.currentTimeMillis()

        if (navigationCount >= Config.INTERSTITIAL_PAGE_THRESHOLD &&
            (now - lastInterstitialShownTime) >= intervalMs
        ) {
            showInterstitial(activity)
            navigationCount = 0
        }
    }

    /**
     * Displays the interstitial ad if ready.
     */
    fun showInterstitial(activity: Activity): Boolean {
        interstitialAd?.let { ad ->
            ad.show(activity)
            lastInterstitialShownTime = System.currentTimeMillis()
            return true
        }
        // If not ready, attempt reload
        loadInterstitial()
        return false
    }

    /**
     * Called when internet connection is restored to reload banner or interstitial.
     */
    fun onNetworkAvailable(container: ViewGroup?) {
        container?.let { loadBanner(it, true) }
        if (interstitialAd == null) {
            loadInterstitial()
        }
    }

    fun pause() {
        adView?.pause()
    }

    fun resume() {
        adView?.resume()
    }

    fun destroy() {
        adView?.destroy()
        adView = null
        interstitialAd = null
    }
}

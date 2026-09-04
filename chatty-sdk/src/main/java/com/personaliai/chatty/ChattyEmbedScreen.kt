package com.personaliai.chatty

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Defaults to the production widget site's own host (not the `api.` API host). */
const val CHATTY_DEFAULT_EMBED_BASE_URL = "https://chatty.personaliai.com"

/**
 * The exact bridge protocol EmbedClient.tsx speaks to `window.parent` (read
 * from that file, not guessed). Outbound (page -> us): chatty:ready,
 * chatty:message, chatty:close, chatty-request-notification,
 * chatty-trigger-notification. Inbound (us -> page): chatty-notification-status,
 * chatty-fullscreen.
 */
private const val BRIDGE_SHIM_JS = """
(function() {
  if (window.__chattyBridgeInstalled) { return; }
  window.__chattyBridgeInstalled = true;
  // MessageEvent's constructor only accepts a Window or MessagePort for
  // `source` (not a generic EventTarget, and definitely not a plain object
  // literal) — a MessagePort from a throwaway MessageChannel is the only
  // one of those we can actually construct standalone. Its native
  // postMessage is shadowed with our own override (own-property lookup
  // beats the prototype's), so the port itself is never really used to
  // send anything, only to satisfy the type check and as a stable
  // identity for the page's own `e.source !== window.parent` filter.
  var fakeParent = new MessageChannel().port1;
  fakeParent.postMessage = function(data) {
      try { window.ChattyAndroidBridge.postMessage(JSON.stringify(data)); } catch (e) { console.error('[ChattyBridge] postMessage failed', e); }
  };
  try {
    Object.defineProperty(window, 'parent', { get: function() { return fakeParent; }, configurable: true });
  } catch (e) { console.error('[ChattyBridge] window.parent override failed', e); }
  window.__chattyDeliver = function(json) {
    try {
      var data = JSON.parse(json);
      window.dispatchEvent(new MessageEvent('message', { data: data, source: fakeParent }));
    } catch (e) { console.error('[ChattyBridge] deliver failed', e); }
  };
})();
"""

/**
 * Loads the bot's own web widget page (the exact code chatty.personaliai.com
 * serves at /embed/{botId}) in a WebView — guaranteed pixel-for-pixel and
 * feature-for-feature parity with the web widget, since it IS the web widget,
 * not a native reimplementation of it. See ChattyChatScreen for the
 * native-components alternative (more native feel, manually kept in parity).
 *
 * @param onRequestNotificationPermission Called when the visitor taps the in-chat
 *   "enable notifications" control. This SDK never requests POST_NOTIFICATIONS
 *   itself — see ChattyChatScreen's own notification-bell doc comment for the
 *   same "reflect, never request" contract. Request the permission yourself
 *   here; the granted state is then reflected back into the page automatically.
 * @param onMicPermissionNeeded Called when the visitor taps the composer's mic
 *   button and RECORD_AUDIO isn't granted yet — that specific tap still fails
 *   (there's no way to pause the in-flight getUserMedia() call for an async
 *   permission dialog), but requesting it here means the *next* tap works.
 *   Request contextually — right when the visitor actually reaches for the
 *   mic — instead of upfront at app launch, which is what this callback is
 *   for; see [onRequestNotificationPermission] for the same never-request
 *   contract.
 * @param onLocationPermissionNeeded Same pattern as [onMicPermissionNeeded],
 *   for the composer's "share location" attach-menu option and
 *   ACCESS_COARSE_LOCATION.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChattyEmbedScreen(
    botId: String,
    modifier: Modifier = Modifier,
    baseUrl: String = CHATTY_DEFAULT_EMBED_BASE_URL,
    onReady: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onRequestNotificationPermission: ((botName: String?) -> Unit)? = null,
    onMicPermissionNeeded: (() -> Unit)? = null,
    onLocationPermissionNeeded: (() -> Unit)? = null,
    /** Set false to hide the WebView (e.g. ChattyLauncher keeps this composed
     * but invisible while closed, to avoid reloading on every reopen) — sets
     * the underlying View's visibility explicitly rather than relying only on
     * layout size, since a hardware-accelerated WebView surface doesn't
     * always respond to a collapsed Compose layout size on its own. */
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val embedUrl = remember(baseUrl, botId) { "${baseUrl.trimEnd('/')}/embed/$botId" }
    val embedHost = remember(embedUrl) { Uri.parse(embedUrl).host }

    var pageReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var isOnline by remember { mutableStateOf(true) }
    var notifGranted by remember { mutableStateOf(hasPostNotificationsPermission(context)) }
    var theme by remember { mutableStateOf<ChattyTheme?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // The composer's mic button uses real navigator.mediaDevices.getUserMedia()
    // (EmbedClient.tsx), and its attach button uses a real <input type="file">
    // — WebView blocks both by default without these two handlers. Mic
    // access is reflect-only, matching the SDK's "never request permissions
    // itself" contract; the file chooser has no such contract (it's not a
    // dangerous runtime permission) so it's just wired up outright.
    var filePathCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val data = result.data
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            when {
                data?.clipData != null -> (0 until data.clipData!!.itemCount)
                    .map { data.clipData!!.getItemAt(it).uri }
                    .toTypedArray()
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback.onReceiveValue(uris)
    }

    LaunchedEffect(botId) {
        try {
            theme = ChattyClient(botId).getTheme()
        } catch (_: Exception) {
            // Loading/offline screens fall back to the "minimal" design — the
            // embedded page itself owns all real theming regardless.
        }
    }
    val designId = chattyNormalizeWidgetStyle(theme?.widgetStyle)
    val t = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")

    // Connectivity — proper offline handling, not a blank/broken WebView.
    DisposableEffect(Unit) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun currentlyOnline(): Boolean {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        isOnline = currentlyOnline()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = currentlyOnline() }
            override fun onLost(network: Network) { isOnline = currentlyOnline() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                isOnline = currentlyOnline()
            }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // Re-check POST_NOTIFICATIONS on resume, same pattern as ChattyChatScreen's
    // mic/notification/location gating — never requested by this SDK itself.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifGranted = hasPostNotificationsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pageReady, notifGranted) {
        if (pageReady) {
            deliverToPage(webViewRef, JSONObject().apply {
                put("type", "chatty-notification-status")
                put("granted", notifGranted)
            })
        }
    }

    fun retry() {
        loadError = false
        pageReady = false
        reloadKey += 1
    }

    if (!isOnline) {
        OfflineOrErrorState(
            title = "You're offline",
            body = "Check your connection and try again.",
            bg = t.containerBg,
            fg = t.headerText,
            onRetry = ::retry,
        )
        return
    }
    if (loadError) {
        OfflineOrErrorState(title = "Couldn't load chat", body = null, bg = t.containerBg, fg = t.headerText, onRetry = ::retry)
        return
    }

    Box(modifier = modifier.fillMaxSize().background(t.containerBg).statusBarsPadding().imePadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setGeolocationEnabled(true)
                    // Some OEMs (MIUI included) apply the system "font size"
                    // accessibility setting to WebView content by default,
                    // which can throw off spacing/padding that assumes 100%
                    // text scale — pin it so the page renders exactly as
                    // designed regardless of the device's display settings.
                    settings.textZoom = 100
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false

                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    addJavascriptInterface(
                        object {
                            // @JavascriptInterface methods run on a WebView background thread,
                            // never the main thread — Compose state (pageReady etc.) must only
                            // be touched from the main thread, hence the post().
                            @JavascriptInterface
                            fun postMessage(json: String) {
                                val msg = try { JSONObject(json) } catch (_: Exception) { return }
                                mainHandler.post {
                                    when (msg.optString("type")) {
                                        "chatty:ready" -> { pageReady = true; onReady?.invoke() }
                                        "chatty:message" -> onMessage?.invoke()
                                        "chatty:close" -> onClose?.invoke()
                                        "chatty-request-notification" ->
                                            onRequestNotificationPermission?.invoke(
                                                if (msg.isNull("botName")) null else msg.optString("botName"),
                                            )
                                        // chatty-trigger-notification: this SDK doesn't bundle a
                                        // local-notification dependency (matches ChattyChatScreen's
                                        // own scope) — watch onMessage and post your own instead.
                                    }
                                }
                            }
                        },
                        "ChattyAndroidBridge",
                    )

                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(this, BRIDGE_SHIM_JS, setOf("*"))
                    }

                    // Grants geolocation to the page only when this app already holds
                    // ACCESS_COARSE_LOCATION — never requests it, same "reflect, never
                    // request" contract as ChattyChatScreen's location-share button.
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        // Forwards the page's own console.log/warn/error to logcat
                        // (tag "ChattyWebView") — otherwise JS-side failures (e.g. a
                        // getUserMedia rejection) are invisible outside Chrome DevTools
                        // remote inspection.
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                            message ?: return false
                            android.util.Log.d(
                                "ChattyWebView",
                                "[${message.messageLevel()}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})",
                            )
                            return true
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: android.webkit.GeolocationPermissions.Callback?,
                        ) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!granted) onLocationPermissionNeeded?.invoke()
                            callback?.invoke(origin, granted, false)
                        }

                        // The composer's mic button calls real getUserMedia({audio:true})
                        // (EmbedClient.tsx) — WebView blocks it outright without this
                        // override. Reflects RECORD_AUDIO's already-granted state only;
                        // never calls ActivityCompat.requestPermissions itself, same
                        // "reflect, never request" contract as ChattyChatScreen's own
                        // mic button.
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            request ?: return
                            val wantsAudio = request.resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                            val micGranted = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (wantsAudio && micGranted) {
                                request.grant(arrayOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            } else {
                                if (wantsAudio) onMicPermissionNeeded?.invoke()
                                request.deny()
                            }
                        }

                        // The attach button's real <input type="file"> (EmbedClient.tsx)
                        // needs a native file-chooser Activity launched on its behalf —
                        // WebView cannot show one itself. Not a dangerous runtime
                        // permission, so this is just wired up outright (no gating).
                        override fun onShowFileChooser(
                            webView: WebView?,
                            callback: android.webkit.ValueCallback<Array<Uri>>?,
                            params: android.webkit.WebChromeClient.FileChooserParams?,
                        ): Boolean {
                            val intent = params?.createIntent() ?: return false
                            filePathCallback = callback
                            return try {
                                fileChooserLauncher.launch(intent)
                                true
                            } catch (e: Exception) {
                                filePathCallback = null
                                false
                            }
                        }

                        // The page's own window.alert() calls (e.g. "Browser push
                        // notifications are not supported on this browser" — real
                        // EmbedClient.tsx code, fired because WebView has no
                        // window.Notification, unlike a real browser tab) render as a
                        // jarring native AlertDialog otherwise, breaking the native
                        // feel this screen is meant to have. Every alert this app
                        // actually needs to surface (chatty-request-notification)
                        // already comes through the bridge separately, so suppressing
                        // the raw browser-style dialogs loses nothing.
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: android.webkit.JsResult?,
                        ): Boolean {
                            result?.cancel()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                // Fallback for old WebView versions — races the page's own
                                // scripts, but addDocumentStartJavaScript covers all modern ones.
                                view?.evaluateJavascript(BRIDGE_SHIM_JS, null)
                            }
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) loadError = true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            if (uri.host == embedHost) return false
                            return when (uri.scheme) {
                                "mailto", "tel", "sms" -> {
                                    openExternally(context, uri); true
                                }
                                "http", "https" -> {
                                    openExternally(context, uri); true
                                }
                                else -> false
                            }
                        }
                    }
                    tag = reloadKey
                    loadUrl(embedUrl)
                    webViewRef = this
                }
            },
            update = { view ->
                if (view.tag != reloadKey) {
                    view.tag = reloadKey
                    view.loadUrl(embedUrl)
                }
                view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            },
        )
        if (!pageReady && visible) {
            Box(Modifier.fillMaxSize().background(t.containerBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.userBubbleBg)
            }
        }
    }
}

private fun hasPostNotificationsPermission(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun deliverToPage(webView: WebView?, payload: JSONObject) {
    val js = "window.__chattyDeliver && window.__chattyDeliver(${JSONObject.quote(payload.toString())});"
    android.util.Log.d("ChattyBridge", "delivering: $payload (webView null? ${webView == null})")
    webView?.evaluateJavascript(js) { result ->
        android.util.Log.d("ChattyBridge", "evaluateJavascript result: $result")
    }
}

private fun openExternally(context: android.content.Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
        // No app can handle it (e.g. no mail client installed) — silently drop,
        // matching the web widget's own best-effort target=_blank behavior.
    }
}

@Composable
private fun OfflineOrErrorState(title: String, body: String?, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = fg, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(body, color = fg.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            }
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

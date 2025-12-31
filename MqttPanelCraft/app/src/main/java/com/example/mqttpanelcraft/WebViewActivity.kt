package com.example.mqttpanelcraft

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        
        // Use "customCode" field or "broker" field as URL? 
        // Project doesn't have a distinct "url" field for WebView, but usually Broker might be reused or CustomCode?
        // User didn't specify. Assuming "broker" holds URL for WebView type or we use a hardcoded/intent extra.
        // DashboardActivity should pass URL.
        val url = intent.getStringExtra("URL") ?: "https://www.google.com"
        webView.loadUrl(url)
    }
}

package com.endiq.turtlelauncher.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentDiscordLoginBinding
import com.endiq.turtlelauncher.feature.discord.DiscordAuthManager
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions

/**
 * Discord account linking - same WebView + intercepted-redirect shape as
 * MicrosoftLoginFragment, with a PKCE code_verifier generated per session instead of a
 * client secret. See DiscordAuthManager for why this only links the account and doesn't
 * attempt to drive the linked account's live Discord presence.
 */
class DiscordLoginFragment : BaseFragment(R.layout.fragment_discord_login) {
    companion object {
        const val TAG = "DiscordLoginFragment"
    }

    private lateinit var binding: FragmentDiscordLoginBinding
    private lateinit var codeVerifier: String
    private var mBlankClient = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDiscordLoginBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        TurtleTransitions.animateView(view, true)
        binding.returnButton.setOnClickListener { forceBack() }

        binding.discordWebView.settings.javaScriptEnabled = true
        binding.discordWebView.webViewClient = RedirectInterceptClient()
        mBlankClient = false

        val pkce = DiscordAuthManager.generatePkce()
        codeVerifier = pkce.verifier
        CookieManager.getInstance().removeAllCookies {
            binding.discordWebView.clearHistory()
            binding.discordWebView.clearCache(true)
            binding.discordWebView.clearFormData()
            binding.discordWebView.loadUrl(DiscordAuthManager.buildAuthorizeUrl(pkce.challenge))
        }
    }

    override fun onStart() {
        super.onStart()
        if (mBlankClient) binding.discordWebView.webViewClient = RedirectInterceptClient()
    }

    override fun onBackPressed(): Boolean {
        if (binding.discordWebView.canGoBack()) {
            binding.discordWebView.goBack()
            return false
        }
        return super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.discordWebView.webViewClient = WebViewClient()
        mBlankClient = true
        super.onSaveInstanceState(outState)
    }

    private inner class RedirectInterceptClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val code = DiscordAuthManager.extractAuthorizationCode(url)
            if (code != null) {
                Toast.makeText(requireContext(), R.string.account_login_start, Toast.LENGTH_SHORT).show()
                val verifier = codeVerifier
                Task.runTask {
                    DiscordAuthManager.completeLogin(code, verifier)
                }.ended(TaskExecutors.getAndroidUI()) { success ->
                    if (isAdded) {
                        if (success != true) {
                            Toast.makeText(requireContext(), R.string.discord_link_failed, Toast.LENGTH_LONG).show()
                        }
                        forceBack()
                    }
                }.execute()
                return true
            }
            return super.shouldOverrideUrlLoading(view, url)
        }
    }
}

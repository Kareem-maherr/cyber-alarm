package com.cyberalarm.lock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.cyberalarm.ui.lock.LockScreen
import com.cyberalarm.ui.theme.CyberTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Block screen drawn over a locked app. Back / "exit" sends the user home, not into the app. */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var installedApps: InstalledApps

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        onBackPressedDispatcher.addCallback(this) { goHome() }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val label = installedApps.label(pkg)
        setContent {
            CyberTheme {
                LockScreen(
                    packageName = pkg,
                    appLabel = label,
                    lockManager = lockManager,
                    onExit = ::goHome,
                    onUnlocked = {
                        lifecycleScope.launch {
                            lockManager.grantPass(pkg)
                            finishAndRemoveTask()
                        }
                    },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg != null && !lockManager.isLockedNow(pkg)) finishAndRemoveTask()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, pkg: String): Intent =
            Intent(context, LockActivity::class.java).putExtra(EXTRA_PACKAGE, pkg)
    }
}

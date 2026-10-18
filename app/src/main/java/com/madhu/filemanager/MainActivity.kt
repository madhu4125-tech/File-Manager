package com.madhu.filemanager

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: FmViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.checkAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.onDone = { finish() }
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            BackHandler {
                if (!vm.onBack()) finish()
            }
            App(vm, onGrant = { requestAccess() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onDestroy() {
        vm.onDone = null
        super.onDestroy()
    }

    /** Another app shared a file (or text) to "Save to File Manager". */
    private fun handleShareIntent(i: Intent?) {
        if (i == null) return
        if (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE) return
        vm.receiveShare(extractUris(i), i.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Suppress("DEPRECATION")
    private fun extractUris(i: Intent): List<Uri> {
        val out = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND) {
            val u: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                i.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (u != null) out.add(u)
        } else {
            val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (list != null) out.addAll(list)
        }
        if (out.isEmpty()) {
            val cd = i.clipData
            if (cd != null) {
                for (k in 0 until cd.itemCount) {
                    val u = cd.getItemAt(k).uri
                    if (u != null) out.add(u)
                }
            }
        }
        return out
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = Uri.parse("package:$packageName")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }
}

package com.yilmaz.screenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yilmaz.screenrecorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = PermissionHelper(this, this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !p.checkNotificationPermission()
        )
            p.requestNotificationPermissions()

        setContent {
            ScreenRecorderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isRecorderStarted = remember {
                        mutableStateOf(RecorderService.IS_SERVICE_RUNNING)
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                                    if (p.checkRecordAudioPermission() && p.checkWriteStoragePermission())
                                        isRecorderStarted.value =
                                            startRecorder(isRecorderStarted.value)
                                    else
                                        p.requestRecordAudioWriteStoragePermissions()

                                } else {
                                    if (p.checkRecordAudioPermission())
                                        isRecorderStarted.value =
                                            startRecorder(isRecorderStarted.value)
                                    else
                                        p.requestRecordAudioPermissions()
                                }
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(text = if (isRecorderStarted.value) "Stop" else "Start")
                        }
                    }
                }
            }
        }
    }

    private fun startRecorder(isStarted: Boolean): Boolean {
        if (isStarted) stopRecorderService() else startMediaProjection()
        return !isStarted
    }

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                startRecorderService(data!!)
            }
        }

    private fun startMediaProjection() {
        projectionManager = applicationContext.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        projectionManager.createScreenCaptureIntent().let { resultLauncher.launch(it) }
    }

    private fun startRecorderService(data: Intent) {
        val i = Intent(this@MainActivity, RecorderService::class.java)
        i.action = RecorderService.ACTION_START
        i.putExtra(RecorderService.EXTRA_RESULT_DATA, data)
        startService(i)
    }

    private fun stopRecorderService() {
        val i = Intent(this@MainActivity, RecorderService::class.java)
        i.action = RecorderService.ACTION_STOP
        startService(i)
    }

}
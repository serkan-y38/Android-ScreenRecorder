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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yilmaz.screenrecorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper(this, this).checkNotificationPermission()
        )
            PermissionHelper(this, this).requestNotificationPermissions()

        setContent {
            ScreenRecorderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Recorder()
                }
            }
        }
    }

    @Composable
    fun Recorder() {
        val p = PermissionHelper(this, this)
        val isRecorderStarted = remember {
            mutableStateOf(RecorderService.IS_SERVICE_RUNNING)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF4FC3F7),
                        1.0f to Color(0xFFE1BEE7),
                        radius = 1500.0f,
                        tileMode = TileMode.Repeated
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
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
                modifier = Modifier
                    .wrapContentSize()
                    .width(64.dp)
                    .height(64.dp)
            ) {
                Icon(
                    modifier = Modifier
                        .fillMaxSize(),
                    painter = painterResource(
                        id = if (isRecorderStarted.value)
                            R.drawable.baseline_stop_circle_24
                        else
                            R.drawable.baseline_play_circle_24
                    ),
                    contentDescription = "play - stop button"
                )
            }
        }
    }

    private fun startRecorder(isStarted: Boolean): Boolean {
        if (isStarted) stopRecorderService() else startMediaProjection()
        return !isStarted
    }

    private val projectionResultLauncher =
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
        projectionResultLauncher.launch(projectionManager.createScreenCaptureIntent())
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
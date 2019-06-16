package com.xialulee.wavesyn.android.datatransfer
import androidx.appcompat.app.AppCompatActivity
import android.content.*
import android.os.Environment
import android.net.Uri

open class ActivityEx : AppCompatActivity() {

    data class ResultArgs(
        val requestCode: Int,
        val resultCode: Int,
        val data: Intent?
    )

    data class PermissionArgs(
        val requestCode: Int,
        val permissions: Array<out String>,
        val grantResults: IntArray
    )

    private val requestMap = HashMap<Int, (ResultArgs)->Unit>()
    private val permissionMap = HashMap<Int, (PermissionArgs)->Unit>()

    protected fun getDownloadDirectory(): String {
        return getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString()
    }

    protected fun readUriBytes(uri: Uri?): ByteArray {
        uri?: return ByteArray(0)
        val stream = contentResolver.openInputStream(uri)
        val bytes = stream?.readBytes()
        bytes?: return ByteArray(0)
        return bytes
    }

    protected fun readClipboard(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText =
            clipboard
                .primaryClip
                ?.getItemAt(0)
                ?.coerceToText(applicationContext)
                .toString()
        return clipText
    }

    protected fun writeClipboard(text: String) {
        val clipData = ClipData.newPlainText("text", text)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clipData)
    }

    protected fun activityDo(intent: Intent, onFinished: (ResultArgs)->Unit) {
        var id = 0
        for (i in 0 until 4096) {
            if (!requestMap.containsKey(i)) {
                id = i
                break
            }
        }
        requestMap[id] = onFinished
        startActivityForResult(intent, id)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        requestMap.remove(requestCode)?.invoke(ResultArgs(requestCode, resultCode, data))
    }

    protected fun permittedToDo(permission: String, block: (PermissionArgs)->Unit) {
        var id = 0
        for (i in 0 until 4096) {
            if (!permissionMap.containsKey(i)) {
                id = i
                break
            }
        }
        permissionMap[id] = block
        requestPermissions(arrayOf(permission), id)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionMap.remove(requestCode)?.invoke(PermissionArgs(requestCode, permissions, grantResults))
    }
}


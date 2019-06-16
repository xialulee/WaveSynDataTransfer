package com.xialulee.wavesyn.android.datatransfer

import android.content.*
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import java.net.Socket
import org.json.JSONObject
import kotlin.concurrent.thread
import kotlin.collections.HashMap


import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.Exception


data class ScanResult(
    val ip: String,
    val port: Int,
    val password: Int,
    val action: String,
    val source: String,
    val target: String,
    val fileName: String)

fun parseBarcode(jsonStr: String?): ScanResult? {
    if (jsonStr == null) return null
    val jsonResult = JSONObject(jsonStr)
    val ip = jsonResult.getString(("ip"))
    val port = jsonResult.getString("port").toInt()
    val password = jsonResult.getString("password").toInt()
    val command = jsonResult.getJSONObject("command")
    val action = command.getString("action")
    val source = command.getString(("source"))
    var target = ""
    var fileName = ""
    if (action == "write") {
        target = command.getString("target")
        if (target == "dir:Download") fileName = command.getString("name")
    }
    return ScanResult(
        ip=ip,
        port=port,
        password=password,
        action=action,
        source=source,
        target=target,
        fileName=fileName
    )
}


fun sendHead(outp: DataOutputStream, password: Int) {
    val devModel = "${Build.MANUFACTURER} ${Build.MODEL}".toByteArray()
    val devName = ByteArray(32) {
        if (it < devModel.size)
            devModel[it]
        else
            0
    }

    outp.write(0)
    outp.writeInt(password)
    outp.write(devName)
}


class MainActivity : ActivityEx() {
    val funMap = HashMap<String, (ScanResult)->Unit>()

    private fun readClipb(): ByteArray {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText =
            clipboard
                .primaryClip
                ?.getItemAt(0)
                ?.coerceToText(applicationContext)
                .toString()
        val content =
            JSONObject()
                .put("data", clipText)
                .toString()
                .toByteArray()
        return content
    }

    private fun readPhotoData(uri: Uri?): ByteArray {
        if (uri == null) return ByteArray(0)
        val imgStream = contentResolver.openInputStream(uri)
        val imgBytes = imgStream?.readBytes()
        if (imgBytes == null)
            return ByteArray(0)
        else
            return imgBytes
    }

    private fun sendData(scanResult: ScanResult, data: ByteArray) {
        thread {
            Socket(scanResult.ip, scanResult.port).use {
                val outp = DataOutputStream(it.getOutputStream())
                sendHead(outp, scanResult.password)
                outp.writeInt(data.size)
                outp.write(data)
            }
        }
    }

    private fun sendClipbText(scanResult: ScanResult) = sendData(scanResult, readClipb())

    private fun sendGalleryPhoto(scanResult: ScanResult) {
        val pickPhotoIntent = Intent().setAction("android.intent.action.PICK").setType("image/*")
        activityDo(pickPhotoIntent) {
            val uri = it.data?.data
            sendData(scanResult, readPhotoData(uri))
        }
    }

    private fun recvClipbText(scanResult: ScanResult) {
        thread {
            Socket(scanResult.ip, scanResult.port).use {
                val inp = it.getInputStream()
                val outp = DataOutputStream(it.getOutputStream())
                sendHead(outp, scanResult.password)
                outp.writeInt(0)
                val jsonText = inp.readBytes().toString(Charsets.UTF_8)
                val text = JSONObject(jsonText).getString("data")
                val clipData = ClipData.newPlainText("text", text)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(clipData)
            }
        }
    }

    private fun recvStream(scanResult: ScanResult, stream: OutputStream, onFinished: (()->Unit)? = null) {
        thread {
            Socket(scanResult.ip, scanResult.port).use {
                val inp = it.getInputStream()
                val outp = DataOutputStream(it.getOutputStream())
                sendHead(outp, scanResult.password)
                outp.writeInt(0)
                val bufSize = 32768
                val buf = ByteArray(bufSize)
                while (true) {
                    val len = inp.read(buf, 0, bufSize)
                    if (len < 0) break
                    stream.write(buf, 0, len)
                }
            }
            onFinished?.invoke()
        }
    }

    private fun recvFile(scanResult: ScanResult) {
        val path = "${getDownloadDirectory()}/${scanResult.fileName}"

        val fobj = FileOutputStream(path)
        recvStream(scanResult, fobj) {
            fobj.close()
        }
    }

    private fun recvImage(scanResult: ScanResult) {
        permittedToDo("android.permission.WRITE_EXTERNAL_STORAGE") {

            val path = "${getDownloadDirectory()}/${scanResult.fileName}"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, scanResult.fileName)
                put(MediaStore.Images.Media.DISPLAY_NAME, scanResult.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/*")
            }

            var item: Uri? = null
            try {
                item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (e: Exception) {
                Log.println(Log.ERROR, "insert", e.toString())
            }

            item ?: return@permittedToDo

            val pfd = contentResolver.openFileDescriptor(item, "w", null)
            val fobj = FileOutputStream(pfd?.fileDescriptor)
            recvStream(scanResult, fobj) {
                startActivity(Intent(Intent.ACTION_VIEW, item))
                fobj.close()
                pfd?.close()
            }
        }
    }

    private fun sendFile(scanResult: ScanResult, fileName: String, stream: InputStream) {
        thread {
            Socket(scanResult.ip, scanResult.port).use {
                val outp = DataOutputStream(it.getOutputStream())
                sendHead(outp, scanResult.password)
                outp.writeInt(stream.available())
                outp.write(fileName.length)
                outp.write(fileName.toByteArray())
                val bufLen = 32768
                val buf = ByteArray(bufLen)
                while (true) {
                    val readLen = stream.read(buf, 0, bufLen)
                    if (readLen < 0) break
                    outp.write(buf, 0, readLen)
                }
                outp.close()
            }
            stream.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView.text = """Ordinary files can be found in
            |${getDownloadDirectory()}.
            |Media files are in gallery.""".trimMargin()

        val shareIntent = intent
        var shareStream: InputStream? = null
        var shareFileName = ""
        if (intent.action == "android.intent.action.SEND") {
            val shareUri = shareIntent.extras?.get("android.intent.extra.STREAM") as Uri
            shareStream = contentResolver.openInputStream(shareUri)
            if (shareStream != null && shareUri != null) {
                val fullPath = shareUri.toString()
                val pathArr = fullPath.split("/")
                shareFileName = pathArr[pathArr.size-1]
            }
        }

        funMap["read&clipboard&"] = this::sendClipbText
        funMap["read&gallery&"] = this::sendGalleryPhoto
        funMap["read&storage&"] = {scanResult ->
            if (shareStream != null) {
                this.sendFile(scanResult, shareFileName, shareStream)
            }
        }
        funMap["write&clipboard&"] = this::recvClipbText
        funMap["write&storage:image&dir:Download"] = this::recvImage
        funMap["write&storage:file&dir:Download"] = this::recvFile

        val scanIntent =
            Intent()
                .setAction("com.google.zxing.client.android.SCAN")
                .putExtra("SAVE_HISTORY", false)

        activityDo(scanIntent) {
            val scanResult = parseBarcode(it.data?.getStringExtra("SCAN_RESULT"))
            scanResult ?: return@activityDo
            val command = "${scanResult.action}&${scanResult.source}&${scanResult.target}"
            funMap.get(command)?.invoke(scanResult)
        }
    }

}



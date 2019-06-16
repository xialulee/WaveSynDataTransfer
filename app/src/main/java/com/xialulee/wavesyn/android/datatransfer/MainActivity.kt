package com.xialulee.wavesyn.android.datatransfer

import android.content.*
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import org.json.JSONObject
import kotlin.collections.HashMap


import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.Exception


class MainActivity : ActivityEx() {
    val funMap = HashMap<String, (ServerInfo)->Unit>()

    private fun makeClipboardJson(): ByteArray {
        val clipText = readClipboard()
        val content =
            JSONObject()
                .put("data", clipText)
                .toString()
                .toByteArray()
        return content
    }

    private fun sendClipboardText(serverInfo: ServerInfo) = sendBytes(serverInfo, makeClipboardJson())

    private fun sendGalleryPhoto(scanResult: ServerInfo) {
        val pickPhotoIntent = Intent().setAction("android.intent.action.PICK").setType("image/*")
        activityDo(pickPhotoIntent) {
            val uri = it.data?.data
            sendBytes(scanResult, readUriBytes(uri))
        }
    }

    private fun recvClipboardText(serverInfo: ServerInfo) {
        recvBytes(serverInfo) {
            val text = JSONObject(it.toString(Charsets.UTF_8)).getString("data")
            writeClipboard(text)
        }
    }

    private fun recvFile(scanResult: ServerInfo) {
        val path = "${getDownloadDirectory()}/${scanResult.fileName}"
        val fobj = FileOutputStream(path)
        recvStream(scanResult, fobj) {
            fobj.close()
        }
    }

    private fun recvImage(scanResult: ServerInfo) {
        permittedToDo("android.permission.WRITE_EXTERNAL_STORAGE") {

            val path = "${getDownloadDirectory()}/${scanResult.fileName}"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, scanResult.fileName)
                put(MediaStore.Images.Media.DISPLAY_NAME, scanResult.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/*")
            }

            val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
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

        funMap["read&clipboard&"] = this::sendClipboardText
        funMap["read&gallery&"] = this::sendGalleryPhoto
        funMap["read&storage&"] = {scanResult ->
            if (shareStream != null) {
                sendFile(scanResult, shareFileName, shareStream)
            }
        }
        funMap["write&&clipboard"] = this::recvClipboardText
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



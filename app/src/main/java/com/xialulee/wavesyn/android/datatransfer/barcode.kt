package com.xialulee.wavesyn.android.datatransfer

import org.json.JSONObject

data class ServerInfo(
    val ip: String,
    val port: Int,
    val password: Int,
    val action: String,
    val source: String,
    val target: String,
    val fileName: String)

fun parseBarcode(jsonStr: String?): ServerInfo? {
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
    return ServerInfo(
        ip=ip,
        port=port,
        password=password,
        action=action,
        source=source,
        target=target,
        fileName=fileName
    )
}


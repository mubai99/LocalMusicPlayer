package com.example.musicplayer.lan

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLDecoder

/**
 * 内嵌 HTTP 服务（NanoHTTPD，纯 Java、零外部依赖）。
 * 仅监听本机局域网端口，不连接任何公网。
 *
 * 接口：
 *  GET  /            返回传歌网页（含配对码）
 *  POST /upload      接收单个音频文件（二进制 body）
 *                    请求头: X-Token（配对码）、X-Filename（URL 编码的文件名）
 */
class LanHttpServer(
    port: Int,
    private val token: String,
    private val context: Context,
    private val onActivity: () -> Unit,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        onActivity() // 每次请求都重置闲置计时器

        return when {
            session.method == Method.GET &&
                (session.uri == "/" || session.uri == "/index.html") ->
                newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", PAGE_HTML)

            session.method == Method.POST && session.uri == "/upload" ->
                handleUpload(session)

            else ->
                newFixedLengthResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "not found")
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val provided = session.headers["x-token"]
        if (provided != token) {
            return json(Status.FORBIDDEN, false, "配对码错误")
        }
        val rawName = session.headers["x-filename"]
        if (rawName.isNullOrBlank()) {
            return json(Status.BAD_REQUEST, false, "缺少文件名")
        }
        val name = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            rawName
        }

        return try {
            val bytes = session.inputStream.readBytes()
            if (bytes.isEmpty()) {
                return json(Status.BAD_REQUEST, false, "空文件")
            }
            val ok = MediaStoreWriter.save(context, sanitize(name), bytes)
            if (ok) {
                LanTransferController.notifyReceived(name)
                json(Status.OK, true, "已接收: $name")
            } else {
                json(Status.BAD_REQUEST, false, "仅支持音频文件")
            }
        } catch (e: Exception) {
            Log.w("LanHttpServer", "upload failed", e)
            json(Status.INTERNAL_ERROR, false, "接收失败: ${e.message}")
        }
    }

    private fun json(status: Status, ok: Boolean, msg: String): Response {
        val body = """{"ok":$ok,"msg":"$msg"}"""
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }

    /** 去掉可能破坏路径的字符，仅保留文件名本身。 */
    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return if (base.isBlank()) "audio_${System.currentTimeMillis()}.mp3" else base
    }

    companion object {
        private const val PAGE_HTML = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>本地音乐 · 传歌</title>
<style>
body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;margin:0;background:#12121c;color:#eee;padding:24px}
h2{margin:0 0 8px}
p{color:#9aa}
.card{background:#1d1d2e;border:1px solid #2c2c44;border-radius:12px;padding:16px;margin:16px 0}
button{background:#5b6cff;color:#fff;border:0;border-radius:10px;padding:12px 18px;font-size:15px;cursor:pointer}
input[type=file]{display:block;margin:12px 0;color:#ccc}
#log{margin-top:16px;font-size:13px;line-height:1.7;color:#bfe}
</style>
</head>
<body>
<h2>本地音乐 · 局域网传歌</h2>
<p id="tip">正在校验配对码…</p>
<div class="card">
  <input id="file" type="file" accept="audio/*" multiple>
  <button id="send">发送选中歌曲</button>
</div>
<div id="log"></div>
<script>
var params = new URLSearchParams(location.search);
var token = params.get('token') || '';
var tip = document.getElementById('tip');
if (token) {
  tip.textContent = '配对码已生效，选择歌曲后点击"发送"。';
} else {
  tip.textContent = '缺少配对码，请通过 App 内显示的链接打开本页。';
}
function log(msg){ var d = document.createElement('div'); d.textContent = msg; document.getElementById('log').appendChild(d); }
document.getElementById('send').onclick = function(){
  var files = document.getElementById('file').files;
  if (!files.length){ log('请先选择文件'); return; }
  if (!token){ log('缺少配对码，无法发送'); return; }
  for (var i = 0; i < files.length; i++){
    (function(f){
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/upload');
      xhr.setRequestHeader('X-Token', token);
      xhr.setRequestHeader('X-Filename', encodeURIComponent(f.name));
      xhr.onload = function(){ log(f.name + ' -> ' + xhr.responseText); };
      xhr.onerror = function(){ log(f.name + ' 发送失败'); };
      xhr.send(f);
    })(files[i]);
  }
};
</script>
</body>
</html>
"""
    }
}

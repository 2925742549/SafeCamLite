package com.xiao.safecamlite;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

final class LocalHttpServer extends NanoHTTPD {
    private final Context appContext;
    private final String pin;
    private final SimpleDateFormat displayFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    LocalHttpServer(Context context, int port, String pin) {
        super(port);
        this.appContext = context.getApplicationContext();
        this.pin = pin;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (!pin.equals(params.get("pin"))) {
            return html(Response.Status.UNAUTHORIZED,
                    "<h2>401 PIN required</h2><p>请在地址后加：<code>?pin=你的PIN</code></p>");
        }

        if ("/snapshot.jpg".equals(uri)) return snapshot();
        if ("/recordings".equals(uri)) return recordingsPage();
        if ("/api/recordings".equals(uri)) return recordingsJson();
        if (uri != null && uri.startsWith("/recording/")) return recordingFile(uri);
        if ("/guide".equals(uri)) return guidePage();

        return livePage();
    }

    private Response snapshot() {
        byte[] jpeg = FrameStore.latestJpeg.get();
        if (jpeg == null) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "Camera is starting...");
        }
        Response res = newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                new ByteArrayInputStream(jpeg),
                jpeg.length
        );
        res.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        return res;
    }

    private Response recordingFile(String uri) {
        try {
            String name = URLDecoder.decode(uri.substring("/recording/".length()), "UTF-8");
            if (name.contains("/") || name.contains("\\") || !name.endsWith(".jpg")) {
                return html(Response.Status.BAD_REQUEST, "<h2>Bad file name</h2>");
            }
            File f = new File(RecordingManager.dir(appContext), name);
            if (!f.exists()) return html(Response.Status.NOT_FOUND, "<h2>Not found</h2>");
            Response res = newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    new FileInputStream(f),
                    f.length()
            );
            res.addHeader("Cache-Control", "no-store");
            return res;
        } catch (Exception e) {
            return html(Response.Status.INTERNAL_ERROR, "<h2>Error reading file</h2>");
        }
    }

    private Response livePage() {
        String p = esc(pin);
        String recordingText = AppSettings.recordingEnabled(appContext) ? "ON" : "OFF";
        String nightText = AppSettings.nightMode(appContext) ? "夜间增强 ON" : "夜间增强 OFF";
        String torchText = AppSettings.torchEnabled(appContext) ? "手电补光 ON" : "手电补光 OFF";

        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>SafeCam Lite Pro Night</title>" + css() + "</head><body>"
                + "<header><b>SafeCam Lite Pro Night</b><nav><a href='/?pin=" + p + "'>实时</a><a href='/recordings?pin=" + p + "'>回放</a><a href='/guide?pin=" + p + "'>外网</a></nav></header>"
                + "<main><div class='card'><h2>实时监控</h2>"
                + "<p>循环记录：" + recordingText
                + "，记录间隔：" + AppSettings.recordSeconds(appContext) + " 秒，"
                + nightText + "，" + torchText
                + "，已用空间：" + RecordingManager.humanSize(RecordingManager.totalBytes(appContext)) + "</p>"
                + "<img id='cam' alt='camera frame' src='/snapshot.jpg?pin=" + p + "&t=0'>"
                + "<div><button onclick='toggle()'>暂停 / 继续</button><a class='btn' href='/recordings?pin=" + p + "'>查看回放</a></div>"
                + "</div></main>"
                + "<script>let on=true;function loop(){if(on){document.getElementById('cam').src='/snapshot.jpg?pin=" + p + "&t='+Date.now();}setTimeout(loop,500);}loop();function toggle(){on=!on;}</script>"
                + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response recordingsPage() {
        String p = esc(pin);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>SafeCam Replay</title>" + css() + "</head><body>"
                + "<header><b>回放记录</b><nav><a href='/?pin=" + p + "'>实时</a><a href='/recordings?pin=" + p + "'>回放</a><a href='/guide?pin=" + p + "'>外网</a></nav></header>"
                + "<main><div class='card'><h2>循环记录回放</h2><p>点击下面记录可查看，也可以点自动播放按时间顺序回放。旧记录会按 App 设置自动删除。</p>"
                + "<img id='play' alt='replay frame'><div><button onclick='playAuto()'>自动播放</button><button onclick='stopAuto()'>停止</button></div>"
                + "<div id='list' class='list'>加载中...</div></div></main>"
                + "<script>"
                + "let items=[],idx=0,timer=null;const pin='" + p + "';"
                + "fetch('/api/recordings?pin='+pin+'&t='+Date.now()).then(r=>r.json()).then(d=>{items=d.items||[];render();if(items.length){show(items[items.length-1].name);}});"
                + "function render(){let el=document.getElementById('list');if(!items.length){el.innerHTML='<p>暂无回放记录。请确认 App 里开启了循环记录，并运行几分钟。</p>';return;}el.innerHTML=items.slice().reverse().map((x)=>'<button class=\"row\" onclick=\"show(\\''+x.name+'\\')\">'+x.time+' ｜ '+x.size+'</button>').join('');}"
                + "function show(n){document.getElementById('play').src='/recording/'+encodeURIComponent(n)+'?pin='+pin+'&t='+Date.now();idx=items.findIndex(x=>x.name===n);}"
                + "function playAuto(){if(!items.length)return;stopAuto();timer=setInterval(()=>{show(items[idx].name);idx=(idx+1)%items.length;},700);}"
                + "function stopAuto(){if(timer){clearInterval(timer);timer=null;}}"
                + "</script></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response recordingsJson() {
        File[] files = RecordingManager.list(appContext);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");
        boolean first = true;
        for (File f : files) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(json(f.getName())).append("\",");
            sb.append("\"time\":\"").append(json(displayFmt.format(new Date(f.lastModified())))).append("\",");
            sb.append("\"size\":\"").append(json(RecordingManager.humanSize(f.length()))).append("\"}");
        }
        sb.append("]}");
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString());
    }

    private Response guidePage() {
        String p = esc(pin);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Remote View Guide</title>" + css() + "</head><body>"
                + "<header><b>外出实时观看</b><nav><a href='/?pin=" + p + "'>实时</a><a href='/recordings?pin=" + p + "'>回放</a><a href='/guide?pin=" + p + "'>外网</a></nav></header>"
                + "<main><div class='card'><h2>推荐方式：Tailscale / ZeroTier</h2>"
                + "<p>不要把 8080 端口直接映射到公网。更稳妥的方式是两台手机安装 Tailscale 或 ZeroTier，加入同一个虚拟局域网。</p>"
                + "<ol><li>旧手机安装并登录 Tailscale/ZeroTier。</li><li>外出查看的手机也安装并登录同一账号/网络。</li><li>旧手机重新打开本 App，查看地址里如果出现 100.x.x.x 之类的虚拟 IP，就用这个地址访问。</li><li>浏览器打开：<code>http://虚拟IP:端口/?pin=你的PIN</code></li></ol>"
                + "<p>这个版本不做隐藏监控、不绕过系统权限，适合自有场所、店铺、仓库、宠物等明确场景使用。</p>"
                + "</div></main></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response html(Response.Status status, String body) {
        return newFixedLengthResponse(status, "text/html; charset=utf-8",
                "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" + css() + "</head><body><main><div class='card'>" + body + "</div></main></body></html>");
    }

    private static String css() {
        return "<style>"
                + "body{margin:0;background:#101114;color:#f1f1f1;font-family:Arial,'Noto Sans SC',sans-serif;}"
                + "header{position:sticky;top:0;background:#1f232b;padding:12px 14px;box-shadow:0 2px 10px #0008;}"
                + "header b{font-size:18px}nav{float:right}nav a{color:#fff;text-decoration:none;margin-left:12px;font-size:14px}"
                + "main{padding:14px}.card{max-width:980px;margin:auto;background:#181b22;border-radius:14px;padding:14px;box-shadow:0 4px 20px #0006}"
                + "img{width:100%;max-height:72vh;object-fit:contain;background:#000;border-radius:10px;margin:8px 0}"
                + "button,.btn{display:inline-block;text-decoration:none;border:0;border-radius:10px;padding:11px 14px;margin:6px;background:#1565c0;color:white;font-size:15px}"
                + ".row{display:block;width:100%;text-align:left;background:#2a2e38}.list{margin-top:10px}code{background:#2a2e38;padding:2px 5px;border-radius:5px}"
                + "p,li{line-height:1.55;color:#ddd}"
                + "</style>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

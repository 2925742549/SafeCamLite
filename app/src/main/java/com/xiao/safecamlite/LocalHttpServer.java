package com.xiao.safecamlite;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class LocalHttpServer extends NanoHTTPD {
    private final String pin;

    LocalHttpServer(int port, String pin) {
        super(port);
        this.pin = pin;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (!pin.equals(params.get("pin"))) {
            return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    "text/html; charset=utf-8",
                    "<h2>401 PIN required</h2><p>Use: /?pin=your_pin</p>"
            );
        }

        if ("/snapshot.jpg".equals(uri)) {
            byte[] jpeg = FrameStore.latestJpeg.get();
            if (jpeg == null) {
                return newFixedLengthResponse(
                        Response.Status.NO_CONTENT,
                        "text/plain",
                        "Camera is starting..."
                );
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

        String html = html(pin);
        return newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                html
        );
    }

    private static String html(String pin) {
        String safePin = pin.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>SafeCam Lite</title>"
                + "<style>"
                + "body{margin:0;background:#111;color:#eee;font-family:Arial,sans-serif;text-align:center;}"
                + "header{padding:12px;background:#1e1e1e;position:sticky;top:0;}"
                + "img{width:100%;max-width:980px;height:auto;background:#222;}"
                + ".tip{font-size:13px;color:#bbb;padding:8px 14px;line-height:1.45;}"
                + "button{font-size:16px;border:0;border-radius:10px;padding:10px 14px;margin:8px;background:#1565c0;color:white;}"
                + "</style></head><body>"
                + "<header><b>SafeCam Lite - Live View</b><div class='tip'>Visible self-owned camera stream. PIN protected. Do not expose this port to public internet.</div></header>"
                + "<img id='cam' alt='camera frame' src='/snapshot.jpg?pin=" + safePin + "&t=0'>"
                + "<div><button onclick='toggle()'>Pause / Resume</button></div>"
                + "<script>"
                + "let on=true;function loop(){if(on){document.getElementById('cam').src='/snapshot.jpg?pin=" + safePin + "&t='+Date.now();}setTimeout(loop,500);}loop();"
                + "function toggle(){on=!on;}"
                + "</script></body></html>";
    }
}

package keystrokesmod.script.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import keystrokesmod.script.Manager;
import keystrokesmod.utility.Utils;

public class Request {
    public String method;
    public String url;
    public List<String[]> headers = new ArrayList<>();
    public String userAgent;
    public int connectionTimeout;
    public int readTimeout;
    public String content = "";

    public Request(String method, String URL) {
        this.method = !method.equals("POST") && !method.equals("GET") ? "GET" : method;
        this.url = URL;
        this.userAgent = "";
        this.readTimeout = 5000;
        this.connectionTimeout = 5000;
    }

    public void addHeader(String header, String value) {
        this.headers.add(new String[] {header, value});
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setConnectTimeout(int timeout) {
        this.connectionTimeout = timeout;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Response fetch() {
        if (!Manager.enableHttpRequests.isToggled()) {
            Utils.sendMessage("&cFailed to send http request, http requests are not enabled.");
            return new Response(404, "");
        }

        if (this.url.isEmpty()) {
            return null;
        }

        HttpURLConnection con = null;

        try {
            URL urlObj = new URL(this.url);
            con = (HttpURLConnection) urlObj.openConnection();
            con.setRequestMethod(this.method);
            con.setConnectTimeout(this.connectionTimeout);
            con.setReadTimeout(this.readTimeout);
            con.setRequestProperty(
                    "User-Agent",
                    this.userAgent.isEmpty()
                            ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            : this.userAgent);

            for (String[] h : this.headers) {
                con.setRequestProperty(h[0], h[1]);
            }

            if (this.method.equals("POST") && !this.content.isEmpty()) {
                con.setDoOutput(true);
                byte[] out = this.content.getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(out.length);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.connect();
                OutputStream os = con.getOutputStream();

                try {
                    os.write(out);
                } catch (Throwable var22) {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Throwable var20) {
                            var22.addSuppressed(var20);
                        }
                    }

                    throw var22;
                }

                if (os != null) {
                    os.close();
                }
            }

            String contents = "";

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));

                try {
                    StringBuilder sb = new StringBuilder();

                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }

                    contents = sb.toString();
                } catch (Throwable var23) {
                    try {
                        br.close();
                    } catch (Throwable var21) {
                        var23.addSuppressed(var21);
                    }

                    throw var23;
                }

                br.close();
            } catch (IOException er1) {
                InputStream es = con.getErrorStream();
                if (es != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(es));

                    try {
                        StringBuilder sb = new StringBuilder();

                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }

                        contents = sb.toString();
                    } catch (Throwable var24) {
                        try {
                            br.close();
                        } catch (Throwable var19) {
                            var24.addSuppressed(var19);
                        }

                        throw var24;
                    }

                    br.close();
                }
            }

            List<String[]> respHeaders = new ArrayList<>();

            for (Entry<String, List<String>> e : con.getHeaderFields().entrySet()) {
                if (e.getKey() != null) {
                    for (String v : e.getValue()) {
                        respHeaders.add(new String[] {e.getKey(), v});
                    }
                }
            }

            return new Response(con.getResponseCode(), contents, respHeaders);
        } catch (IOException var26) {
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "Request(" + this.method + "," + this.url + ")";
    }
}

package net.nicovrc.dev;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

public class HTTPServer extends Thread {

    private final String nicovrc_baseurl = "http://localhost:25252";

    public HTTPServer(){

    }

    @Override
    public void run() {

        boolean[] isRun = {true};
        ServerSocket svSock = null;
        try {
            svSock = new ServerSocket(8882);
        } catch (Exception e){
            e.printStackTrace();
            isRun[0] = false;
            return;
        }

        while (isRun[0]){
            try {
                final Socket sock = svSock.accept();
                Thread.ofVirtual().start(()->{
                    try {
                        InputStream in = sock.getInputStream();
                        OutputStream out = sock.getOutputStream();

                        final String httpRequest = Function.getHTTPRequest(sock);
                        if (httpRequest == null){
                            in.close();
                            out.close();
                            sock.close();
                            return;
                        }

                        String uri = Function.getURI(httpRequest).replaceAll("/dummy\\.m3u8", "/");

                        String httpVersion = Function.getHTTPVersion(httpRequest);
                        if (uri.startsWith("/?url=https://www.nicovideo.jp/") || uri.startsWith("/?url=http://www.nicovideo.jp/")) {

                            out.write(hls_dummy_create(httpVersion, uri));

                        } else if (uri.startsWith("/?url=https://nico.ms") || uri.startsWith("/?url=http://nico.ms")) {

                            String url = uri.replaceAll("^/\\?url=", "");
                            try (HttpClient client = HttpClient.newBuilder()
                                    .version(HttpClient.Version.HTTP_2)
                                    .followRedirects(HttpClient.Redirect.NEVER)
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .build()) {

                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(new URI(url))
                                        .headers("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                                        .GET()
                                        .build();

                                HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
                                HttpHeaders headers = send.headers();
                                String s = headers.firstValue("location").get();

                                if (s.startsWith("https://www.nicovideo.jp/")) {
                                    out.write(hls_dummy_create(httpVersion, uri));
                                } else {
                                    out.write(redirect(httpVersion, uri));
                                }

                            }
                        } else if (uri.startsWith("/hls_create.m3u8")) {

                            out.write(hls_create(httpVersion, uri));

                        } else {
                            out.write(redirect(httpVersion, uri));
                        }

                        in.close();
                        out.close();
                        sock.close();
                    } catch (Exception e){
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e){
                e.printStackTrace();
                isRun[0] = false;
            }
        }

        try {
            svSock.close();
        } catch (Exception e){
            // e.printStackTrace();
        }

    }

    public byte[] hls_dummy_create(String httpVersion, String uri) throws Exception {
        byte[] bytes;
        StringBuilder header = new StringBuilder();
        StringBuilder m3u8_dummy = new StringBuilder();

        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build()){

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(nicovrc_baseurl+uri+"?hlsfix"))
                    .headers("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0 HLSFix/1.0")
                    .GET()
                    .build();

            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());

            String m3u8 = send.body();
            for (String str : m3u8.split("\n")){

                if (!str.startsWith("/dummy.m3u8?url=")){
                    m3u8_dummy.append(str).append("\n");
                    continue;
                }

                m3u8_dummy.append("/hls_create.m3u8").append(uri.replaceAll("^/", ""));

            }

            bytes = m3u8_dummy.toString().getBytes(StandardCharsets.UTF_8);

        }

        if (httpVersion == null || httpVersion.equals("1.1")) {
            header.append("HTTP/1.1 200 OK\r\nContent-Length: ").append(bytes.length).append("\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: ").append(new Date()).append("\r\n\r\n").append(m3u8_dummy);
            bytes = header.toString().getBytes(StandardCharsets.UTF_8);

        } else if (httpVersion.equals("2.0")) {
            header.append("HTTP/2.0 200 OK\r\nContent-Length: ").append(bytes.length).append("\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: ").append(new Date()).append("\r\n\r\n").append(m3u8_dummy);
            bytes = header.toString().getBytes(StandardCharsets.UTF_8);
        } else {
            header.append("HTTP/1.0 200 OK\r\nContent-Length: ").append(bytes.length).append("\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: ").append(new Date()).append("\r\n\r\n").append(m3u8_dummy);
            bytes = header.toString().getBytes(StandardCharsets.UTF_8);
        }


        return bytes;
    }

    public byte[] hls_create(String httpVersion, String uri) throws Exception{
        byte[] bytes;
        String s = UUID.randomUUID() + "_" + new Date().getTime();
        File file = new File("/hls/" + s);

        if (!file.exists()) {
            file.mkdir();
        }

        //System.out.println("ffmpeg -i https://yobi.nicovrc.net" + uri + " -c:v copy -c:a copy -f hls -hls_playlist_type vod -hls_segment_filename /hls/"+s+"/%3d.ts /hls/"+s+"/main.m3u8");
        ProcessBuilder pb = new ProcessBuilder("/bin/ffmpeg", "-v","quiet","-i",nicovrc_baseurl + uri,"-c:v","copy","-c:a","copy","-f","hls","-hls_playlist_type","vod","-hls_segment_filename","/hls/"+s+"/%3d.ts","/hls/"+s+"/main.m3u8");
        Process process = pb.start();

        //new Thread(() -> { try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String l; while ((l = r.readLine()) != null) l = l; } catch (IOException ignored) {} }).start();
        //process.waitFor();

        //System.out.println("debug");

        //System.out.println(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

        while (!new File("/hls/"+s+"/main.m3u8").exists()){
            Thread.sleep(10L);
        }

        String hlsText = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/hls/"+s+"/main.m3u8"), StandardCharsets.UTF_8))){
            String str;
            StringBuilder sb = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                sb.append(str).append("\n");
            }
            hlsText = sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder hls_output = new StringBuilder();
        for (String str : hlsText.split("\n")){
            if (str.startsWith("#")){
                hls_output.append(str).append("\n");
                continue;
            }

            hls_output.append("\n/hls/").append(s).append("/").append(str).append("\n");
        }

        byte[] hls = hls_output.toString().getBytes(StandardCharsets.UTF_8);

        if (httpVersion == null || httpVersion.equals("1.1")) {
            bytes = ("HTTP/1.1 200 OK\r\nContent-Length: "+hls.length+"\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: "+(new Date())+"\r\n\r\n" + hls_output).getBytes(StandardCharsets.UTF_8);
        } else if (httpVersion.equals("2.0")) {
            bytes = ("HTTP/2.0 200 OK\r\nContent-Length: "+hls.length+"\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: "+(new Date())+"\r\n\r\n" + hls_output).getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = ("HTTP/1.0 200 OK\r\nContent-Length: "+hls.length+"\r\nContent-Type: application/vnd.apple.mpegurl\r\nDate: "+(new Date())+"\r\n\r\n" + hls_output).getBytes(StandardCharsets.UTF_8);
        }

        return bytes;
    }

    public byte[] redirect(String httpVersion, String uri){

        byte[] bytes = null;

        if (httpVersion == null || httpVersion.equals("1.1")){
            bytes = ("HTTP/1.1 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        } else if (httpVersion.equals("2.0")){
            bytes = ("HTTP/2.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = ("HTTP/1.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        }

        return bytes;

    }
}

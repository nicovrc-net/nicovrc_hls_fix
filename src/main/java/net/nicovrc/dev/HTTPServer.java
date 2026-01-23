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

    public HTTPServer(){

    }

    @Override
    public void run() {

        boolean[] isRun = {true};
        ServerSocket svSock = null;
        try {
            svSock = new ServerSocket(8881);
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
                        byte[] bytes = null;

                        if (uri.startsWith("/?url=https://www.nicovideo.jp/") || uri.startsWith("/?url=http://www.nicovideo.jp/")) {
                            out.write(hls_create(httpVersion, uri));
                        } else if (uri.startsWith("/?url=https://nico.ms") || uri.startsWith("/?url=http://nico.ms")) {

                            String url = uri.replaceAll("^/\\?url=", "");
                            try (HttpClient client = HttpClient.newBuilder()
                                    .version(HttpClient.Version.HTTP_2)
                                    .followRedirects(HttpClient.Redirect.NEVER)
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .build()){

                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(new URI(url))
                                        .headers("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                                        .GET()
                                        .build();

                                HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
                                HttpHeaders headers = send.headers();
                                String s = headers.firstValue("location").get();

                                if (s.startsWith("https://www.nicovideo.jp/")){
                                    out.write(hls_create(httpVersion, uri));
                                } else {
                                    if (httpVersion == null || httpVersion.equals("1.1")){
                                        bytes = ("HTTP/1.1 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                                    } else if (httpVersion.equals("2.0")){
                                        bytes = ("HTTP/2.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                                    } else {
                                        bytes = ("HTTP/1.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                                    }

                                    out.write(bytes);
                                }

                            }
                        } else {
                            if (httpVersion == null || httpVersion.equals("1.1")){
                                bytes = ("HTTP/1.1 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            } else if (httpVersion.equals("2.0")){
                                bytes = ("HTTP/2.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            } else {
                                bytes = ("HTTP/1.0 302 Found\r\nLocation: https://nicovrc.net"+uri+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            }

                            out.write(bytes);

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

    public byte[] hls_create(String httpVersion, String uri) throws Exception{
        byte[] bytes;
        String s = UUID.randomUUID() + "_" + new Date().getTime();
        File file = new File("/hls/" + s);

        if (!file.exists()) {
            file.mkdir();
        }

        //System.out.println("ffmpeg -i https://yobi.nicovrc.net" + uri + " -c:v copy -c:a copy -f hls -hls_playlist_type vod -hls_segment_filename /hls/"+s+"/%3d.ts /hls/"+s+"/main.m3u8");
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", "ffmpeg -v quiet -i https://yobi.nicovrc.net" + uri + " -c:v copy -c:a copy -f hls -hls_playlist_type vod -hls_segment_filename /hls/"+s+"/%3d.ts /hls/"+s+"/main.m3u8", " > /dev/null");
        Process process = pb.start();

        new Thread(() -> { try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String l; while ((l = r.readLine()) != null) System.out.println(l); } catch (IOException ignored) {} }).start();
        process.waitFor();

        //System.out.println("debug");

        //System.out.println(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

        if (httpVersion == null || httpVersion.equals("1.1")) {
            bytes = ("HTTP/1.1 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/" + s + "/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        } else if (httpVersion.equals("2.0")) {
            bytes = ("HTTP/2.0 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/" + s + "/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = ("HTTP/1.0 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/" + s + "/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        }

        return bytes;
    }
}

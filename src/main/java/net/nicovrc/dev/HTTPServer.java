package net.nicovrc.dev;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

                        String uri = Function.getURI(httpRequest);

                        String httpVersion = Function.getHTTPVersion(httpRequest);
                        String method = Function.getMethod(httpRequest);
                        byte[] bytes = null;

                        if (uri.startsWith("/?url=htttps://www.nicovideo.jp/")) {
                            String s = UUID.randomUUID() + "_" + new Date().getTime();
                            File file = new File("/hls/" + s);

                            if (!file.exists()){
                                file.mkdir();
                            }

                            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", "ffmpeg -i \"https://nicovrc.net"+uri+"\" -c:v copy -c:a copy -f hls -hls_list_size 0 /hls/" + s + "/main.m3u8");
                            Process process = pb.start();
                            process.waitFor();

                            if (httpVersion == null || httpVersion.equals("1.1")){
                                bytes = ("HTTP/1.1 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/"+s+"/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            } else if (httpVersion.equals("2.0")){
                                bytes = ("HTTP/2.0 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/"+s+"/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            } else {
                                bytes = ("HTTP/1.0 302 Found\r\nLocation: https://chocolat.nicovrc.net/hls/"+s+"/main.m3u8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                            }

                            out.write(bytes);

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
}

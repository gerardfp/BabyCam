package com.example.gerard.babycam;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MyServer {

    public MyServer(){
        MainActivity.SERVER_IP = getLocalIpAddress();
        MainActivity.SERVER_PORT = 8080;

        Thread fst = new Thread(new ServerThread());
        fst.start();

        Log.v("MyServerEndPoint",MainActivity.SERVER_IP + ":"+ MainActivity.SERVER_PORT);
    }

    public class ServerThread implements Runnable {

        public void run() {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(MainActivity.SERVER_PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    try {
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(
                                        client.getInputStream()));
                        String line = null;
                        int type = 0;
                        while ((line = in.readLine()) != null && !line.equals("")) {
                            if(line.equals("GET / HTTP/1.1")) {
                                type = 1;
                            }
                            else if(line.equals("GET /image HTTP/1.1")) {
                                type = 2;
                            }
                        }
                        SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(client, type);
                        socketServerReplyThread.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            finally {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class SocketServerReplyThread extends Thread {
        private Socket hostThreadSocket;
        private int type;

        SocketServerReplyThread(Socket socket, int type) {
            this.type = type;
            hostThreadSocket = socket;
        }

        @Override
        public void run() {
            try {
                OutputStream outputStream = hostThreadSocket.getOutputStream();

                if(type == 2) {
                    if(MainActivity.currentByteArray != null) {
                        outputStream.write(MainActivity.currentByteArray);
                    }
                } else {
                    String msgReply = "<html>\n" +
                            "<body>\n" +
                            "<img id='image'>\n" +
                            "<script>\n" +
                            "var img = document.getElementById('image');\n" +
                            "setInterval(\n" +
                            "\tfunction(){\n" +
                            "\t\tvar req = new XMLHttpRequest();\n" +
                            "\t\treq.responseType = 'blob';\n" +
                            "\t\treq.onload = function(e) {\n" +
                            "\t\t\tif(req.response){\n" +
                            "\t\t\t\timageurl = window.URL.createObjectURL(req.response);\n" +
                            "\t\t\t\tif(imageurl) img.src = imageurl;\n" +
                            "\t\t\t}\n" +
                            "\t\t};\n" +
                            "\t\treq.open(\"GET\",\"http://" + MainActivity.SERVER_IP + ":" + MainActivity.SERVER_PORT + "/image\");\n" +
                            "\t\treq.send();\n" +
                            "\t}\n" +
                            "\t,50\n" +
                            ")\n" +
                            "</script>\n" +
                            "</body>\n" +
                            "</html>";
                    PrintStream printStream = new PrintStream(outputStream);
                    printStream.print(msgReply);
                    printStream.close();
                }
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();

                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
}


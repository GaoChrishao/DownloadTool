package sample.download;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class DownloadServer {
    public static DownloadFile getDownloadFile(String url,  String savePath,int threadCount){
        String fileName=url.substring(url.lastIndexOf('/')+1);
        Long fileSize=getFileSize(url);
        if(fileSize<=0)return null;
        DownloadFile downloadFile=null;
        try{
            downloadFile= new DownloadFile(fileName,url,fileSize,threadCount,savePath);
        }catch (IOException e){
            e.printStackTrace();
        }
        return downloadFile;
    }



    /**
     * @return 要下载的文件的尺寸
     */
    private static long getFileSize(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("HEAD");
            conn.connect();
            System.out.println("* 连接服务器成功");
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL错误");
        } catch (IOException e) {
            System.err.println("x 连接服务器失败["+ e.getMessage() +"]");
            return -1;
        }
        return conn.getContentLengthLong();
    }

    public static List<DownloadFile> getAllDownloadFile() {
        List<DownloadLogger> loggerList=DownloadLogger.getAllLog();
        List<DownloadFile>downloadFiles=new ArrayList<>();
        for (DownloadLogger downloadLogger:loggerList){
            try {
                downloadFiles.add(new DownloadFile(downloadLogger));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return downloadFiles;
    }


}


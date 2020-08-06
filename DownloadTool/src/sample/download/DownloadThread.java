package sample.download;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

class DownloadThread extends Thread {
    private final String url;

    // 下载的文件区间
    private long lowerBound;
    private long upperBound;
    private DownloadFile downloadFile;
    public int threadId;

    //线程同步
    private final Object lock=new Object();
    private volatile boolean pause=false;
    //停止线程
    public volatile boolean stop=false;

    //回调接口
    private CallBack callBack;



    DownloadThread(String url, long lowerBound, long upperBound, DownloadFile downloadFile, int threadID) {
        this.url = url;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.downloadFile = downloadFile;
        this.threadId = threadID;
    }


    public static interface CallBack{
        public void call(DownloadThread downloadThread);
    }

    public void setCallBack(CallBack callBack){
        this.callBack=callBack;
    }

    @Override
    public void run() {
        if(lowerBound>upperBound){
            System.out.println("线程" + threadId + "：已经下载完毕:"+lowerBound+"--"+upperBound);
            if(callBack!=null){
                callBack.call(this);
            }
            return;
        }
        ReadableByteChannel input = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024 * 1); // 缓存区大小,1MB
            input = connect();
            System.out.println("线程" + threadId + "：连接成功，开始下载:"+lowerBound+"--"+upperBound);

            int len;
            while (lowerBound <= upperBound&&!stop) {
                while (pause){
                    onPause();
                }
                if(stop){
                    break;
                }
                //System.out.println(threadId+"下载中");
                buffer.clear();
                len = input.read(buffer);
                downloadFile.write(lowerBound, buffer, threadId, upperBound);
                lowerBound += len;

            }
            System.out.println("线程" + threadId + "：下载完成" + ": " + lowerBound + "-" + upperBound);
        } catch (IOException e) {
            if(e instanceof ClosedByInterruptException){
                System.out.println("interrupt关闭线程"+threadId);
            }else{
                e.printStackTrace();
                System.err.println("线程" + threadId + "：遇到错误：" + e.getMessage() + "，结束下载");
            }

        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //执行回调函数，让DownloadFile类知道该线程已经下载完毕
            if(callBack!=null){
                callBack.call(this);
            }
        }
    }

    /**
     * 连接服务器
     */
    private ReadableByteChannel connect() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setRequestMethod("GET");
        //请求区间是[lowerBound,upperBound]
        conn.setRequestProperty("Range", "bytes=" + lowerBound + "-" + upperBound);
        conn.connect();

        int statusCode = conn.getResponseCode();
        if (HttpURLConnection.HTTP_PARTIAL != statusCode) {
            conn.disconnect();
            throw new IOException("发生错误，code：" + statusCode);
        }

        return Channels.newChannel(conn.getInputStream());
    }





    /**
     * 等待lock
     */
    private void onPause() {
        synchronized (lock) {
            try {
                System.out.println("wait---------------");
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 调用该方法实现线程的暂停
     */
    public void pauseThread(){
        //pause = true;
        stop=true;
    }


    /*
    调用该方法实现恢复线程的运行
     */
    public void resumeThread(){
        //pause =false;
        synchronized (lock){
            lock.notify();
            System.out.println("notify----------------");
        }
    }

}

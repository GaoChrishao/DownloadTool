package sample.download;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import sample.MyCheckBox;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class DownloadFile implements DownloadThread.CallBack {
    private RandomAccessFile file=null;
    private FileChannel channel=null; // 线程安全类
    private AtomicLong wroteSize; // 已写入的长度
    private DownloadLogger downloadLogger;
    public String savePath; //文件保存地址
    private Long beginTime;
    public MyCheckBox cb=new MyCheckBox();
    private boolean pause=false;
    private boolean needToDelete=false;


    private SimpleStringProperty name; //文件名
    private SimpleStringProperty link;
    private SimpleStringProperty status;
    private SimpleStringProperty speed;
    private long fileSize;
    private SimpleStringProperty fileSizeShow;
    private SimpleStringProperty timeLeft;
    private SimpleStringProperty timeSpend;
    private SimpleStringProperty progress;
    private SimpleIntegerProperty threadCountActived;
    private int threadCountAll;
    private volatile List<DownloadThread> threadList=new ArrayList<>();


    public DownloadFile(String name, String link, Long fileSize, int threadCount, String savePath) throws IOException {
        this.name = new SimpleStringProperty(name);
        this.link = new SimpleStringProperty(link);
        this.fileSize = fileSize;
        this.fileSizeShow=new SimpleStringProperty(fileSize/1024/1024+"MB");
        this.threadCountActived = new SimpleIntegerProperty(threadCount);
        this.threadCountAll=threadCount;
        this.status=new SimpleStringProperty("准备");
        this.speed=new SimpleStringProperty("-");
        this.timeLeft=new SimpleStringProperty("-");
        this.progress=new SimpleStringProperty("-");
        this.timeSpend=new SimpleStringProperty("-");

        this.savePath=savePath;

        this.wroteSize = new AtomicLong(0);
        this.file=new RandomAccessFile(savePath+"/"+name,"rw");
        this.file.setLength(fileSize);
        this.channel=file.getChannel();
    }

    public DownloadFile(DownloadLogger logger) throws IOException {
        this.name = new SimpleStringProperty(logger.getLogValue("fileName"));
        this.link = new SimpleStringProperty(logger.getLogValue("url"));
        this.fileSize = Long.parseLong(logger.getLogValue("fileSize"));
        this.fileSizeShow=new SimpleStringProperty(fileSize/1024/1024+"MB");
        this.threadCountAll = Integer.parseInt(logger.getLogValue("threadCount"));
        this.threadCountActived = new SimpleIntegerProperty(threadCountAll);
        this.status=new SimpleStringProperty("准备");
        this.speed=new SimpleStringProperty("-");
        this.timeLeft=new SimpleStringProperty("-");
        this.progress=new SimpleStringProperty("-");
        this.savePath=logger.getLogValue("savePath");;
        this.wroteSize = new AtomicLong(logger.getWroteSize());
        this.timeSpend=new SimpleStringProperty("-");

        if(wroteSize.get()>=fileSize){
            status.set("完成");
        }
        if(!Files.exists(Paths.get(savePath + "/" + name.get()))){
            status.set("已删除");
        }
        progress.set(getProgressStr(wroteSize.get() / (double)fileSize * 100));

    }


    /**
     * 线程结束后的回调函数
     * @param downloadThread
     */
    @Override
    public void call(DownloadThread downloadThread) {
        threadList.remove(downloadThread);
        threadCountActived.set(threadCountActived.get()-1);
        System.out.println("线程"+downloadThread.threadId+":回调");
        if(needToDelete&&threadList.size()==0){
            if(Files.exists(Paths.get(savePath + "/" + name.get()))){
                try {
                    if(file!=null){
                        file.close();
                    }
                    Files.delete(Paths.get(savePath + "/" + name.get()));
                    DownloadLogger.delete(name.get());
                    System.out.println("删除成功:"+name.get());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 写数据
     * @param offset 写偏移量
     * @param buffer 数据
     * @throws IOException 写数据出现异常
     */
    void write(long offset, ByteBuffer buffer, int threadID, long upperBound) throws IOException {
        buffer.flip();
        int length = buffer.limit();
        while (buffer.hasRemaining()) {
            channel.write(buffer, offset);
        }
        wroteSize.addAndGet(length);
        downloadLogger.updateLog(threadID, length, offset + length, upperBound); // 更新日志
    }

    /**
     * @return 已经下载的数据量，为了知道何时结束整个任务，以及统计信息
     */
    long getWroteSize() {
        return wroteSize.get();
    }

    // 继续下载时调用
    void setWroteSize(long wroteSize) {
        this.wroteSize.set(wroteSize);
    }


    /**
     * 开始下载
     */
    public void onStart() {
        boolean reStart = DownloadLogger.exist(name.get())&&Files.exists(Paths.get(savePath + "/" + name.get()));
        if (reStart) {
            //若存在log文件并且存在下载文件，则继续下载
            downloadLogger = new DownloadLogger(name.get());
            System.out.printf("继续上次下载[已下载：%.2fMB]：%s\n", downloadLogger.getWroteSize() / 1014.0 / 1024, name.get());
        } else {
            //重新下载
            System.out.println("开始下载：" + name.get());
            downloadLogger =new DownloadLogger(name.get(),link.get(), threadCountActived.get(),savePath,fileSize);
        }
        if(file==null){
            try {
                this.file=new RandomAccessFile(savePath+"/"+name.get(),"rw");
                this.file.setLength(fileSize);
                this.channel=file.getChannel();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.printf("文件大小：%.2fMB\n", fileSize/ 1024.0 / 1024);
        beginTime = System.currentTimeMillis();
        //设置文件已经写入的大小
        setWroteSize(downloadLogger.getWroteSize());
        // 分配线程下载
        dispatcher(reStart);
        // 循环打印进度
        new Thread(new Runnable() {
            @Override
            public void run() {
                showProgress();
            }
        }).start();
        pause=false;
    }

    /**
     * 暂停全部线程的下载任务
     */
    public void onPause(){
        System.out.println("暂停下载:"+name.get());
        status.set("暂停");
        speed.set("0 KB/s");
        timeSpend.set("0 s");
        pause=true;

        List<DownloadThread>list=new ArrayList<>();
        for (DownloadThread thread:threadList){
            list.add(thread);
        }
        //由于线程中断后会调用回调函数来将自己从threadList中删除，会产生同步修改的错误,需要这样处理
        for(DownloadThread thread:list){
            if(thread.isAlive()){
                thread.pauseThread();
            }
        }
    }


    /**
     * 继续下载，若未创建线程且下载大小<文件大小，则启动下载
     */
    public void onResume(){
        status.set("下载中");
        threadCountActived.setValue(threadCountAll);
        if(threadList.size()>0){
            for (DownloadThread thread :threadList) {
                thread.resumeThread();
            }
        }else if(getWroteSize()<fileSize){
            onStart();
        }else if(!Files.exists(Paths.get(savePath + "/" + name.get()))){
            onStart();
        }
        pause=false;
    }

    /**
     * 删除下载文件及任务
     */
    public void onDelete(){

        //若当前没有下载线程，则直接删除，否则在线程的回调函数里面删除
        if(threadList.size()==0){
            if(Files.exists(Paths.get(savePath + "/" + name.get()))){
                try {
                    if(file!=null){
                        file.close();
                    }

                    Files.delete(Paths.get(savePath + "/" + name.get()));
                    System.out.println("删除成功:"+name.get());
                    DownloadLogger.delete(name.get());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }else{
            if(pause){
                //先唤醒线程，才可以中断它
                for (DownloadThread thread :threadList) {
                    thread.resumeThread();
                }
            }
            for(int i=0;i<threadList.size();i++){
                DownloadThread downloadThread=threadList.get(i);
                threadList.get(i).stop=true;

            }
        }

        //DownloadLogger.delete(name.get());
        needToDelete=true;
    }


    /**
     * 分配器，决定每个线程下载哪个区间的数据
     */
    private void dispatcher(boolean reStart) {
        long blockSize = fileSize / threadCountAll; // 将文件按照线程数量进行分块
        long lowerBound = 0, upperBound = 0;
        long[][] bounds = null;
        int threadID = 0;
        if (reStart) {
            //继续下载时，获取各个下载线程的上下界信息
            bounds = downloadLogger.getThreadBounds();
        }
        for (int i = 0; i < threadCountAll; i++) {
            if (reStart) {
                threadID = (int)(bounds[i][0]);
                lowerBound = bounds[i][1];
                upperBound = bounds[i][2];
            } else {
                //新的下载，重新分配下载边界信息
                // 采用闭区间，假设文件大小为15，三个线程，则：[0,4],[5,9],[10,14]
                threadID = i;
                lowerBound = i * blockSize;
                upperBound = lowerBound + blockSize-1;
            }
            DownloadThread task=new DownloadThread(link.get(), lowerBound, upperBound, this, threadID);
            threadList.add(task);

            //回调函数
            task.setCallBack(this);
            task.start();
        }
    }

    /**
     * 循环打印进度，直到下载完毕，或任务被取消
     */
    private boolean showProgress() {
        //获取已经下载的文件大小
        int interval=2;// 每1秒打印一次
        long downloadedSize = getWroteSize();
        int i = 0;
        int timeS=0;
        long lastSize = 0; // 三秒前的下载量
        status.set("下载中");
        while (downloadedSize < fileSize) {
            if(!pause){
                if (i++ % (interval+1) == interval) {
                    long speed_kb=(downloadedSize-lastSize)/1024/3;
                    speed.set(speed_kb+" KB/s");
                    if(speed_kb>0){
                        timeLeft.set(((fileSize-downloadedSize)/1024)/speed_kb+" s");
                    }

                    //下载进度
                    progress.set(getProgressStr(downloadedSize / (double)fileSize * 100));

                    lastSize = downloadedSize;
                    i = 0;
                }
            }else{
                speed.set("0 KB/s");
                timeLeft.set("-");
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {}
            downloadedSize = getWroteSize();
            if(threadList.size()>0){
                timeS++;
                timeSpend.set(timeS+" s");
            }else{
                timeSpend.set("-");
            }

        }
        try {
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("文件下载出错!");
            try {
                DownloadLogger.delete(name.get());
                Files.delete(Path.of(savePath+"/"+name.get()));
                //删除文件
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }finally {
                return false;
            }
        }
        System.out.println("* 下载成功，本次用时"+ (System.currentTimeMillis() - beginTime) / 1000 +"秒");
        status.set("完成");
        speed.set("-");
        timeLeft.set("-");
        progress.set("100%");
        return true;
    }

    public static String getProgressStr(double progress){
        DecimalFormat df = new DecimalFormat("###.#");
        String s = df.format(progress);
        return s+"%";
    }














    public String getName() {
        return name.get();
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getLink() {
        return link.get();
    }

    public SimpleStringProperty linkProperty() {
        return link;
    }

    public void setLink(String link) {
        this.link.set(link);
    }

    public String getStatus() {
        return status.get();
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public int getThreadCountActived() {
        return threadCountActived.get();
    }

    public SimpleIntegerProperty threadCountActivedProperty() {
        return threadCountActived;
    }

    public void setThreadCountActived(int threadCountActived) {
        this.threadCountActived.set(threadCountActived);
    }

    public String getSpeed() {
        return speed.get();
    }

    public SimpleStringProperty speedProperty() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed.set(speed);
    }

    public String getFileSizeShow() {
        return fileSizeShow.get();
    }

    public SimpleStringProperty fileSizeShowProperty() {
        return fileSizeShow;
    }

    public void setFileSizeShow(String fileSizeShow) {
        this.fileSizeShow.set(fileSizeShow);
    }

    public String getTimeLeft() {
        return timeLeft.get();
    }

    public SimpleStringProperty timeLeftProperty() {
        return timeLeft;
    }

    public void setTimeLeft(String timeLeft) {
        this.timeLeft.set(timeLeft);
    }

    public String getProgress() {
        return progress.get();
    }

    public SimpleStringProperty progressProperty() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress.set(progress);
    }

    public String getTimeSpend() {
        return timeSpend.get();
    }

    public SimpleStringProperty timeSpendProperty() {
        return timeSpend;
    }

    public void setTimeSpend(String timeSpend) {
        this.timeSpend.set(timeSpend);
    }
}

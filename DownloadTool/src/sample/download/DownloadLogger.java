package sample.download;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

class DownloadLogger {
    private String logFileName; // log文件名，例如:a.mp3.log
    private Properties log;  //键值对数据
    private static String log_dir="log/"; //log文件保存目录
    public String fileName; //文件名,例如：a.mp3

    /**
     * 判断log文件是否存在
     * @param fileName 文件名，例如:a.mp3
     * @return
     */
    public static boolean exist(String fileName){
        return Files.exists(Path.of(log_dir+fileName + ".log"));
    }

    /**
     * 删除log文件
     * @param fileName 文件名,例如:a.mp3
     * @return
     */
    public static boolean delete(String fileName){
        try {
            Files.delete(Paths.get(log_dir + fileName + ".log"));
            System.out.println("删除成功:"+fileName+".log");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(log_dir+fileName+".log 删除失败!!!!");
            return false;
        }
    }

    /**
     * 读取log目录下的全部log文件并返回list
     * @return
     */
    public static List<DownloadLogger> getAllLog(){
        List<DownloadLogger> downloadLoggerList =new ArrayList<>();
        File dir=new File(log_dir);
        for(String s:dir.list()){
            if(s.endsWith(".log")){
                s=s.substring(0,s.length()-4);
                downloadLoggerList.add(new DownloadLogger(s));
            }
        }
        return downloadLoggerList;
    }

     /**
      * 当存在Log文件时，利用文件名来构建logger
      * @param fileName
      */
    DownloadLogger(String fileName) {
        this.fileName = fileName;
        this.logFileName=fileName+".log";
        log = new Properties();
        FileInputStream fin = null;
        try {
            log.load(new FileInputStream(log_dir+logFileName));
        } catch (IOException ignore) {
        } finally {
            try {
                fin.close();
            } catch (Exception ignore) {}
        }
    }

    /**
     *创建新的Logger,当创建新的下载任务时调用
     * @param fileName  下载的文件名
     * @param url   下载地址
     * @param threadCount  线程数
     * @param fileSavePath 下载文件的保存目录
     * @return
     */
    DownloadLogger(String fileName, String url, int threadCount, String fileSavePath,Long fileSize) {
        this.fileName = fileName;
        this.logFileName=fileName+".log";
        this.log = new Properties();
        log.put("fileName",fileName); //文件名
        log.put("url", url); //下载链接
        log.put("wroteSize", "0"); //写入大小
        log.put("fileSize", String.valueOf(fileSize)); //文件总大小
        log.put("savePath",fileSavePath);  //文件下载目录
        log.put("threadCount", String.valueOf(threadCount)); //线程数量
        for (int i = 0; i < threadCount; i++) {
            log.put("thread_" + i, "0-0"); //线程数及区间
        }
    }

    /**
     * 获取Log文件总的键值对信息
     * @param key  键名
     * @return
     */
    public String getLogValue(String key){
        return (String) log.getProperty(key);
    }


    /**
     * 更新Log中的线程下载区间信息
     */
    synchronized void updateLog(int threadID, long length, long lowerBound, long upperBound) {
        //线程信息
        log.put("thread_"+threadID, lowerBound + "-" + upperBound);
        //写入大小
        log.put("wroteSize", String.valueOf(length + Long.parseLong(log.getProperty("wroteSize"))));

        FileOutputStream file = null;
        try {
            File dir=new File(log_dir);
            if(!dir.exists()){
                dir.mkdirs();
            }
            file = new FileOutputStream(log_dir+logFileName); // 每次写时都清空文件
            log.store(file, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取Log中的线程的下载区间信息
     * @return
     */
    long[][] getThreadBounds() {
        long[][] bounds = new long[Integer.parseInt(log.get("threadCount").toString())][3];
        int[] index = {0};//lambda表达式的原因，不能直接定义int变量使用
        log.forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith("thread_")) {
                String[] interval = v.toString().split("-");
                bounds[index[0]][0] = Long.parseLong(key.substring(key.indexOf("_") + 1)); //线程序号
                bounds[index[0]][1] = Long.parseLong(interval[0]);  //下界
                bounds[index[0]++][2] = Long.parseLong(interval[1]);//上界
            }
        });
       return bounds;
    }
    //获取写入大小
    long getWroteSize() {
        return Long.parseLong(log.getProperty("wroteSize"));
    }
}

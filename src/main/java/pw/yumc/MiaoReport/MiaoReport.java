package pw.yumc.MiaoReport;

import lombok.Cleanup;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.plugin.java.JavaPlugin;
import pw.yumc.YumCore.bukkit.Log;
import pw.yumc.YumCore.commands.CommandSub;
import pw.yumc.YumCore.commands.annotation.Async;
import pw.yumc.YumCore.commands.annotation.Cmd;
import pw.yumc.YumCore.commands.annotation.Help;
import pw.yumc.YumCore.commands.interfaces.Executor;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author MiaoWoo
 */
public class MiaoReport extends JavaPlugin implements Executor {
    public String doPost(String httpUrl, String user, String content) {
        String address = null;
        String param = String.format("poster=%s&syntax=text&expiration=day&content=%s", user, content);
        try {
            URL url = new URL(httpUrl);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            @Cleanup
            OutputStream os = connection.getOutputStream();
            os.write(param.getBytes());
            if (connection.getResponseCode() == 302) {
                address = connection.getHeaderField("Location");
            }
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return address;
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
        new CommandSub("MiaoReport", this);
    }

    public String b2mb(long bytes) {
        return bytes / 1024 / 1024 + " MB";
    }

    public String long2Time(long date) {
        final long sec = date / 1000;
        if (sec < 60) {
            return sec + " 秒";
        } else if (sec < 3600) {
            return sec / 60 + " 分 " + sec % 60 + " 秒";
        } else if (sec < 86400) {
            return sec / 3600 + " 小时 " + sec % 3600 / 60 + " 分 " + sec % 3600 % 60 + " 秒";
        } else {
            return sec / 86400 + " 天 " + sec % 86400 / 3600 + " 小时 " + sec % 86400 % 3600 / 60 + " 分 " + sec % 86400 % 60 + " 秒";
        }
    }

    @Async
    @Cmd(permission = "MiaoReport.admin")
    @Help("上报服务器日志")
    public boolean main(CommandSender sender) {
        try {
            Log.sender(sender, "§6正在生成日志数据...");
            String content = "当前报告由 MiaoBugReport(" + this.getDescription().getVersion() + ") 生成 作者 MiaoWoo 官网 https://w.yumc.pw\n";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            content += "生成时间: " + sdf.format(new Date()) + "\n";

            content += "============================== 以下为本次报告内容 ==============================\n";
            content += "服务器版本: " + Bukkit.getVersion() + "\n";
            content += "Bukkit版本: " + Bukkit.getBukkitVersion() + "\n";

            content += "============================== 运行数据 ==============================\n";
            final Runtime rt = Runtime.getRuntime();
            content += "运行时间: " + long2Time(System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) + "\n";
            content += "CPU 核心: " + rt.availableProcessors() + "\n";
            content += "最大内存: " + b2mb(rt.maxMemory()) + "\n";
            content += "分配内存: " + b2mb(rt.totalMemory()) + "\n";
            content += "空闲内存: " + b2mb(rt.freeMemory()) + "\n";

            content += "============================== 数据统计 ==============================\n";
            //  info: '§a%s §3- §a%s  §6区块: §a%s §6实体: §a%s §6tiles: §a%s §6玩家: §a%s'
            content += "世界列表: \n" + Bukkit.getWorlds()
                    .stream()
                    .map(w -> {
                        int tileEntities = 0;
                        for (final Chunk chunk : w.getLoadedChunks()) {
                            tileEntities += chunk.getTileEntities().length;
                        }
                        return String.format(" - %s(%s)\n    - 已加载区块: %s\n    - 实体数量: %s\n    - Tile数量: %s\n    - 玩家数量: %s",
                                w.getName(),
                                w.getEnvironment().name(),
                                w.getLoadedChunks().length,
                                w.getEntities().size(),
                                tileEntities,
                                w.getPlayers().size());
                    }).collect(Collectors.joining("\n")) + "\n";
            content += "在线玩家: " + Bukkit.getOnlinePlayers()
                    .stream().map(HumanEntity::getName)
                    .collect(Collectors.joining(", ")) + "\n";
            String plugins = Arrays.stream(Bukkit.getPluginManager()
                    .getPlugins())
                    .map(p -> String.format("%s(%s)", p.getName(), p.getDescription().getVersion()))
                    .collect(Collectors.joining(", ")
                    );
            content += "插件列表: " + plugins + "\n";

            content += "============================== 服务器线程堆栈 ==============================\n";
            content += getAllStackTraces();

            content += "============================== 本次运行日志 ==============================\n";
            File log = new java.io.File("logs/latest.log");
            String logs;
            if (log.exists()) {
                logs = new String(Files.readAllBytes(log.toPath()));
            } else {
                logs = "日志文件不存在!";
            }
            content += logs;

            content += "============================== 报告结束 ==============================";
            Log.sender(sender, "§6正在上报数据至UbuntuPaste §a大小: " + (content.getBytes().length / 1024) + "KB §6请稍候...");
            String address = doPost("https://paste.ubuntu.com/",
                    sender.getName(),
                    URLEncoder.encode(content, "UTF-8"));
            Log.sender(sender, "§a数据上报完成 §6您可以分享地址给他人!");
            Log.sender(sender, "§6地址: §3" + "https://paste.ubuntu.com" + address);
        } catch (IOException e) {
            Log.sender(sender, "§c日志上报失败! 请查看后台报错!");
            e.printStackTrace();
        }
        return true;
    }

    public String getAllStackTraces() {
        StringBuilder trace = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stackTraceElements = entry.getValue();
            trace.append("\"").append(thread.getName()).append("\"");
            if (thread.isDaemon()) {
                trace.append(" daemon");
            }
            trace.append(" prio=").append(thread.getPriority());
            trace.append(" id=").append(thread.getId()).append("\n");
            trace.append("\tjava.lang.Thread.State: ").append(thread.getState().name()).append("\n");
            for (StackTraceElement element : stackTraceElements) {
                trace.append("\t at ").append(element).append("\n");
            }
            trace.append("\n");
        }
        return trace.toString();
    }

    @Override
    public void onDisable() {
    }
}
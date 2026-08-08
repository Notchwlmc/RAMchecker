package com.notchwlmc;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RamCommand implements CommandExecutor {

    private final RAMChecker plugin;

    public RamCommand(RAMChecker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("rc")) return false;

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();

            // autocheck 子命令
            if (sub.equals("autocheck")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /rc autocheck <on|off|status>");
                    return true;
                }
                String opt = args[1].toLowerCase();
                if (opt.equals("on")) {
                    plugin.setAutoCheck(true);
                    sender.sendMessage(ChatColor.GREEN + "✅ 自动内存监控已开启");
                } else if (opt.equals("off")) {
                    plugin.setAutoCheck(false);
                    sender.sendMessage(ChatColor.GREEN + "✅ 自动内存监控已关闭");
                } else if (opt.equals("status")) {
                    boolean enabled = plugin.isAutoCheckEnabled();
                    sender.sendMessage(ChatColor.YELLOW + "当前自动监控状态: " + (enabled ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
                } else {
                    sender.sendMessage(ChatColor.RED + "无效参数，请使用 on / off / status");
                }
                return true;
            }

            // update 子命令
            if (sub.equals("update")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/rc update check   - 检查更新");
                    sender.sendMessage(ChatColor.YELLOW + "/rc update download - 下载最新版本");
                    return true;
                }
                String action = args[1].toLowerCase();
                if (action.equals("check")) {
                    sender.sendMessage(ChatColor.YELLOW + "正在检查更新...");
                    plugin.checkForUpdates(true);
                } else if (action.equals("download")) {
                    if (plugin.getLatestVersion() == null) {
                        sender.sendMessage(ChatColor.RED + "请先使用 /rc update check 检查是否有新版本");
                        return true;
                    }
                    sender.sendMessage(ChatColor.YELLOW + "正在下载新版本...");
                    boolean success = plugin.downloadUpdate();
                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "下载完成！请重启服务器以加载新版本。");
                    } else {
                        sender.sendMessage(ChatColor.RED + "下载失败，请查看控制台日志。");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "无效操作，请使用 check 或 download");
                }
                return true;
            }

            sender.sendMessage(ChatColor.RED + "未知子命令，可用: autocheck, update");
            return true;
        }

        // 默认显示内存和 TPS
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;

        double[] tps = plugin.getServer().getTPS();

        sender.sendMessage(ChatColor.GOLD + "===== 服务器状态 (Paper) =====");
        sender.sendMessage(ChatColor.YELLOW + "最大内存: " + ChatColor.WHITE + formatBytes(maxMemory));
        sender.sendMessage(ChatColor.YELLOW + "已分配内存: " + ChatColor.WHITE + formatBytes(allocatedMemory));
        sender.sendMessage(ChatColor.YELLOW + "已使用内存: " + ChatColor.WHITE + formatBytes(usedMemory));
        sender.sendMessage(ChatColor.YELLOW + "空闲内存: " + ChatColor.WHITE + formatBytes(freeMemory));
        sender.sendMessage(ChatColor.YELLOW + "可用内存: " + ChatColor.WHITE + formatBytes(availableMemory));
        sender.sendMessage(ChatColor.YELLOW + "TPS (1m/5m/15m): "
                + getTpsColor(tps[0]) + String.format("%.2f", tps[0]) + ChatColor.WHITE + "/"
                + getTpsColor(tps[1]) + String.format("%.2f", tps[1]) + ChatColor.WHITE + "/"
                + getTpsColor(tps[2]) + String.format("%.2f", tps[2]));
        sender.sendMessage(ChatColor.GOLD + "==============================");
        return true;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), pre);
    }

    private ChatColor getTpsColor(double tps) {
        if (tps >= 19.5) return ChatColor.GREEN;
        if (tps >= 15.0) return ChatColor.YELLOW;
        return ChatColor.RED;
    }
}
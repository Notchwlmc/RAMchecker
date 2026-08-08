package com.notchwlmc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RAMChecker extends JavaPlugin {

    private int taskId = -1;
    private int updateTaskId = -1;
    private boolean restarting = false;

    // 内存监控配置
    private long thresholdBytes;
    private int checkInterval;
    private int saveDelay;
    private boolean enabled;
    private String restartCommand;

    // 更新配置
    private boolean updateEnabled;
    private String updateUrl;
    private int updateInterval;
    private String downloadFolder;

    private File configFile;
    private FileConfiguration config;

    // 最新版本信息
    private String latestVersion = null;
    private String downloadUrl = null;

    @Override
    public void onEnable() {
        File customFolder = new File(getDataFolder().getParentFile(), "RC");
        if (!customFolder.exists()) {
            customFolder.mkdirs();
        }
        configFile = new File(customFolder, "config.yml");

        if (!configFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("默认配置文件已生成到 " + configFile.getPath());
                } else {
                    getLogger().warning("找不到默认配置文件 config.yml，请手动创建！");
                }
            } catch (Exception e) {
                getLogger().severe("无法生成默认配置文件: " + e.getMessage());
            }
        }

        reloadConfig();

        if (getCommand("rc") != null) {
            getCommand("rc").setExecutor(new RamCommand(this));
            getCommand("rc").setTabCompleter(new RamTabCompleter(this));
        } else {
            getLogger().warning("命令 'rc' 未在 plugin.yml 中定义！");
        }

        // 启动内存监控
        if (enabled) {
            startMemoryMonitor();
        }

        // 启动更新检查
        if (updateEnabled) {
            startUpdateChecker();
        }

        getLogger().info("RAMChecker 已启用！");
    }

    @Override
    public void onDisable() {
        stopMemoryMonitor();
        stopUpdateChecker();
        getLogger().info("RAMChecker 已禁用！");
    }

    @Override
    public void reloadConfig() {
        if (configFile == null) {
            File customFolder = new File(getDataFolder().getParentFile(), "RC");
            if (!customFolder.exists()) {
                customFolder.mkdirs();
            }
            configFile = new File(customFolder, "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("memory-check.enabled", true);
        int thresholdMB = config.getInt("memory-check.threshold-mb", 1700);
        checkInterval = config.getInt("memory-check.check-interval", 30);
        saveDelay = config.getInt("memory-check.save-delay", 5);
        restartCommand = config.getString("memory-check.restart-command", "restart");

        if (thresholdMB <= 0) {
            getLogger().warning("阈值必须大于 0 MB，已强制设为 1700 MB");
            thresholdMB = 1700;
        }
        thresholdBytes = thresholdMB * 1024L * 1024L;
        if (checkInterval < 5) checkInterval = 5;
        if (saveDelay < 1) saveDelay = 1;

        updateEnabled = config.getBoolean("update.enabled", false);
        updateUrl = config.getString("update.url", "https://example.com/update.json");
        updateInterval = config.getInt("update.interval-hours", 24);
        downloadFolder = config.getString("update.download-folder", "update");

        getLogger().info("内存监控阈值: " + thresholdMB + " MB (" + formatBytes(thresholdBytes) + ")");
        if (updateEnabled) {
            getLogger().info("自动更新已启用，检查间隔: " + updateInterval + " 小时");
        }
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        try {
            config.save(configFile);
        } catch (Exception e) {
            getLogger().severe("无法保存配置文件: " + e.getMessage());
        }
    }

    private void startMemoryMonitor() {
        stopMemoryMonitor();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (restarting) return;
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            if (usedMemory >= thresholdBytes) {
                getLogger().warning(String.format("⚠️ 已使用内存 %s 超过阈值 %s，准备自动重启...",
                        formatBytes(usedMemory), formatBytes(thresholdBytes)));
                triggerRestart();
            }
        }, 0L, checkInterval * 20L);
        getLogger().info("自动内存监控已启动");
    }

    private void stopMemoryMonitor() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
            getLogger().info("自动内存监控已停止");
        }
    }

    public void setAutoCheck(boolean enable) {
        if (this.enabled == enable) return;
        this.enabled = enable;
        config.set("memory-check.enabled", enable);
        saveConfig();
        if (enable) {
            startMemoryMonitor();
        } else {
            stopMemoryMonitor();
        }
    }

    public boolean isAutoCheckEnabled() {
        return enabled;
    }

    private void triggerRestart() {
        if (restarting) return;
        restarting = true;
        Bukkit.broadcastMessage("§c⚠️ 服务器内存已超限，将在 " + saveDelay + " 秒后执行重启，请尽快保存进度！");
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
            getLogger().info("已执行 save-all，等待 " + saveDelay + " 秒后发送重启命令...");
        }, 20L);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            getLogger().info("正在执行重启命令: /" + restartCommand);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), restartCommand);
        }, (saveDelay + 1) * 20L);
    }

    private void startUpdateChecker() {
        stopUpdateChecker();
        updateTaskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
            checkForUpdates(false);
        }, 10 * 20L, updateInterval * 60 * 60 * 20L);
        getLogger().info("自动更新检查已启动");
    }

    private void stopUpdateChecker() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
            getLogger().info("自动更新检查已停止");
        }
    }

    public void checkForUpdates(boolean notify) {
        if (updateUrl == null || updateUrl.isEmpty()) {
            getLogger().warning("未配置更新URL，跳过检查");
            return;
        }
        try {
            URL url = new URL(updateUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                getLogger().warning("更新检查请求失败，响应码: " + responseCode);
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String json = sb.toString();
            Pattern versionPattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
            Pattern downloadPattern = Pattern.compile("\"download\"\\s*:\\s*\"([^\"]+)\"");
            Matcher vMatcher = versionPattern.matcher(json);
            Matcher dMatcher = downloadPattern.matcher(json);

            if (vMatcher.find() && dMatcher.find()) {
                String remoteVersion = vMatcher.group(1);
                String remoteDownload = dMatcher.group(1);
                String currentVersion = getDescription().getVersion();

                if (!remoteVersion.equals(currentVersion)) {
                    latestVersion = remoteVersion;
                    downloadUrl = remoteDownload;
                    getLogger().info("发现新版本: " + remoteVersion + " (当前: " + currentVersion + ")");

                    if (notify) {
                        // ===== 增强提示：类似 Geyser 风格 =====
                        String line = ChatColor.GOLD + "════════════════════════════════════════════";
                        String title = ChatColor.YELLOW + "   ⚡ 有一个新的 RAMChecker 更新可用！";
                        String verNew = ChatColor.GREEN + "   最新版本: " + ChatColor.WHITE + remoteVersion;
                        String verCur = ChatColor.GREEN + "   当前版本: " + ChatColor.WHITE + currentVersion;
                        String link = ChatColor.AQUA + "   下载链接: " + ChatColor.UNDERLINE + ChatColor.BLUE + remoteDownload;
                        String footer = ChatColor.GOLD + "════════════════════════════════════════════";
                        String tip = ChatColor.GRAY + "   (使用 /rc update download 自动下载安装)";

                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> p.hasPermission("ramchecker.update") || p.isOp())
                                .forEach(p -> {
                                    p.sendMessage(line);
                                    p.sendMessage(title);
                                    p.sendMessage(verNew);
                                    p.sendMessage(verCur);
                                    p.spigot().sendMessage(
                                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(link)
                                    );
                                    p.sendMessage(footer);
                                    p.sendMessage(tip);
                                });

                        getLogger().warning("========================================");
                        getLogger().warning("  [RAMChecker] 新版本 " + remoteVersion + " 可用！");
                        getLogger().warning("  下载: " + remoteDownload);
                        getLogger().warning("========================================");
                    }
                } else {
                    if (notify) {
                        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ [RAMChecker] 已是最新版本 (" + currentVersion + ")");
                    }
                }
            } else {
                getLogger().warning("解析更新信息失败，响应内容: " + json);
            }
        } catch (Exception e) {
            getLogger().warning("检查更新出错: " + e.getMessage());
        }
    }

    public boolean downloadUpdate() {
        if (latestVersion == null || downloadUrl == null) {
            getLogger().warning("没有可用的更新，请先执行 /rc update check");
            return false;
        }
        try {
            File pluginsFolder = getDataFolder().getParentFile();
            File updateFolder = new File(pluginsFolder, downloadFolder);
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }
            String fileName = "RAMChecker-" + latestVersion + ".jar";
            File targetFile = new File(updateFolder, fileName);

            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                getLogger().warning("下载失败，响应码: " + responseCode);
                return false;
            }

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            getLogger().info("新版本已下载到: " + targetFile.getAbsolutePath());
            Bukkit.broadcastMessage("§a[RAMChecker] 新版本已下载到 " + targetFile.getName());
            Bukkit.broadcastMessage("§6请手动重启服务器以加载新版本。");
            return true;
        } catch (Exception e) {
            getLogger().severe("下载更新失败: " + e.getMessage());
            return false;
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
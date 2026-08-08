package com.notchwlmc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RamTabCompleter implements TabCompleter {

    private final RAMChecker plugin;

    public RamTabCompleter(RAMChecker plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("rc")) return result;

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = Arrays.asList("autocheck", "update");
            for (String opt : options) {
                if (opt.startsWith(partial)) {
                    result.add(opt);
                }
            }
            return result;
        }

        // autocheck 子命令
        if (args.length == 2 && args[0].equalsIgnoreCase("autocheck")) {
            String partial = args[1].toLowerCase();
            boolean currentState = plugin.isAutoCheckEnabled();
            List<String> options = new ArrayList<>();
            if (currentState) {
                options.add("off");
                options.add("status");
            } else {
                options.add("on");
                options.add("status");
            }
            for (String opt : options) {
                if (opt.startsWith(partial)) {
                    result.add(opt);
                }
            }
            return result;
        }

        // update 子命令
        if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
            String partial = args[1].toLowerCase();
            List<String> options = Arrays.asList("check", "download");
            for (String opt : options) {
                if (opt.startsWith(partial)) {
                    result.add(opt);
                }
            }
            return result;
        }

        return result;
    }
}
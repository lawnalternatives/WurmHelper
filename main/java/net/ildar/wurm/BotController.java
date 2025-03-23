package net.ildar.wurm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BotController {
    private final static String jarFilePrefix = "jar:file:";
    private final String jarFilePath;
    private final BotClassLoader botClassLoader;
    //The list of all bot implementations.
    private final List<BotRegistration> botList = new ArrayList<>();
    private boolean gPaused = false;

    private BotController() {
        String classResourcePath = Mod.class.getName().replace('.', '/');
        String jarResourceName = Utils.getResource("/" + classResourcePath + ".class").toString();
        int SkipPrefix = jarResourceName.indexOf(jarFilePrefix) + jarFilePrefix.length();
        int DropSuffix = jarResourceName.lastIndexOf("!/");
        this.jarFilePath = jarResourceName
                .substring(SkipPrefix, DropSuffix)
                .replaceAll("%.{2}", " ");
        this.botClassLoader = new BotClassLoader(Thread.currentThread().getContextClassLoader(), jarFilePath);
        initBotRegistrations();
    }

    public static BotController getInstance() {
        return InstanceHolder.instance;
    }

    private synchronized void initBotRegistrations() {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            jarFile.stream().forEach((jarEntry) -> {
                if (!jarEntry.getName().endsWith(".class"))
                    return;
                String jarEntryClassName = jarEntry.getName()
                        .substring(0, jarEntry.getName().length() - ".class".length())
                        .replaceAll("/", ".");
                try {
                    Class<?> jarEntryClass = Class.forName(jarEntryClassName, true, botClassLoader);
                    if (!jarEntryClassName.endsWith("Bot"))
                        return;
                    BotRegistration newRegistration = (BotRegistration) jarEntryClass.getDeclaredMethod("getRegistration").invoke(null);
                    botList.add(newRegistration);

                } catch (ClassNotFoundException e) {
                    Utils.consolePrint("Couldn't find a class with name " + jarEntryClassName);
                } catch (Exception e) {
                    Utils.consolePrint(e.toString());
                }
            });
        } catch (IOException e) {
            Utils.consolePrint(e.toString());
        }
    }

    public synchronized void handleInput(String[] data) {
        if (data.length < 1) {
            Utils.consolePrint(getBotUsageString());
            botList.stream()
                    .map(br -> br.getAbbreviation() + ": " + br.getDescription())
                    .forEachOrdered(Utils::consolePrint);
            Utils.writeToConsoleInputLine();
            return;
        }
        switch (data[0]) {
            case "reload":
                reloadAllBots();
                Utils.writeToConsoleInputLine();
                return;
            case "off":
                deactivateAllBots();
                Utils.writeToConsoleInputLine();
                return;
            case "pause":
                pauseAllBots();
                Utils.writeToConsoleInputLine("pause");
                return;
        }
        BotProxy proxy = getBotProxy(data[0]);
        if (proxy == null) {
            Utils.consolePrint("Didn't find a bot with abbreviation \"" + data[0] + "\"");
            Utils.consolePrint(getBotUsageString());
            return;
        }

        if (data.length == 1) {
            printBotDescription(proxy);
            Utils.writeToConsoleInputLine(data[0]);
            return;
        }
        if (isActive(proxy)) {
            if (data[1].equals("on")) {
                Utils.consolePrint(proxy.getSimpleName() + " is already on");
            } else {
                try {
                    proxy.handleInput(Arrays.copyOfRange(data, 1, data.length));
                } catch (Exception e) {
                    Utils.consolePrint("Unable to configure  " + proxy.getSimpleName());
                    Utils.consolePrint(e.toString());
                }
            }
        } else {
            if (data[1].equals("on")) {
                proxy.instantiate();
                proxy.start();
                Utils.consolePrint(proxy.getSimpleName() + " is on!");
                printBotDescription(proxy);
            } else {
                Utils.consolePrint(proxy.getSimpleName() + " is not running!");
            }
        }
        Utils.writeToConsoleInputLine(data[0]);
    }

    public Stream<BotProxy> getActiveBots() {
        return botList.stream()
                .map(BotRegistration::getProxy)
                .filter(br -> br.isInstantiated() && !br.isInterrupted());
    }

    public synchronized boolean isActive(BotProxy bot) {
        return getActiveBots().anyMatch(Predicate.isEqual(bot));
    }

    private synchronized void deactivateAllBots() {
        getActiveBots().forEach(BotProxy::deactivate);
    }

    private synchronized void reloadAllBots() {
        deactivateAllBots();
        botList.clear();
        initBotRegistrations();
        Utils.consolePrint("Reloaded: " + botList
                .stream()
                .map(BotRegistration::getProxy)
                .map(BotProxy::getSimpleName)
                .collect(Collectors.joining(", ")));
    }

    public synchronized void onBotInterrupted(Class<? extends Thread> botClass) {
        botList.stream().map(BotRegistration::getProxy).filter(bp -> bp.getBotClass() == botClass).forEach(BotProxy::deinstantiate);
    }

    private synchronized void pauseAllBots() {
        if (getActiveBots().findFirst().isPresent()) {
            gPaused = !gPaused;
            if (gPaused) {
                getActiveBots().forEach(BotProxy::setPaused);
            } else {
                getActiveBots().forEach(BotProxy::setResumed);
            }
            Utils.consolePrint("All bots have been " + (gPaused ? "paused!" : "resumed!"));
        } else {
            Utils.consolePrint("No bots are running!");
        }
    }

    public void printBotDescription(BotProxy proxy) {
        BotRegistration botRegistration = getBotRegistration(proxy);
        String description = "no description";
        if (botRegistration != null)
            description = botRegistration.getDescription();
        Utils.consolePrint("=== " + proxy.getSimpleName() + " ===");
        Utils.consolePrint(description);
        if (isActive(proxy)) {
            proxy.printVerboseUsageString();
        } else {
            String abbreviation = "*";
            if (botRegistration != null)
                abbreviation = botRegistration.getAbbreviation();
            Utils.consolePrint("Type \"" + Mod.ConsoleCommand.bot.name() + " " + abbreviation + " " + "on\" to activate the bot");
        }
    }

    public String getBotUsageString() {
        StringBuilder result = new StringBuilder("Usage: " + Mod.ConsoleCommand.bot.name() + " {");
        for (BotRegistration botRegistration : botList)
            result.append(botRegistration.getAbbreviation()).append("|");
        result.append("pause|off}");
        return result.toString();
    }

    private BotProxy getBotProxy(String abbreviation) {
        for (BotRegistration botRegistration : botList)
            if (botRegistration.getAbbreviation().equals(abbreviation))
                return botRegistration.getProxy();
        return null;
    }

    public BotRegistration getBotRegistration(BotProxy proxy) {
        for (BotRegistration botRegistration : botList) {
            if (botRegistration.getProxy() == proxy)
                return botRegistration;
        }
        return null;
    }

    private static class InstanceHolder {
        private static final BotController instance = new BotController();
    }
}

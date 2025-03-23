package net.ildar.wurm;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class BotController {
    private static BotController instance;
    //The list of all bot implementations.
    private final List<BotRegistration> botList = new ArrayList<>();
    private boolean gPaused = false;
    private final ClassLoader botClassLoader = new BotClassLoader(Thread.currentThread().getContextClassLoader());

    public static BotController getInstance() {
        if (instance == null)
            instance = new BotController();
        return instance;
    }

    private BotController() {
        initBotRegistrations();
    }

    private synchronized void initBotRegistrations() {
        String classResourcePath = Mod.class.getName().replace('.', '/');
        String jarFileName = Utils.getResource("/" + classResourcePath + ".class").toString();
        final String jarFilePrefix = "jar:file:";
        jarFileName = jarFileName
                .substring(jarFileName.indexOf(jarFilePrefix) + jarFilePrefix.length(), jarFileName.lastIndexOf("!/"))
                .replaceAll("%.{2}", " ");
        Utils.consolePrint("loading from jar: " + jarFileName);
        try (JarFile jarFile = new JarFile(jarFileName)) {
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
                    Utils.consolePrint("registering bot " + jarEntryClassName);
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
            Utils.writeToConsoleInputLine(Mod.ConsoleCommand.bot.name() + " ");
            return;
        }
        switch (data[0]) {
            case "reload":
                reloadAllBots();
                return;
            case "off":
                deactivateAllBots();
                return;
            case "pause":
                pauseAllBots();
                Utils.writeToConsoleInputLine(Mod.ConsoleCommand.bot.name() + " pause");
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
            Utils.writeToConsoleInputLine(Mod.ConsoleCommand.bot.name() + " " + data[0] + " ");
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
        Utils.writeToConsoleInputLine(Mod.ConsoleCommand.bot.name() + " " + data[0] + " ");
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
}

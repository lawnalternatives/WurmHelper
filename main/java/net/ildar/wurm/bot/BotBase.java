package net.ildar.wurm.bot;

import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract base class for all "Bots".
 * <p>
 * NB: The names of classes in this package are significant: any class ending with "Bot"
 * is assumed to derive from BotBase, and is loaded into the BotController's list of
 * bots dynamically via introspection. The names of BotBase itself and any helper classes must
 * not end with "Bot".
 */
public abstract class BotBase extends Thread {
    /**
     * A timeout an each bot implementation should use between iterations
     */
    long timeout = 1000;
    /**
     * The bot implementation should register his input handlers with {@link #registerInputHandler(InputKey, InputHandler)}
     */
    private final Map<InputKey, InputHandler> inputHandlers = new HashMap<>();
    /**
     * Store all registered message processors here to unregister them on bot deactivation to prevent memory leaks
     */
    private final List<Chat.MessageProcessor> registeredMessageProcessors = new ArrayList<>();
    private boolean paused = false;

    public BotBase() {
        //register standard input handlers
        registerInputHandler(InputKeyBase.t, this::handleTimeoutChange);
        registerInputHandler(InputKeyBase.off, inputs -> deactivate());
        registerInputHandler(InputKeyBase.info, this::handleInfoCommand);
        registerInputHandler(InputKeyBase.pause, inputs -> togglePause());
    }

    public static BotRegistration getRegistration() {
        return new BotRegistration(BotBase.class, "Bot didn't provide a description", "?");
    }

    //Bot implementations must do their stuff here
    abstract void work() throws Exception;

    @Override
    public final void run() {
        try {
            work();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
            Utils.consolePrint(e.toString());
        }
        unregisterMessageProcessors();
        BotController.getInstance().onBotInterrupted(getClass());
        Utils.consolePrint(this.getClass().getSimpleName() + " was stopped");
    }

    boolean isActive() {
        return !isInterrupted();
    }

    synchronized void waitOnPause() throws InterruptedException {
        if (paused) {
            this.wait();
        }
    }

    private void togglePause() {
        if (paused) {
            this.setResumed();
        } else {
            this.setPaused();
        }
    }

    public void setPaused() {
        paused = true;
        for (int i = 0; i < Utils.getMaxActionNumber(); i++) {
            Mod.hud.sendAction(PlayerAction.STOP, 0);
        }
        Utils.consolePrint(getClass().getSimpleName() + " is paused.");
    }

    public synchronized void setResumed() {
        paused = false;
        this.notify();
        Utils.consolePrint(getClass().getSimpleName() + " is resumed.");
    }

    public void deactivate() {
        Utils.consolePrint("Deactivating " + getClass().getSimpleName());
        BotController.getInstance().onBotInterrupted(getClass());
        interrupt();
    }

    public String getUsageString() {
        return "Usage: " +
                this.getClass().getSimpleName() +
                " {" +
                inputHandlers
                        .keySet()
                        .stream()
                        .map(InputKey::getName)
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.joining("|")) +
                "}";
    }

    public void printVerboseUsageString() {
        Utils.consolePrint(getUsageString());
        inputHandlers
                .keySet()
                .stream()
                .sorted(Comparator.comparing(InputKey::getName))
                .forEachOrdered(ik -> Utils.consolePrint(ik.getName() + " " + ik.getUsage() + ": " + ik.getDescription()));
    }

    void printInputKeyUsageString(InputKey inputKey) {
        Utils.consolePrint("Usage: " + this.getClass().getSimpleName() + " " + inputKey.getName() + " " + inputKey.getUsage());
    }

    /**
     * Handle the console input for current bot instance
     *
     * @param data console input
     */
    public void handleInput(String[] data) {
        if (data == null || data.length == 0)
            return;
        InputHandler inputHandler = getInputHandler(data[0]);
        if (inputHandler == null) {
            Utils.consolePrint("Unknown key - " + data[0]);
            return;
        }
        String[] handlerParameters = null;
        if (data.length > 1)
            handlerParameters = Arrays.copyOfRange(data, 1, data.length);
        inputHandler.handle(handlerParameters);
    }

    private void handleInfoCommand(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.info);
            return;
        }
        InputKey inputKey = getInputKey(input[0]);
        if (inputKey == null) {
            Utils.consolePrint("Unknown key");
            printVerboseUsageString();
            return;
        }
        Utils.consolePrint(inputKey.getDescription());
    }

    private void handleTimeoutChange(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.t);
            return;
        }
        try {
            int timeout = Integer.parseInt(input[0]);
            setTimeout(timeout);
        } catch (Exception e) {
            Utils.consolePrint("Wrong timeout value!");
        }
    }

    final void setTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        this.timeout = timeout;
        Utils.consolePrint("Current timeout is " + timeout + " milliseconds");
    }

    /**
     * The enumeration type of the key must have a usage and description string fields for each item
     */
    final void registerInputHandler(InputKey key, InputHandler inputHandler) {
        InputKey oldKey = getInputKey(key.getName());
        if (oldKey != null)
            inputHandlers.remove(oldKey);
        inputHandlers.put(key, inputHandler);
    }

    private InputKey getInputKey(String key) {
        for (InputKey inputKey : inputHandlers.keySet())
            if (inputKey.getName().equals(key))
                return inputKey;
        return null;
    }

    private InputHandler getInputHandler(String key) {
        InputKey inputKey = getInputKey(key);
        if (inputKey == null) return null;
        return inputHandlers.get(inputKey);
    }

    final void registerEventProcessor(Function<String, Boolean> filter, Runnable callback) {
        registerMessageProcessor(":Event", filter, callback);
    }

    final void registerMessageProcessor(String tabName, Function<String, Boolean> filter, Runnable callback) {
        registeredMessageProcessors.add(Chat.registerMessageProcessor(tabName, filter, callback));
    }

    private void unregisterMessageProcessors() {
        registeredMessageProcessors.forEach(Chat::unregisterMessageProcessor);
    }

    private enum InputKeyBase implements InputKey {
        t("Set the timeout for bot. The bot will wait for specified time(in milliseconds) after each iteration/update",
                "timeout(in milliseconds)"),
        off("Deactivate the bot",
                ""),
        pause("Pause/resume the bot",
                ""),
        info("Get information about configuration key",
                "key");

        private final String description;
        private final String usage;

        InputKeyBase(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }

        @Override
        public String getName() {
            return name();
        }
    }

    protected interface InputHandler {
        void handle(String[] inputData);
    }

    interface InputKey {
        String getName();

        String getDescription();

        String getUsage();
    }

}

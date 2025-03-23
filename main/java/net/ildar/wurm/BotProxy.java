package net.ildar.wurm;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class BotProxy {
    private final Class<? extends Thread> botClass;
    private Thread botInstance = null;

    public BotProxy(Class<? extends Thread> botClass) {
        this.botClass = botClass;
    }

    public Class<? extends Thread> getBotClass() { return botClass; }

    public String getSimpleName() { return botClass.getSimpleName(); }

    public void instantiate() {
        try {
            botInstance = botClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deinstantiate() {
        botInstance = null;
    }

    public boolean isInstantiated() {
        return botInstance != null;
    }

    public void start() {
        botInstance.start();
    }

    private Object call(String methodName, Object... parameters) {
        Class<?>[] parameterTypes = Arrays.stream(parameters).map(Object::getClass).toArray(Class<?>[]::new);
        try {
            Method run = botClass.getMethod(methodName, parameterTypes);
            return run.invoke(botInstance, parameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public BotRegistration getRegistration() {
        return (BotRegistration) call("getRegistration");
    }

    public void setPaused() {
        call("setPaused");
    }

    public void setResumed() {
        call("setResumed");
    }

    public void deactivate() {
        call("deactivate");
    }

    public String getUsageString() {
        return (String) call("getUsageString");
    }

    public void printVerboseUsageString() {
        call("printVerboseUsageString");
    }

    public void handleInput(String[] data) {
        call("handleInput", (Object) data);
    }

    public boolean isInterrupted() {
        return (boolean) call("isInterrupted");
    }
}

package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;

public class BotRegistration {
    private final BotProxy proxy;
    private final String description;
    private final String abbreviation;

    public BotRegistration(Class<? extends Thread> botClass, String description, String abbreviation) {
        this.proxy = new BotProxy(botClass);
        this.description = description;
        this.abbreviation = abbreviation;
    }

    public BotProxy getProxy() { return proxy; }

    public String getDescription() {
        return description;
    }

    public String getAbbreviation() {
        return abbreviation;
    }
}

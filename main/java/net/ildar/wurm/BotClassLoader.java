package net.ildar.wurm;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class BotClassLoader extends ClassLoader {
    private final ClassLoader defaultLoader;
    private final String jarFilePath;

    public BotClassLoader(ClassLoader defaultLoader, String jarFilePath) {
        this.defaultLoader = defaultLoader;
        this.jarFilePath = jarFilePath;
    }

    public static byte[] readStream(InputStream fin) throws IOException {
        byte[][] bufs = new byte[8][];
        int bufsize = 4096;
        for (int i = 0; i < 8; ++i) {
            bufs[i] = new byte[bufsize];
            int size = 0;
            int len = 0;
            do {
                len = fin.read(bufs[i], size, bufsize - size);
                if (len < 0) {
                    byte[] result = new byte[bufsize - 4096 + size];
                    int s = 0;
                    for (int j = 0; j < i; ++j) {
                        System.arraycopy(bufs[j], 0, result, s, s + 4096);
                        s = s + s + 4096;
                    }
                    System.arraycopy(bufs[i], 0, result, s, size);
                    return result;
                }
                size += len;
            } while (size < bufsize);
            bufsize *= 2;
        }
        throw new IOException("too much data");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith("net.ildar.wurm.bot"))
            return defaultLoader.loadClass(name);
        try {
            byte[] b = loadClassData(name);
            return defineClass(name, b, 0, b.length);
        } catch (Exception e) {
            Utils.consolePrint("BotClassLoader: " + e);
            return defaultLoader.loadClass(name);
        }
    }

    private byte[] loadClassData(String name) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            JarEntry entry = jarFile.stream().filter(jarEntry -> {
                        int extPos = jarEntry.getName().lastIndexOf(".class");
                        if (extPos == -1)
                            return false;
                        String jarEntryClassName = jarEntry
                                .getName()
                                .substring(0, extPos)
                                .replaceAll("/", ".");
                        return jarEntryClassName.equals(name);
                    })
                    .findFirst()
                    .orElseThrow(() -> new IOException("BotClassLoader: could not find class " + name));
            return readStream(jarFile.getInputStream(entry));
        }
    }
}

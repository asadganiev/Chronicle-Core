/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.core.watcher;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.io.IOTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileSystemWatcherTest {
    static String base = OS.TARGET + "/FileSystemWatcherTest";

    @Before
    public void setup() {
        tearDown();

        new File(base).mkdirs();
    }

    @After
    public void tearDown() {
        IOTools.deleteDirWithFiles(base);
    }

    @Test
    public void bootstrapAndUpdate() throws IOException {
        SortedMap<String, String> events = new ConcurrentSkipListMap<>();

        WatcherListener listener = new WatcherListener() {
            @Override
            public void onExists(String base, String filename, Boolean modified) throws IllegalStateException {
                events.put(filename, "modified: " + modified);
            }

            @Override
            public void onRemoved(String base, String filename) throws IllegalStateException {
                events.put(filename, "removed: true");
            }
        };
        assertTrue(new File(base + "/dir1").mkdir());
        assertTrue(new File(base + "/dir2").mkdir());
        assertTrue(new File(base + "/dir1/file11").createNewFile());
        assertTrue(new File(base + "/dir1/file12").createNewFile());
        assertTrue(new File(base + "/dir2/file20").createNewFile());

        FileSystemWatcher watcher = new FileSystemWatcher();
        watcher.addPath(base);
        watcher.start();
        watcher.addListener(listener);
        retryAssertEquals("dir1=modified: null\n" +
                "dir1/file11=modified: null\n" +
                "dir1/file12=modified: null\n" +
                "dir2=modified: null\n" +
                "dir2/file20=modified: null", events);
        try (FileWriter fw = new FileWriter(base + "/dir1/file11")) {
        }
        assertTrue(new File(base + "/dir2/file20").delete());
        assertTrue(new File(base + "/dir2/file21").createNewFile());
        assertTrue(new File(base + "/dir3/dir30").mkdirs());
        assertTrue(new File(base + "/dir3/dir30/file301").createNewFile());

        retryAssertEquals(
                "dir1=modified: null\n" +
                "dir1/file11=modified: true\n" +
                "dir1/file12=modified: null\n" +
                "dir2=modified: null\n" +
                "dir2/file20=removed: true\n" +
                "dir2/file21=modified: false\n" +
                "dir3=modified: false\n" +
                "dir3/dir30=modified: null\n" +
                "dir3/dir30/file301=modified: null", events);

        IOTools.deleteDirWithFiles(base + "/dir2", 2);

        retryAssertEquals(
                "dir1=modified: null\n" +
                "dir1/file11=modified: true\n" +
                "dir1/file12=modified: null\n" +
                "dir2=removed: true\n" +
                "dir2/file20=removed: true\n" +
                "dir2/file21=removed: true\n" +
                "dir3=modified: false\n" +
                "dir3/dir30=modified: null\n" +
                "dir3/dir30/file301=modified: null", events);

        watcher.stop();
    }

    private void retryAssertEquals(String expected, SortedMap<String, String> events) {
        for (int i = Jvm.isDebug() ? 500 : 50; ; i--) {
            try {
                Jvm.pause(20);
                assertEquals(expected,
                        events.entrySet().stream()
                                .map(Map.Entry::toString)
                                .collect(Collectors.joining("\n")));
                break;
            } catch (AssertionError ae) {
                if (i <= 0)
                    throw ae;
            }
        }
    }
}
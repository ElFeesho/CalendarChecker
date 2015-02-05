package uk.sawcz.calendarchecker;

import java.io.File;

/**
 * Created by sawczc01 on 05/02/2015.
 */
public class FileWatcher
{
    interface Listener
    {
        void fileCreated(File file);
    }

    private final Listener listener;
    private final String watchPath;

    public FileWatcher(String watchPath, Listener listener)
    {
        this.watchPath = watchPath;
        this.listener = listener;
    }

    public void poll()
    {
        System.out.println("Polling...");

        for (File file : new File(watchPath).listFiles())
        {
            listener.fileCreated(file);
        }

    }
}

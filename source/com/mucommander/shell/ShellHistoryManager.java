package com.mucommander.shell;

import com.mucommander.*;
import com.mucommander.conf.*;
import com.mucommander.io.BackupInputStream;
import com.mucommander.io.BackupOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Used to manage shell history.
 * <p>
 * Using this class is fairly basic: you can add elements to the shell history through
 * {@link #add(String)} and browse it through {@link #getHistoryIterator()}.
 * </p>
 * @author Nicolas Rinaudo
 */
public class ShellHistoryManager {
    // - History configuration -----------------------------------------------
    // -----------------------------------------------------------------------
    /** File in which to store the shell history. */
    private static final String DEFAULT_HISTORY_FILE_NAME = "shell_history.xml";



    // - Class fields ---------------------------------------------------------------
    // ------------------------------------------------------------------------------
    /** List of shell history registered listeners. */
    private static WeakHashMap listeners;
    /** Stores the shell history. */
    private static String[]    history;
    /** Index of the first element of the history. */
    private static int         historyStart;
    /** Index of the last element of the history. */
    private static int         historyEnd;
    /** Path to the history file. */
    private static File        historyFile;



    // - Initialisation -------------------------------------------------------------
    // ------------------------------------------------------------------------------
    /**
     * Prevents instantiations of the class.
     */
    private ShellHistoryManager() {}

    /**
     * Initialises history.
     */
    static {
        history   = new String[ConfigurationManager.getVariableInt(ConfigurationVariables.SHELL_HISTORY_SIZE, ConfigurationVariables.DEFAULT_SHELL_HISTORY_SIZE)];
        listeners = new WeakHashMap();
    }



    // - Listener code --------------------------------------------------------------
    // ------------------------------------------------------------------------------
    /**
     * Registers a listener to changes in the shell history.
     * @param listener listener to register.
     */
    public static void addListener(ShellHistoryListener listener) {listeners.put(listener, null);}

    /**
     * Propagates shell history events to all registered listeners.
     * @param command command that was added to the shell history.
     */
    private static void triggerEvent(String command) {
        Iterator iterator;

        iterator = listeners.keySet().iterator();
        while(iterator.hasNext())
            ((ShellHistoryListener)iterator.next()).historyChanged(command);
    }



    // - History access -------------------------------------------------------------
    // ------------------------------------------------------------------------------
    /**
     * Completely empties the shell history.
     */
    public static void clear() {
        Iterator iterator; // Iterator on the history listeners.

        // Empties history.
        historyStart = 0;
        historyEnd   = 0;

        // Notifies listeners.
        iterator = listeners.keySet().iterator();
        while(iterator.hasNext())
            ((ShellHistoryListener)iterator.next()).historyCleared();
    }

    /**
     * Returns a <b>non thread-safe</code> iterator on the history.
     * @return an iterator on the history.
     */
    public static Iterator getHistoryIterator() {return new HistoryIterator();}

    /**
     * Adds the specified command to shell history.
     * @param command command to add to the shell history.
     */
    public static void add(String command) {
        // Ignores empty commands.
        if(command.trim().equals(""))
            return;
        // Ignores the command if it's the same as the last one.
        // There is no last command if history is empty.
        if(historyEnd != historyStart) {
            int lastIndex;

            // Computes the index of the previous command.
            if(historyEnd == 0)
                lastIndex = history.length - 1;
            else
                lastIndex = historyEnd - 1;

            if(command.equals(history[lastIndex]))
                return;
        }

        if(Debug.ON) Debug.trace("Adding  " + command + " to shell history.");

        // Updates the history buffer.
        history[historyEnd] = command;
        historyEnd++;

        // Wraps around the history buffer.
        if(historyEnd == history.length)
            historyEnd = 0;

        // Clears items from the begining of the buffer if necessary.
        if(historyEnd == historyStart) {
            if(++historyStart == history.length)
                historyStart = 0;
        }

        // Propagates the event.
        triggerEvent(command);
    }



    // - History saving / loading ---------------------------------------------------
    // ------------------------------------------------------------------------------
    /**
     * Sets the path of the shell history file.
     * @param     path                  where to load the shell history from.
     * @exception FileNotFoundException if <code>path</code> is not accessible.
     */
    public static void setHistoryFile(String path) throws FileNotFoundException {
        File tempFile;

        tempFile = new File(path);
        if(!(tempFile.exists() && tempFile.isFile() && tempFile.canRead()))
            throw new FileNotFoundException("Not a valid file: " + path);

        historyFile = tempFile;
    }

    /**
     * Returns the path to the shell history file.
     * <p>
     * This method cannot guarantee the file's existence, and it's up to the caller
     * to deal with the fact that the user might not actually have created a history file yet.
     * </p>
     * <p>
     * This method's return value can be modified through {@link #setHistoryFile(String)}.
     * If this wasn't called, the default path will be used: {@link #DEFAULT_HISTORY_FILE_NAME}
     * in the {@link com.mucommander.PlatformManager#getPreferencesFolder() preferences} folder.
     * </p>
     * @return the path to the shell history file.
     */
    public static File getHistoryFile() {
        if(historyFile == null)
            return new File(PlatformManager.getPreferencesFolder(), DEFAULT_HISTORY_FILE_NAME);
        return historyFile;
    }

    /**
     * Writes the shell history to hard drive.
     */
    public static boolean writeHistory() {
        BackupOutputStream out;

        out = null;
        try {
            ShellHistoryWriter.write(out = new BackupOutputStream(getHistoryFile()));
            out.close();
        }
        catch(Exception e) {
            if(out != null) {
                try {out.close(false);}
                catch(Exception e2) {}
            }
            return false;
        }
        return true;
    }

    /**
     * Loads the shell history.
     */
    public static void loadHistory() throws Exception {
        BackupInputStream in;

        in = null;
        try {ShellHistoryReader.read(in = new BackupInputStream(getHistoryFile()));}
        finally {
            if(in != null) {
                try {in.close();}
                catch(Exception e2) {}
            }
        }
    }

    /**
     * Iterator used to browse history.
     * @author Nicolas Rinaudo
     */
    static class HistoryIterator implements Iterator {
        /** Index in the history. */
        private int index;

        /**
         * Creates a new history iterator.
         */
        public HistoryIterator() {index = ShellHistoryManager.historyStart;}

        /**
         * Returns <code>true</code> if there are more elements to iterate through.
         * @return <code>true</code> if there are more elements to iterate through, <code>false</code> otherwise.
         */
        public boolean hasNext() {return index != ShellHistoryManager.historyEnd;}

        /**
         * Returns the next element in the history.
         * @return the next element in the history.
         */
        public Object next() throws NoSuchElementException {
            String value;

            if(!hasNext())
                throw new NoSuchElementException();

            value = ShellHistoryManager.history[index];
            if(++index == ShellHistoryManager.history.length)
                index = 0;
            return value;
        }

        /**
         * Operation not supported.
         */
        public void remove() throws UnsupportedOperationException {throw new UnsupportedOperationException();}
    }

}

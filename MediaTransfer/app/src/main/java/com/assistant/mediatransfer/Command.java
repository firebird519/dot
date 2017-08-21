package com.assistant.mediatransfer;

/**
 * Created by alex on 17-8-21.
 */

public class Command {
    public static final String COMMAND_CLIENT_INFO = "client";
    public static final String COMMAND_TEXT = "text";
    public static final String COMMAND_FILE = "file";

    String command;
    String data;
}

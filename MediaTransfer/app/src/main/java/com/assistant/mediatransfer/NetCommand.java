package com.assistant.mediatransfer;

/**
 * Created by alex on 17-8-21.
 */

public class NetCommand {
    // with ClientInfo Json
    public static final String COMMAND_CLIENT_INFO = "clientInfo";

    // with ChatMessage
    public static final String COMMAND_TEXT = "text";

    // to be defined
    public static final String COMMAND_FILE = "file";

    // with CommandVerifyMessage
    public static final String COMMAND_VERIFY = "verify";

    public String command;
    public String jsonData;
    public long unId;

    public NetCommand(String cmd, String data) {
        command = cmd;
        jsonData = data;
        unId = System.currentTimeMillis();
    }

    public void setCommand(String cmd) {
        command = cmd;
        unId = System.currentTimeMillis();
    }

    public void setData(String data) {
        jsonData = data;
    }

}

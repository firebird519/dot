package com.assistant.mediatransfer;

/**
 * Created by alex on 17-8-21.
 */

public class FileTransferCommand extends Command {
    String fileName;
    int fileAttributes;
    long fileCreateTime;
    long fileLastAccessTime;
    long fileLastWriteTime;
    long fileSize;
    int fileReserved1;
}
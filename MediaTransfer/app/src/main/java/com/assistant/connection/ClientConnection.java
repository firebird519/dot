package com.assistant.connection;

import com.assistant.utils.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by alex on 17-8-5.
 */

public class ClientConnection extends Connection {

    ClientConnection() {
        this(null);
    }

    ClientConnection(Socket socket) {
        super(socket, false);
    }

    // TODO: considering put it to other places.
    public int sendFile(File file, final long timeout) {
        if (file == null) {
            return -1;
        }

        try {
            FileHeader header = createFileHeader(file);

            if (header == null) {
                //mListener.onFileTransferFailed(mTransferingFilePathName, 0, 0);
                return -1;
            }

            int progress = 0;
            long fileSize = header.fileSize;

            FileInputStream reader = new FileInputStream(file);
        }catch (Exception e) {

        }

        return 0;
    }

    private class FileHeader {
        byte[] byteFileName; // 256 byte
        int fileAttributes;
        long fileCreateTime;
        long fileLastAccessTime;
        long fileLastWriteTime;
        long fileSize;
        int fileReserved1; // 0

        FileHeader() {

        }
    }

    private FileHeader createFileHeader(File file) {
        FileHeader header = new FileHeader();
        header.byteFileName = new byte[256];

        String fileName = file.getName();

        // format file name to UTF-16LE
        byte[] byteFileName = null;
        try {
            byteFileName = fileName.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        System.arraycopy(byteFileName, 0, header.byteFileName, 0,
                byteFileName.length);

        Log.d(this, "=========Log==================");
        Log.d(this, "fileName:" + fileName + ", UTF-16LE bytes length:"
                + byteFileName.length);
        StringBuffer lb = new StringBuffer();
        for (int i = 0; i < 256; i++) {
            lb.append(Integer.toHexString(header.byteFileName[i])).append(" ");
            if (i % 32 == 0 && i != 0) {
                Log.d(this, lb.toString());
                lb = new StringBuffer();
            }
        }
        Log.d(this, lb.toString());
        Log.d(this, "===========================");
        header.fileAttributes = 0;
        header.fileCreateTime = 0;
        header.fileLastAccessTime = 0;

        // java last modified createTime is ms. but windows file createTime is 100ns and start with 1601-1-1 0:0:0. We convert to windows filetime here first!
        // TODO: Should move to windows client side.
        header.fileLastWriteTime = file.lastModified() * 10000;
        header.fileSize = file.length();

        header.fileReserved1 = 0;

        Log.d(this,
                "--->sendFile fileLastWriteTime:"
                        + Long.toHexString(header.fileLastWriteTime)
                        + ", fileSize:" + header.fileSize);
        Log.d(this, "--->sendFile fileLastWriteTime:" + header.fileLastWriteTime
                + ", fileSize:" + header.fileSize);
        Log.d(this,
                "--->sendFile fileLastWriteTime:"
                        + getNormalTime(file.lastModified()));

        return header;
    }

    // TODO: rename?
    private static String getNormalTime(long value) {
        DateFormat format = SimpleDateFormat.getDateTimeInstance(); //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new Date(value));
    }

}

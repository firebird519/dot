package pjsipapp.com.firebird.pjsip;

import org.pjsip.pjsua2.LogEntry;
import org.pjsip.pjsua2.LogWriter;

/**
 * Created by liyong on 17-7-20.
 */

public class PjSipLogWriter extends LogWriter {
    @Override
    public void write(LogEntry entry) {
        System.out.println(entry.getMsg());
    }
}

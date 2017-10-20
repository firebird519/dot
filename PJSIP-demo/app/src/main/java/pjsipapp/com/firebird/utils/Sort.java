package pjsipapp.com.firebird.utils;

import android.util.Log;

/**
 * Created by liyong on 17-8-8.
 */

public class Sort {

    public static void quickSort(int[] data, int offset, int len) {
        if (len == 1) return;

        int basePos = -1;
        int base = -1;

        String log;

        int temp;
        for (int i = offset; i < len; i ++) {
            log = "";

            for(int m = offset; m < len; m ++) {
                log += data[m] + " ";
            }

            Log.d("Sort", "before:" + log);

            if (basePos == -1) {
                if (i + 1 == len) {
                    continue;
                }
                if (data[i] > data[i + 1]) {
                    temp = data[i];
                    data[i] = data[i + 1];
                    data[i + 1]  = temp;

                    basePos = i + 1;
                    base = data[i + 1];

                    i = basePos;
                    continue;
                }
            } else {
                if (data[i] <= base) {
                    if ( i == basePos + 1) {
                        temp = data[i];
                        data[i] = data[basePos];
                        data[basePos]  = temp;

                        basePos = i;
                        continue;
                    } else {
                        temp = data[i];
                        data[i] = data[basePos + 1];
                        data[basePos + 1]  = data[basePos];
                        data[basePos] = temp;

                        basePos += 1;
                    }
                }
            }
        }

        log = "";

        for(int i = offset; i < len; i ++) {
            log += data[i] + " ";
        }

        Log.d("Sort", "after:" + log);


        Log.d("Sort", "offset:" + offset + ", len:" + len);
        Log.d("Sort", "basePos:" + basePos + ", base:" + base);


        if (basePos > 1) {
            quickSort(data, 0, basePos);
        }

        if (basePos >= 0 && (len - (basePos + 1) > 1)) {
            quickSort(data, basePos + 1, len);
        }
    }
}

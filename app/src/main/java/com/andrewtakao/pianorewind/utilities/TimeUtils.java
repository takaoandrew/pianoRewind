package com.andrewtakao.pianorewind.utilities;

/**
 * Created by andrewtakao on 12/14/17.
 */

public class TimeUtils {
    public static String convertToMinutesAndSeconds(int milliseconds) {
        int seconds = milliseconds/1000;
        int minutes = seconds/60;
        int leftOverSeconds = seconds - minutes*60;
        String colon = ":";
        if (leftOverSeconds<10) {
            colon = ":0";
        }
        String returnString = (minutes+colon+leftOverSeconds);
        return returnString;

    }
}

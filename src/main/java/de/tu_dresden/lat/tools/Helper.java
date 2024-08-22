package de.tu_dresden.lat.tools;

import de.tu_dresden.lat.data.names.ReasonerName;

/**
 * @author Christian Alrabbaa
 *
 */

public class Helper {

    public static String getMDsID(String[] args) {
        String id = "0";

        if (args == null)
            return id;

        for (String str : args)
            if (!ReasonerName.isName(str))
                return str;

        return id;
    }

    public static ReasonerName getReasonerName(String[] args) {
        ReasonerName name = ReasonerName.Elk;

        if (args == null)
            return name;

        for (String str : args)
            if (ReasonerName.isName(str))
                return ReasonerName.getReasonerName(str);

        return name;
    }
}

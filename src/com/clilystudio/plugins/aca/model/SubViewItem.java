package com.clilystudio.plugins.aca.model;

import com.clilystudio.plugins.aca.utils.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubViewItem {
    private static final Pattern sIdPattern = Pattern.compile("@\\+?(android:)?id/([^$]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern sValidityPattern = Pattern.compile("^([a-zA-Z_$][\\w$]*)$", Pattern.CASE_INSENSITIVE);
    private String mId;
    private boolean mIsAndroidNS = false;
    private String mClassFull;
    private String mClassName;
    private boolean mSelected = true;
    private boolean mClickEvent = true;
    private String mPrefix;
    private boolean mIsTrimType;

    public SubViewItem(String className, String id) {
        if (Utils.isAddPrefix()) {
            mPrefix = Utils.getPrefix();
        } else {
            mPrefix = null;
        }
        mIsTrimType = Utils.isTrimType();

        final Matcher matcher = sIdPattern.matcher(id);
        if (matcher.find() && matcher.groupCount() > 0) {
            this.mId = matcher.group(2);
            String androidNS = matcher.group(1);
            this.mIsAndroidNS = !(androidNS == null || androidNS.length() == 0);
        }

        // mClassName
        String[] packages = className.split("\\.");
        if (packages.length > 1) {
            this.mClassFull = className;
            this.mClassName = packages[packages.length - 1];
        } else {
            this.mClassFull = null;
            this.mClassName = className;
        }
    }

    public String getId() {
        return mId;
    }

    public String getFullID() {
        StringBuilder fullID = new StringBuilder();
        String rPrefix;

        if (mIsAndroidNS) {
            rPrefix = "android.R.mId.";
        } else {
            rPrefix = "R.mId.";
        }

        fullID.append(rPrefix);
        fullID.append(mId);

        return fullID.toString();
    }

    public String getFieldName() {
        return getFieldName(mPrefix, mIsTrimType);
    }

    public String getFieldName(String prefix, boolean isTrimType) {
        mPrefix = prefix;
        mIsTrimType = isTrimType;

        String[] words = this.mId.split("_");
        StringBuilder sb = new StringBuilder();

        if (!Utils.isEmptyString(prefix)) {
            sb.append(prefix);
        }
        int start = 0;
        if (isTrimType && words.length > 1) {
            start = 1;
        }
        for (int i = start; i < words.length; i++) {
            String[] idTokens = words[i].split("\\.");
            char[] chars = idTokens[idTokens.length - 1].toCharArray();
            if (i > start || !Utils.isEmptyString(prefix)) {
                chars[0] = Character.toUpperCase(chars[0]);
            }

            sb.append(chars);
        }

        return sb.toString();
    }

    public boolean isAndroidNS() {
        return mIsAndroidNS;
    }

    public String getClassFull() {
        return mClassFull;
    }

    public String getClassName() {
        return mClassName;
    }

    public boolean isSelected() {
        return mSelected;
    }

    public void setSelected(boolean selected) {
        this.mSelected = selected;
    }

    public boolean hasClickEvent() {
        return mClickEvent;
    }

    public void setClickEvent(boolean clickEvent) {
        this.mClickEvent = clickEvent;
    }

    public boolean checkValidity() {
        String fieldName = getFieldName();
        Matcher matcher = sValidityPattern.matcher(fieldName);
        return matcher.find();
    }
}

package com.hpp3.smartrentwidget;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class SmartRentLock implements Parcelable {
    private int deviceId;
    private String name;

    public SmartRentLock(int deviceId, String name) {
        this.deviceId = deviceId;
        this.name = name;
    }

    protected SmartRentLock(Parcel in) {
        deviceId = in.readInt();
        name = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(deviceId);
        dest.writeString(name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SmartRentLock> CREATOR = new Creator<SmartRentLock>() {
        @Override
        public SmartRentLock createFromParcel(Parcel in) {
            return new SmartRentLock(in);
        }

        @Override
        public SmartRentLock[] newArray(int size) {
            return new SmartRentLock[size];
        }
    };

    // Getters and setters

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public String getNameShort() {
        if (name.endsWith(" - Lock")) {
            return name.substring(0, name.length() - " - Lock".length());
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return this.getName();
    }
}
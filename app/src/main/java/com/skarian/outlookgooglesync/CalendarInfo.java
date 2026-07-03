package com.skarian.outlookgooglesync;

final class CalendarInfo {
    final long id;
    final String name;
    final String accountName;
    final String accountType;
    final boolean visible;
    final boolean syncEvents;
    final int accessLevel;

    CalendarInfo(long id, String name, String accountName, String accountType, boolean visible, boolean syncEvents, int accessLevel) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.accountName = accountName == null ? "" : accountName;
        this.accountType = accountType == null ? "" : accountType;
        this.visible = visible;
        this.syncEvents = syncEvents;
        this.accessLevel = accessLevel;
    }

    @Override
    public String toString() {
        return name + " <" + accountName + "> #" + id;
    }
}

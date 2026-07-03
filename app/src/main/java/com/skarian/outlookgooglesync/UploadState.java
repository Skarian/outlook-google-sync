package com.skarian.outlookgooglesync;

final class UploadState {
    final int active;
    final int cleanActive;
    final int dirtyActive;
    final int dirtyDeleted;

    UploadState(int active, int cleanActive, int dirtyActive, int dirtyDeleted) {
        this.active = active;
        this.cleanActive = cleanActive;
        this.dirtyActive = dirtyActive;
        this.dirtyDeleted = dirtyDeleted;
    }

    boolean isComplete() {
        return dirtyActive == 0 && dirtyDeleted == 0;
    }
}

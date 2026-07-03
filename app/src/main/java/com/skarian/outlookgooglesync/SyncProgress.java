package com.skarian.outlookgooglesync;

interface SyncProgress {
    void update(String message, int percent);
}

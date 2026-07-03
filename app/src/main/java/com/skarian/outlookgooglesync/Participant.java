package com.skarian.outlookgooglesync;

final class Participant {
    final String name;
    final String email;
    final int type;
    final int status;

    Participant(String name, String email, int type, int status) {
        this.name = name == null ? "" : name;
        this.email = email == null ? "" : email;
        this.type = type;
        this.status = status;
    }

    String sortKey() {
        return (name + "\n" + email).toLowerCase();
    }
}

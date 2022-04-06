package com.moyn.talk.models.json.status

enum class StatusType(val string: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    DND("dnd"),
    AWAY("away"),
    INVISIBLE("invisible");
}

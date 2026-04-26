package com.example.hehehe

enum class Instruction(val speech: String) {
    MOVE_LEFT("Left"),
    MOVE_RIGHT("Right"),
    MOVE_UP("Up"),
    MOVE_DOWN("Down"),
    MOVE_CLOSER("Closer"),
    PULL_BACK("Back"),
    ALIGNED("Hold"),
    NOT_FOUND("QR not found")
}
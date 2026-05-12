package com.name.flashlight.utils

object TorchController {

    enum class Owner {
        NONE,
        FLASHLIGHT,
        BLINK
    }

    private var currentOwner = Owner.NONE

    fun acquire(owner: Owner): Boolean {

        return if (
            currentOwner == Owner.NONE ||
            currentOwner == owner
        ) {

            currentOwner = owner
            true

        } else {

            false
        }
    }

    fun release(owner: Owner) {

        if (currentOwner == owner) {
            currentOwner = Owner.NONE
        }
    }

    fun isOwner(owner: Owner): Boolean {
        return currentOwner == owner
    }

//    fun currentOwner(): Owner {
//        return currentOwner
//    }
}
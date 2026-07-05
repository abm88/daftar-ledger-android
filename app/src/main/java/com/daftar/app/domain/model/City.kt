package com.daftar.app.domain.model

/** Cities on the hawala corridor network. */
enum class City(val code: String, val displayName: String) {
    KBL("KBL", "Kabul"),
    HRT("HRT", "Herat"),
    MZR("MZR", "Mazar"),
    JAL("JAL", "Jalalabad");

    companion object {
        fun fromCode(code: String): City = entries.first { it.code == code }
    }
}

package com.daftar.app.domain.model

/** The saraf's own shop identity, shown on headers and statements. */
data class ShopProfile(
    val ownerName: String = "Haji Rahmat",
    val shopName: String = "Sarai Shahzada",
    val city: City = City.KBL,
    val phone: String = "+93 70 000 0001",
    val registration: String = "Reg #AFG-0421",
    val tagline: String = "Currency & hawala",
)

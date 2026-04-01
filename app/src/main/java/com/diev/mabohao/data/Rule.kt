package com.diev.mabohao.data

import com.google.gson.annotations.SerializedName

data class Rule(
    @SerializedName("id")
    val id: String,
    @SerializedName("code")
    val code: String,
    @SerializedName("packageName")
    val packageName: String,
    @SerializedName("appLabel")
    val appLabel: String,
    @SerializedName("isEnabled")
    val isEnabled: Boolean = true,
    @SerializedName("prefix")
    val prefix: String = "*#*#",
    @SerializedName("suffix")
    val suffix: String = "#*#*"
) {
    fun getFullCode(): String = "$prefix$code$suffix"
}